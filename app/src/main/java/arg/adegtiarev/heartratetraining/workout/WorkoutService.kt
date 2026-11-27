package arg.adegtiarev.heartratetraining.workout

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import arg.adegtiarev.heartratetraining.HeartRateApplication
import arg.adegtiarev.heartratetraining.MainActivity
import arg.adegtiarev.heartratetraining.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class WorkoutService : Service() {
    private val app get() = application as HeartRateApplication
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val radio = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = app.sensors.refresh()
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.workout_channel), NotificationManager.IMPORTANCE_LOW))
        ContextCompat.registerReceiver(this, radio, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ServiceCompat.startForeground(this, NOTIFICATION, notification(false),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            if (!app.recorder.startInService(::end)) end()
            else if (watch == null) watch = scope.launch {
                app.recorder.state.map { it.ride?.phase }.distinctUntilChanged().collect { phase ->
                    if ((phase == WorkoutPhase.Paused || phase == WorkoutPhase.Recording) &&
                        (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED)) {
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(phase == WorkoutPhase.Paused))
                    }
                }
            }
        } catch (_: RuntimeException) {
            app.recorder.startFailed()
            end()
        }
        return START_NOT_STICKY
    }

    private var watch: Job? = null

    private fun notification(paused: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java).setAction(OPEN_WORKOUT)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_workout_heart)
            .setContentTitle(getString(if (paused) R.string.workout_paused else R.string.workout_recording))
            .setContentText(getString(R.string.workout_notification_open)).setOnlyAlertOnce(true)
            .setOngoing(true).setContentIntent(open).build()
    }

    private fun end() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        unregisterReceiver(radio)
        app.recorder.serviceDestroyed()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val OPEN_WORKOUT = "arg.adegtiarev.heartratetraining.OPEN_WORKOUT"
        private const val CHANNEL = "workout_recording"
        private const val NOTIFICATION = 71
    }
}
