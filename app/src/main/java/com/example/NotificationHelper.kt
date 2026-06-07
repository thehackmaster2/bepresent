package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "bepresent_focus_channel"
        const val PERSISTENT_NOTIFICATION_ID = 2026
        const val COMPLETE_NOTIFICATION_ID = 2027
        
        const val ACTION_PAUSE = "com.example.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.ACTION_RESUME"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Focus Session Notifications"
            val desc = "Shows active countdown timers and completion notices for BePresent focus sessions."
            val importance = NotificationManager.IMPORTANCE_LOW // Low so it doesn't chirp every second on tick updates
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = desc
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showActiveFocusNotification(minutesRemaining: Int, secondsRemaining: Int, modeName: String, isPaused: Boolean) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action Pause/Resume broadcast pending intent
        val actionIntent = Intent(if (isPaused) ACTION_RESUME else ACTION_PAUSE)
        val actionPendingIntent = PendingIntent.getBroadcast(
            context, 1, actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actionTitle = if (isPaused) "Resume" else "Pause"
        val actionIcon = if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause

        val contentText = String.format("%02d:%02d remaining • Screen Shield active", minutesRemaining, secondsRemaining)
        val title = if (isPaused) "$modeName Session Paused" else "Focusing on $modeName..."

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(!isPaused)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .addAction(actionIcon, actionTitle, actionPendingIntent)
            .setOnlyAlertOnce(true)

        notificationManager.notify(PERSISTENT_NOTIFICATION_ID, builder.build())
    }

    fun cancelActiveFocusNotification() {
        notificationManager.cancel(PERSISTENT_NOTIFICATION_ID)
    }

    fun showSessionCompleteNotification(modeName: String, pointsEarned: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Focus Session Complete! \uD83C\uDF89")
            .setContentText("Awesome job! You completed your $modeName session and earned +$pointsEarned focus points.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)

        notificationManager.notify(COMPLETE_NOTIFICATION_ID, builder.build())
    }
}
