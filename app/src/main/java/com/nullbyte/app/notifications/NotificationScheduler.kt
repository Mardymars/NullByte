package com.nullbyte.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nullbyte.app.MainActivity
import com.nullbyte.app.R
import java.util.concurrent.TimeUnit

object NotificationScheduler {
    private const val REMINDER_CHANNEL_ID = "nullbyte_reminders"
    private const val COMPLETE_CHANNEL_ID = "nullbyte_complete"
    private const val REMINDER_WORK_NAME = "nullbyte_daily_reminder"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return

        val manager = context.getSystemService(NotificationManager::class.java)
        val reminderChannel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "NullByte reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Optional local reminders to clean media before sharing."
        }
        val completeChannel = NotificationChannel(
            COMPLETE_CHANNEL_ID,
            "NullByte results",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Status updates after NullByte exports clean files."
        }

        manager.createNotificationChannel(reminderChannel)
        manager.createNotificationChannel(completeChannel)
    }

    fun scheduleDailyReminder(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            REMINDER_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancelDailyReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(REMINDER_WORK_NAME)
    }

    fun showReminderNotification(context: Context) {
        if (!canPostNotifications(context)) return

        val openIntent = Intent(context, MainActivity::class.java)
        val tutorialIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_TUTORIAL, true)
        }

        val openPendingIntent = PendingIntent.getActivity(
            context,
            1001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val tutorialPendingIntent = PendingIntent.getActivity(
            context,
            1002,
            tutorialIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Clean before you share")
            .setContentText("NullByte is ready when you want to strip share-sensitive metadata from media files.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(0, "Open NullByte", openPendingIntent)
            .addAction(0, "How it works", tutorialPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(7001, notification)
    }

    fun showSanitizedNotification(context: Context, savedCount: Int) {
        if (!canPostNotifications(context) || savedCount <= 0) return

        val openIntent = Intent(context, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            context,
            1003,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = if (savedCount == 1) {
            "1 clean copy is waiting in your NullByte media folders."
        } else {
            "$savedCount clean copies are waiting in your NullByte media folders."
        }

        val notification = NotificationCompat.Builder(context, COMPLETE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("NullByte finished cleaning")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(7002, notification)
    }

    private fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
