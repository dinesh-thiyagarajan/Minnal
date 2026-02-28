package com.app.minnal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.app.minnal.MainActivity
import com.app.minnal.R
import com.app.minnal.core.common.Constants
import com.app.minnal.core.common.toHumanReadableSpeed
import com.app.minnal.core.data.engine.TorrentEngineWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Foreground service that keeps the torrent engine running in the background.
 *
 * Displays a persistent notification showing the current download status
 * including active download count and total download speed. The notification
 * is updated every 2 seconds.
 *
 * Includes notification actions for pausing and resuming all torrents.
 */
class TorrentService : Service() {

    private val engineWrapper: TorrentEngineWrapper by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var updateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_ALL -> {
                serviceScope.launch {
                    engineWrapper.pauseAll()
                }
            }
            ACTION_RESUME_ALL -> {
                serviceScope.launch {
                    engineWrapper.resumeAll()
                }
            }
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundService()
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = buildNotification(
            activeCount = 0,
            totalSpeed = 0L
        )

        ServiceCompat.startForeground(
            this,
            Constants.NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )

        startNotificationUpdates()
    }

    private fun startNotificationUpdates() {
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            while (isActive) {
                val activeCount = engineWrapper.getActiveDownloadCount()
                val totalSpeed = engineWrapper.getTotalDownloadSpeed()

                val notification = buildNotification(
                    activeCount = activeCount,
                    totalSpeed = totalSpeed
                )

                val notificationManager = getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager
                notificationManager.notify(Constants.NOTIFICATION_ID, notification)

                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun buildNotification(activeCount: Int, totalSpeed: Long): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseAllIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TorrentService::class.java).apply {
                action = ACTION_PAUSE_ALL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resumeAllIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, TorrentService::class.java).apply {
                action = ACTION_RESUME_ALL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentTitle = if (activeCount > 0) {
            "$activeCount active download${if (activeCount != 1) "s" else ""}"
        } else {
            "Minnal - No active downloads"
        }

        val contentText = if (totalSpeed > 0) {
            "Speed: ${totalSpeed.toHumanReadableSpeed()}"
        } else {
            "Idle"
        }

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Pause All",
                pauseAllIntent
            )
            .addAction(
                android.R.drawable.ic_media_play,
                "Resume All",
                resumeAllIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                "Download Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the current torrent download progress"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        updateJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val UPDATE_INTERVAL_MS = 2000L
        private const val ACTION_PAUSE_ALL = "com.app.minnal.action.PAUSE_ALL"
        private const val ACTION_RESUME_ALL = "com.app.minnal.action.RESUME_ALL"
        private const val ACTION_STOP = "com.app.minnal.action.STOP"

        /**
         * Creates an intent to start the torrent service.
         *
         * @param context the context to create the intent from
         * @return the intent to start the service
         */
        fun startIntent(context: Context): Intent {
            return Intent(context, TorrentService::class.java)
        }

        /**
         * Creates an intent to stop the torrent service.
         *
         * @param context the context to create the intent from
         * @return the intent to stop the service
         */
        fun stopIntent(context: Context): Intent {
            return Intent(context, TorrentService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
}
