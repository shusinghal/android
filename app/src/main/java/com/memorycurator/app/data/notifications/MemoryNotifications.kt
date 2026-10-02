package com.memorycurator.app.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.memorycurator.app.MainActivity
import com.memorycurator.app.data.local.DatabaseProvider
import com.memorycurator.app.data.local.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

object MemoryNotifications {

    val messages = listOf(
        "You captured some wonderful moments recently! Tap to explore your fresh memories.",
        "Unexplored memories are waiting for you in Footage. Take a look!",
        "Relive the best parts of your day. Open Footage to see your latest photos.",
        "New memories captured! Discover and curate your recent shots.",
        "Got some great snaps? Let's check them out in Footage.",
        "Your recent moments are calling! Open up Footage to relive them.",
        "Take a stroll down memory lane. Your latest photos are ready.",
        "Captured anything exciting lately? Check your fresh timeline.",
        "Turn your recent photos into cherished memories with Footage.",
        "A lot happened in the last few days! See your latest shots.",
        "Your camera roll has fresh stories. Tap to explore.",
        "Ready to relive your recent adventures? Open Footage now.",
        "Moments worth remembering are right here in your timeline.",
        "Explore your recent photos and discover your favorite takes.",
        "Fresh captures waiting for you! Dive into your timeline.",
        "What a week! Let's review your latest memories.",
        "Capture. Relive. Cherish. Open Footage to see your new snaps.",
        "Your recent gallery moments deserve a spotlight.",
        "Check out what you snapped recently in Footage.",
        "Relive your day's highlights. Tap to view your fresh timeline."
    )

    fun checkAndSendMemoryNotification(context: Context) {
        val userPrefs = UserPreferences(context)
        if (!userPrefs.isNotificationsEnabled) return

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        // Condition: current hour from 10 AM (10) to 9 PM (20)
        if (currentHour !in 10..20) return

        val lastNotifTime = userPrefs.lastNotificationTime
        val hoursElapsed = (System.currentTimeMillis() - lastNotifTime) / (60 * 60 * 1000L)
        // Condition: last notification datetime - current time hour > 18 (at least 18 hours elapsed)
        if (lastNotifTime != 0L && hoursElapsed < 18L) return

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val database = DatabaseProvider.getDatabase(context)
        val threeDaysAgo = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000L)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val allMedia = database.mediaDao().getAllMediaSync()
                // Condition: If AIScore on photo = null (aiScore == -1f) AND Today - photo timestamp date < 3
                val hasRecentUnanalyzed = allMedia.any { it.aiScore == -1f && it.dateTaken >= threeDaysAgo }

                if (hasRecentUnanalyzed) {
                    val randomMessage = messages.random()
                    sendNotification(context, randomMessage)
                    userPrefs.lastNotificationTime = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendNotification(context: Context, message: String) {
        val channelId = "footage_memories_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Footage Memories",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Reminders to explore and curate your recent memories"
        }
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_TIMELINE", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("Footage")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(1001, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
