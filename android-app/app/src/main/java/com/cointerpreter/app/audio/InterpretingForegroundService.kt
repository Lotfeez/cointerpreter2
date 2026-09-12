package com.cointerpreter.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cointerpreter.app.R

/**
 * Minimal foreground service required by Android 14+'s microphone
 * foreground-service-type rules (spec §20) to keep an active interpreting
 * session's mic + socket alive if the app is backgrounded mid-session. It
 * only ever runs while a session is active and is stopped the instant the
 * user presses Stop or the session ends, per spec §28 (no hidden sessions
 * left running).
 *
 * The visible notification intentionally avoids exposing any transcript
 * content (spec §17: no conversation content leaves the device via logs or
 * unrelated channels).
 */
class InterpretingForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.state_interpreting))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Active interpreting session", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "cointerpreter_session"
        private const val NOTIFICATION_ID = 42
    }
}
