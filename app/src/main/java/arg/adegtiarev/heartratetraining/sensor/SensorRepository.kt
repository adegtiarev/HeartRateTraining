package arg.adegtiarev.heartratetraining.sensor

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

// The UI and Bluetooth callbacks are serialized on the main dispatcher.
class SensorRepository(
    private val transport: SensorTransport,
    private val store: RememberedStrapStore,
    private val scope: CoroutineScope,
    private val elapsedMs: () -> Long,
) {
    companion object {
        const val SCAN_MS = 12_000L
        // The H9Z reported roughly once per second in the physical acceptance check.
        const val FRESH_MS = 5_000L
    }

    private val mutableState = MutableStateFlow(SensorState())
    val state = mutableState.asStateFlow()
    private var current: SensorState
        get() = mutableState.value
        set(value) {
            if (mutableState.value.sample != null && value.sample == null) recorder?.signalLost(elapsedMs())
            mutableState.value = value
        }

    private var operation: Job? = null
    private var expiry: Job? = null
    private var loading: Job? = null
    private var loaded = false
    private var visitOpen = false
    private var releaseAfterWorkout = false
    private var recorder: SensorRecorder? = null
    private var visible = false
    private var autoConnectPending = false
    private var deliberatelyDisconnected = false
    private val storageLock = Mutex()
    private val connectionLock = Mutex()

    fun startVisit() {
        releaseAfterWorkout = false
        if (visitOpen) return
        visitOpen = true
        deliberatelyDisconnected = false
        current = SensorState()
        autoConnectPending = true
        loading = scope.launch {
            try {
                val remembered = storageLock.withLock { store.read() }
                current = current.copy(remembered = remembered)
            } catch (_: IOException) {
                current = current.copy(storageError = true)
            }
            loaded = true
            refresh()
        }
    }

    fun stopVisit() {
        if (recorder != null) {
            visible = false
            releaseAfterWorkout = true
            return
        }
        visitOpen = false
        visible = false
        loaded = false
        loading?.cancel()
        disconnect()
    }

    fun recordWith(listener: SensorRecorder) {
        check(recorder == null)
        recorder = listener
        if (!visitOpen) {
            startVisit()
            releaseAfterWorkout = true
        }
        if (!deliberatelyDisconnected && operation?.isActive != true && current.access == SensorAccess.Ready) {
            current.remembered?.let { startOperation { connectAndRecover(it) } }
        }
    }

    fun releaseRecorder() {
        recorder = null
        if (releaseAfterWorkout) stopVisit()
    }

    fun setVisible(isVisible: Boolean) {
        visible = isVisible
        if (visible) {
            refresh()
        } else if (recorder == null && (current.phase == SensorPhase.Scanning || current.phase == SensorPhase.Searching)) {
            autoConnectPending = current.phase == SensorPhase.Searching
            resetOperation()
        }
    }

    fun refresh() {
        if (!loaded) return
        val access = transport.access()
        if (access != current.access) {
            if (access != SensorAccess.Ready) {
                if (current.active != null || current.phase == SensorPhase.Searching) autoConnectPending = true
                resetOperation()
            }
            current = current.copy(access = access)
        }
        expireStaleSample()
        if ((visible || recorder != null) && loaded && access == SensorAccess.Ready && autoConnectPending) {
            autoConnectPending = false
            current.remembered?.let { startOperation { if (recorder != null) connectAndRecover(it) else findAndConnect(it) } }
        }
    }

    fun findStraps() = startOperation {
        current = current.copy(phase = SensorPhase.Scanning)
        val results = linkedMapOf<String, NearbyStrap>()
        withTimeoutOrNull(SCAN_MS) {
            transport.scan().collect { found ->
                val previous = results[found.strap.id]
                val candidate = NearbyStrap(
                    found.strap.copy(name = found.strap.name ?: previous?.strap?.name),
                    found.advertisesHeartRate || previous?.advertisesHeartRate == true,
                )
                if (candidate != previous) {
                    results[candidate.strap.id] = candidate
                    current = current.copy(nearby = results.values.sortedByDescending { it.advertisesHeartRate })
                }
            }
        }
        current = current.copy(phase = SensorPhase.ScanFinished)
    }

    fun select(strap: Strap) = startOperation { connectAndRecover(strap) }

    fun retry() {
        if (current.phase == SensorPhase.ScanFailed && current.active == null) {
            findStraps()
            return
        }
        val strap = current.active ?: current.remembered
        if (strap == null) findStraps() else startOperation { findAndConnect(strap) }
    }

    fun disconnect() {
        deliberatelyDisconnected = true
        autoConnectPending = false
        resetOperation()
    }

    private fun resetOperation() {
        operation?.cancel()
        expiry?.cancel()
        current = current.copy(phase = SensorPhase.Idle, sample = null, nearby = emptyList(), active = null)
    }

    private fun startOperation(block: suspend () -> Unit) {
        deliberatelyDisconnected = false
        autoConnectPending = false
        resetOperation()
        val access = transport.access()
        current = current.copy(access = access)
        if ((!visible && recorder == null) || !loaded || access != SensorAccess.Ready) return
        operation = scope.launch {
            try {
                // Finish closing the old scanner or GATT client before opening its replacement.
                connectionLock.withLock { block() }
            } catch (failure: SensorFailure) {
                currentCoroutineContext().ensureActive()
                current = current.copy(phase = failure.phase, sample = null)
                if (transport.access() != SensorAccess.Ready) refresh()
            } catch (_: SecurityException) {
                currentCoroutineContext().ensureActive()
                autoConnectPending = current.active != null
                current = current.copy(access = SensorAccess.PermissionNeeded, phase = SensorPhase.Idle,
                    active = null, sample = null, nearby = emptyList())
            }
        }
    }

    private suspend fun locate(strap: Strap): Strap {
        current = current.copy(phase = SensorPhase.Searching, sample = null, active = strap)
        val found = withTimeoutOrNull(SCAN_MS) {
            transport.scan().first { it.strap.id == strap.id }
        } ?: throw SensorFailure(SensorPhase.Absent)
        return found.strap.copy(name = found.strap.name ?: strap.name)
    }

    private suspend fun findAndConnect(strap: Strap) = connectAndRecover(locate(strap))

    private suspend fun connectAndRecover(initial: Strap) {
        var target = initial
        var canRecover = true
        var retries = 0
        while (true) {
            current = current.copy(phase = SensorPhase.Connecting, active = target, sample = null)
            var subscribed = false
            try {
                transport.connect(target).collect { event ->
                    when (event) {
                        SensorEvent.Subscribed -> {
                            subscribed = true
                            current = current.copy(phase = SensorPhase.Waiting)
                            remember(target)
                        }
                        is SensorEvent.Measurement -> if (subscribed) {
                            receive(event)
                            if (event.value is HeartRateMeasurement.Pulse) {
                                canRecover = true
                                retries = 0
                            }
                        }
                    }
                }
                throw SensorFailure(SensorPhase.ConnectionFailed)
            } catch (failure: SensorFailure) {
                currentCoroutineContext().ensureActive()
                expiry?.cancel()
                current = current.copy(sample = null)
                if (failure.phase != SensorPhase.ConnectionFailed) throw failure
                if (recorder != null && transport.access() == SensorAccess.Ready) {
                    if (retries >= 4) throw failure
                    current = current.copy(phase = SensorPhase.Searching)
                    delay(1_000L shl retries++)
                    continue
                }
                if (!subscribed || !canRecover) throw failure
                if (!visible || transport.access() != SensorAccess.Ready) {
                    autoConnectPending = true
                    throw failure
                }
                canRecover = false
                target = locate(target)
            }
        }
    }

    private fun remember(strap: Strap) {
        if (strap == current.remembered && !current.storageError) return
        current = current.copy(remembered = strap)
        // Finish an acknowledged selection even if the rider immediately disconnects.
        scope.launch {
            try {
                storageLock.withLock { store.write(strap) }
                current = current.copy(storageError = false)
            } catch (_: IOException) {
                current = current.copy(storageError = true)
            }
        }
    }

    private fun receive(event: SensorEvent.Measurement) {
        expiry?.cancel()
        when (val value = event.value) {
            HeartRateMeasurement.ContactLost -> current = current.copy(phase = SensorPhase.ContactLost, sample = null)
            is HeartRateMeasurement.Pulse -> {
                val sample = PulseSample(value.bpm, event.receivedAtMs)
                recorder?.pulse(sample)
                current = current.copy(phase = SensorPhase.Live, sample = sample)
                val remaining = FRESH_MS - (elapsedMs() - sample.receivedAtMs)
                if (remaining <= 0) expireStaleSample() else expiry = scope.launch {
                    delay(remaining)
                    expireStaleSample()
                }
            }
        }
    }

    internal fun expireStaleSample() {
        val sample = current.sample ?: return
        if (elapsedMs() - sample.receivedAtMs >= FRESH_MS) {
            current = current.copy(phase = SensorPhase.Stale, sample = null)
        }
    }
}
