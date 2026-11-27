package arg.adegtiarev.heartratetraining.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import arg.adegtiarev.heartratetraining.sensor.*
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.buffer
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.callback.FailCallback
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.observer.ConnectionObserver

private val HEART_RATE_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
private val HEART_RATE_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
private val BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
private val BATTERY_LEVEL = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
private const val BATTERY_READ_INTERVAL_MS = 60_000L

fun sensorPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

@SuppressLint("MissingPermission") // Checked at the operation boundary and on return from Settings.
class AndroidSensorTransport(private val context: Context) : SensorTransport {
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    override fun access(): SensorAccess {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) || adapter == null) {
            return SensorAccess.Unavailable
        }
        if (sensorPermissions().any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) {
            return SensorAccess.PermissionNeeded
        }
        return try {
            when {
                adapter?.isEnabled != true -> SensorAccess.BluetoothOff
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
                    context.getSystemService(LocationManager::class.java)?.isLocationEnabled != true -> SensorAccess.LocationOff
                else -> SensorAccess.Ready
            }
        } catch (_: SecurityException) {
            SensorAccess.PermissionNeeded
        }
    }

    override fun scan(): Flow<NearbyStrap> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner ?: throw SensorFailure(SensorPhase.ScanFailed)
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                try {
                    val name = result.scanRecord?.deviceName ?: result.device.name
                    val strap = Strap(result.device.address, name)
                    val heartRate = result.scanRecord?.serviceUuids.orEmpty().any { it.uuid == HEART_RATE_SERVICE }
                    trySend(NearbyStrap(strap, heartRate))
                } catch (error: SecurityException) {
                    close(error)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                close(SensorFailure(SensorPhase.ScanFailed))
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(null, settings, callback)
        } catch (_: IllegalStateException) {
            throw SensorFailure(SensorPhase.ScanFailed)
        }
        try {
            awaitClose()
        } finally {
            try {
                scanner.stopScan(callback)
            } catch (_: SecurityException) {
                // Permission was revoked mid-scan.
            } catch (_: IllegalStateException) {
                // The radio has already stopped the scan.
            }
        }
    }

    override fun connect(strap: Strap): Flow<SensorEvent> = callbackFlow {
        val device = adapter?.getRemoteDevice(strap.id) ?: throw SensorFailure(SensorPhase.ConnectionFailed)
        val manager = HeartRateManager(context, { trySend(it) }, { close(SensorFailure(it)) })
        val request = manager.connect(device).useAutoConnect(false).timeout(15_000)
        try {
            request.fail { _, reason ->
                val phase = if (reason == FailCallback.REASON_DEVICE_NOT_SUPPORTED) SensorPhase.Unsupported
                    else SensorPhase.ConnectionFailed
                close(SensorFailure(phase))
            }.enqueue()
            awaitClose()
        } finally {
            request.cancel()
            manager.close()
        }
    }.buffer(Channel.UNLIMITED)
}

private class HeartRateManager(
    context: Context,
    private val emit: (SensorEvent) -> Unit,
    private val fail: (SensorPhase) -> Unit,
) : BleManager(context) {
    private var measurement: BluetoothGattCharacteristic? = null
    private var batteryLevel: BluetoothGattCharacteristic? = null
    private val handler = Handler(Looper.getMainLooper())
    private val pollBattery = object : Runnable {
        override fun run() {
            val level = batteryLevel ?: return
            readCharacteristic(level).with { _, data -> reportBattery(data) }.enqueue()
            handler.postDelayed(this, BATTERY_READ_INTERVAL_MS)
        }
    }

    init {
        setConnectionObserver(object : ConnectionObserver {
            override fun onDeviceConnecting(device: BluetoothDevice) = Unit
            override fun onDeviceConnected(device: BluetoothDevice) = Unit
            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) = Unit
            override fun onDeviceReady(device: BluetoothDevice) = Unit
            override fun onDeviceDisconnecting(device: BluetoothDevice) = Unit
            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                fail(if (reason == ConnectionObserver.REASON_NOT_SUPPORTED) SensorPhase.Unsupported
                    else SensorPhase.ConnectionFailed)
            }
        })
    }

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        measurement = gatt.getService(HEART_RATE_SERVICE)?.getCharacteristic(HEART_RATE_MEASUREMENT)
        val properties = measurement?.properties ?: return false
        return properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
    }

    override fun isOptionalServiceSupported(gatt: BluetoothGatt): Boolean {
        batteryLevel = gatt.getService(BATTERY_SERVICE)?.getCharacteristic(BATTERY_LEVEL)
        return batteryLevel != null
    }

    override fun initialize() {
        setNotificationCallback(measurement).with { _, data ->
            val receivedAt = SystemClock.elapsedRealtime()
            val value = data.value?.let(::decodeHeartRate)
            if (value != null) emit(SensorEvent.Measurement(value, receivedAt))
        }
        enableNotifications(measurement)
            .done {
                emit(SensorEvent.Subscribed)
                batteryLevel?.let(::observeBattery)
            }
            .fail { _, _ -> fail(SensorPhase.SubscriptionFailed) }
            .invalid { fail(SensorPhase.SubscriptionFailed) }
            .enqueue()
    }

    private fun observeBattery(level: BluetoothGattCharacteristic) {
        if (level.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) pollBattery.run()
        val subscription = when {
            level.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 -> enableNotifications(level)
            level.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 -> enableIndications(level)
            else -> return
        }
        // Nordic dispatches both notifications and indications through this callback.
        setNotificationCallback(level).with { _, data ->
            if (reportBattery(data)) handler.removeCallbacks(pollBattery)
        }
        subscription.enqueue()
    }

    private fun reportBattery(data: Data): Boolean {
        val percent = data.value?.let(::decodeBatteryPercent) ?: return false
        emit(SensorEvent.BatteryLevel(percent))
        return true
    }

    override fun onServicesInvalidated() {
        measurement = null
        batteryLevel = null
        handler.removeCallbacks(pollBattery)
    }

    override fun close() {
        batteryLevel = null
        handler.removeCallbacks(pollBattery)
        super.close()
    }
}
