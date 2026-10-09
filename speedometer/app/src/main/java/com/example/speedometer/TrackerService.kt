package com.example.speedometer

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import java.util.Locale

/**
 * Foreground service that keeps GPS running while the app is in the background and
 * publishes the live speed as an ongoing notification. On Android 16+ the notification
 * is requested as a Live Update, which shows the speed as a status-bar chip / island.
 */
class TrackerService : Service() {

    private lateinit var locationManager: LocationManager
    private lateinit var notificationManager: NotificationManager
    private var tracking = false
    private var lastNotify = 0L

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = Tracker.onLocation(location)
        override fun onProviderEnabled(provider: String) = Tracker.setGpsOn(true)
        override fun onProviderDisabled(provider: String) {
            Tracker.setGpsOn(false)
            Tracker.clearSatellites()
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) = Tracker.onSatellites(status)
    }

    private val stateListener: (TrackerState) -> Unit = { state ->
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotify >= 1000) {
            lastNotify = now
            notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
        }
    }

    override fun onCreate() {
        super.onCreate()
        Tracker.init(this)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Live speed", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows your current speed while tracking"
                setShowBadge(false)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = buildNotification(Tracker.state)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startTracking()
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        if (tracking) return
        tracking = true
        Tracker.setGpsOn(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener)
        locationManager.registerGnssStatusCallback(gnssCallback, null)
        Tracker.setRunning(true)
        Tracker.addListener(stateListener)
    }

    override fun onDestroy() {
        if (tracking) {
            Tracker.removeListener(stateListener)
            locationManager.removeUpdates(locationListener)
            locationManager.unregisterGnssStatusCallback(gnssCallback)
            tracking = false
            Tracker.setRunning(false)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(state: TrackerState): Notification {
        val speed = Tracker.toUnit(state.speedMs).toInt()
        val unit = Tracker.unitLabel
        val text = when {
            !state.gpsOn -> "GPS is off"
            state.used == 0 -> "Searching for satellites…"
            else -> String.format(
                Locale.US, "%d of %d satellites · max %d",
                state.used, state.visible, Tracker.toUnit(state.maxMs).toInt()
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TrackerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val b = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_speed)
            .setContentTitle("$speed $unit")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_stat_speed), "Stop", stop).build())

        if (Build.VERSION.SDK_INT >= 36) {
            b.addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
            b.setShortCriticalText(speed.toString())
        }
        return b.build()
    }

    companion object {
        private const val CHANNEL_ID = "live_speed"
        private const val NOTIFICATION_ID = 1
        // Notification.EXTRA_REQUEST_PROMOTED_ONGOING (Android 16 Live Updates opt-in)
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
        private const val ACTION_STOP = "com.example.speedometer.STOP"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, TrackerService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TrackerService::class.java))
        }
    }
}
