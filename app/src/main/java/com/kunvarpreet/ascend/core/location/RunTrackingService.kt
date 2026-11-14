package com.kunvarpreet.ascend.core.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kunvarpreet.ascend.MainActivity
import com.kunvarpreet.ascend.R
import com.kunvarpreet.ascend.data.local.AscendDatabaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Foreground service managing active GPS outdoor running sessions.
 * Guarantees foreground lifecycle execution, updates ongoing notifications every 5s,
 * filters GPS jitter (<20m accuracy, <12m/s speed), and persists 100m SQLite checkpoints.
 */
class RunTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null
    private var locationClient: LocationProviderClient? = null
    private lateinit var dbHelper: AscendDatabaseHelper
    private lateinit var notificationManager: NotificationManager

    private var lastCheckpointMeters = 0f
    private var secondsSinceLastNotification = 0

    override fun onCreate() {
        super.onCreate()
        dbHelper = AscendDatabaseHelper.getInstance(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        locationClient = PlatformLocationProviderClient(applicationContext)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val targetId = intent.getStringExtra(EXTRA_TARGET_ID) ?: ""
                val targetDistance = intent.getFloatExtra(EXTRA_TARGET_DISTANCE, 3000f)
                startTracking(targetId, targetDistance)
            }
            ACTION_PAUSE -> {
                pauseTracking()
            }
            ACTION_RESUME -> {
                resumeTracking()
            }
            ACTION_STOP_AND_SAVE -> {
                stopAndSaveTracking()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTracking(targetId: String, targetDistance: Float) {
        val current = _sessionState.value
        if (current.state == RunState.RUNNING) return

        val newSessionId = UUID.randomUUID().toString()
        lastCheckpointMeters = 0f
        secondsSinceLastNotification = 0

        _sessionState.value = ActiveRunSessionState(
            state = RunState.RUNNING,
            targetId = targetId,
            sessionId = newSessionId,
            elapsedSeconds = 0,
            distanceMeters = 0f,
            currentPaceSecPerKm = 0f,
            caloriesBurned = 0,
            waypoints = emptyList(),
            targetDistanceMeters = targetDistance
        )

        startForegroundServiceNotification()
        startTimer()
        startLocationUpdates()
    }

    private fun pauseTracking() {
        if (_sessionState.value.state != RunState.RUNNING) return
        _sessionState.update { it.copy(state = RunState.PAUSED) }
        timerJob?.cancel()
        timerJob = null
        locationClient?.stopLocationUpdates()
        updateNotification()
    }

    private fun resumeTracking() {
        if (_sessionState.value.state != RunState.PAUSED) return
        _sessionState.update { it.copy(state = RunState.RUNNING) }
        startTimer()
        startLocationUpdates()
        updateNotification()
    }

    private fun stopAndSaveTracking() {
        val finalState = _sessionState.value
        timerJob?.cancel()
        timerJob = null
        locationClient?.stopLocationUpdates()

        serviceScope.launch(Dispatchers.IO) {
            if (finalState.distanceMeters > 0f) {
                val geoJson = formatGeoJson(finalState.waypoints)
                dbHelper.finalizeRunGps(
                    sessionId = finalState.sessionId,
                    targetId = finalState.targetId,
                    distanceMeters = finalState.distanceMeters,
                    durationSeconds = finalState.elapsedSeconds,
                    avgPaceSecPerKm = finalState.currentPaceSecPerKm,
                    caloriesBurned = finalState.caloriesBurned,
                    gpsPathGeoJson = geoJson
                )
            }
            withContext(Dispatchers.Main) {
                stopForegroundNotification()
                _sessionState.value = ActiveRunSessionState(state = RunState.IDLE)
                stopSelf()
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive && _sessionState.value.state == RunState.RUNNING) {
                delay(1000L)
                val updatedSeconds = _sessionState.value.elapsedSeconds + 1
                val dist = _sessionState.value.distanceMeters
                val pace = if (dist > 50f && updatedSeconds > 5) {
                    updatedSeconds / (dist / 1000f)
                } else {
                    0f
                }

                _sessionState.update {
                    it.copy(
                        elapsedSeconds = updatedSeconds,
                        currentPaceSecPerKm = pace
                    )
                }

                secondsSinceLastNotification++
                if (secondsSinceLastNotification >= 5) {
                    secondsSinceLastNotification = 0
                    updateNotification()
                }
            }
        }
    }

    private fun startLocationUpdates() {
        locationClient?.startLocationUpdates { location ->
            processNewLocation(location)
        }
    }

    private fun processNewLocation(location: Location) {
        if (_sessionState.value.state != RunState.RUNNING) return

        // 1. Accuracy filter: discard noisy fixes with accuracy > 20m
        if (location.hasAccuracy() && location.accuracy > 20.0f) {
            return
        }

        // 2. Speed filter: discard vehicular speeds > 12.0 m/s (~43.2 km/h)
        if (location.hasSpeed() && location.speed > 12.0f) {
            return
        }

        val currentSession = _sessionState.value
        val lastWaypoint = currentSession.waypoints.lastOrNull()

        if (lastWaypoint != null) {
            val results = FloatArray(1)
            Location.distanceBetween(
                lastWaypoint.latitude,
                lastWaypoint.longitude,
                location.latitude,
                location.longitude,
                results
            )
            val deltaMeters = results[0]

            // 3. Movement threshold: discard stationary jitter < 1.0m
            if (deltaMeters < 1.0f) {
                return
            }

            // 4. Implied speed check between consecutive points
            val deltaSeconds = (location.time - lastWaypoint.timestampMillis) / 1000.0
            if (deltaSeconds > 0) {
                val impliedSpeed = deltaMeters / deltaSeconds
                if (impliedSpeed > 12.0) {
                    return
                }
            }

            val newDistance = currentSession.distanceMeters + deltaMeters
            val newCalories = (newDistance * 0.065f).toInt()
            val newWaypoints = currentSession.waypoints + LatLng(
                latitude = location.latitude,
                longitude = location.longitude,
                timestampMillis = location.time
            )
            val currentPace = if (newDistance > 50f && currentSession.elapsedSeconds > 5) {
                currentSession.elapsedSeconds / (newDistance / 1000f)
            } else {
                currentSession.currentPaceSecPerKm
            }

            _sessionState.update {
                it.copy(
                    distanceMeters = newDistance,
                    caloriesBurned = newCalories,
                    currentPaceSecPerKm = currentPace,
                    waypoints = newWaypoints
                )
            }

            // 5. 100m SQLite Checkpoint
            if (newDistance - lastCheckpointMeters >= 100f) {
                lastCheckpointMeters = (newDistance / 100f).toInt() * 100f
                val sessionSnapshot = _sessionState.value
                val pointsSnapshot = newWaypoints.toList()
                serviceScope.launch(Dispatchers.IO) {
                    dbHelper.checkpointRunGps(
                        sessionId = sessionSnapshot.sessionId,
                        targetId = sessionSnapshot.targetId,
                        distanceMeters = newDistance,
                        durationSeconds = sessionSnapshot.elapsedSeconds,
                        avgPaceSecPerKm = currentPace,
                        caloriesBurned = newCalories,
                        gpsPathGeoJson = formatGeoJson(pointsSnapshot)
                    )
                }
            }
        } else {
            // First valid GPS fix
            val firstPoint = LatLng(
                latitude = location.latitude,
                longitude = location.longitude,
                timestampMillis = location.time
            )
            _sessionState.update {
                it.copy(waypoints = listOf(firstPoint))
            }
        }
    }

    private fun startForegroundServiceNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun updateNotification() {
        if (_sessionState.value.state != RunState.IDLE) {
            val notification = buildNotification()
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val state = _sessionState.value
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actionTitle = if (state.state == RunState.RUNNING) "Pause" else "Resume"
        val actionIntent = Intent(this, RunTrackingService::class.java).apply {
            action = if (state.state == RunState.RUNNING) ACTION_PAUSE else ACTION_RESUME
        }
        val actionPendingIntent = PendingIntent.getService(
            this,
            if (state.state == RunState.RUNNING) 1 else 2,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (state.state == RunState.RUNNING) {
            "Ascend Hunter Run: Active"
        } else {
            "Ascend Hunter Run: Paused"
        }

        val contentText = "${state.distanceKmFormatted}  |  ${state.paceFormatted}  |  ${state.durationFormatted}"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_play, actionTitle, actionPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ascend Hunter Run Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing GPS tracking and telemetry for distance running quests"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun formatGeoJson(points: List<LatLng>): String {
        if (points.isEmpty()) return "[]"
        return points.joinToString(separator = ",", prefix = "[", postfix = "]") { pt ->
            "{\"lat\":${pt.latitude},\"lng\":${pt.longitude}}"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationClient?.stopLocationUpdates()
        timerJob?.cancel()
        serviceScope.cancel()
    }

    companion object {
        const val CHANNEL_ID = "ascend_run_tracking_channel"
        const val NOTIFICATION_ID = 4040

        const val ACTION_START = "com.kunvarpreet.ascend.action.START_RUN"
        const val ACTION_PAUSE = "com.kunvarpreet.ascend.action.PAUSE_RUN"
        const val ACTION_RESUME = "com.kunvarpreet.ascend.action.RESUME_RUN"
        const val ACTION_STOP_AND_SAVE = "com.kunvarpreet.ascend.action.STOP_AND_SAVE_RUN"

        const val EXTRA_TARGET_ID = "extra_target_id"
        const val EXTRA_TARGET_DISTANCE = "extra_target_distance"

        private val _sessionState = MutableStateFlow(ActiveRunSessionState())
        val sessionState: StateFlow<ActiveRunSessionState> = _sessionState.asStateFlow()

        fun startService(context: Context, targetId: String, targetDistanceMeters: Float = 3000f) {
            val intent = Intent(context, RunTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TARGET_ID, targetId)
                putExtra(EXTRA_TARGET_DISTANCE, targetDistanceMeters)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseService(context: Context) {
            val intent = Intent(context, RunTrackingService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resumeService(context: Context) {
            val intent = Intent(context, RunTrackingService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun stopAndSaveService(context: Context) {
            val intent = Intent(context, RunTrackingService::class.java).apply {
                action = ACTION_STOP_AND_SAVE
            }
            context.startService(intent)
        }
    }
}
