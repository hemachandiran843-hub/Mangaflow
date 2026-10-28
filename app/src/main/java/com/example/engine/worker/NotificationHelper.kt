package com.example.engine.worker

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
import com.example.MainActivity

object NotificationHelper {

    const val CHANNEL_ID = "mangaflow_new_chapters"
    const val CHANNEL_NAME = "New Chapter Releases"

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when tracked manhwa have new chapters available"
                enableLights(true)
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showNewChapterNotification(
        context: Context,
        seriesId: Long? = null,
        manhwaTitle: String,
        chapterTitle: String = "",
        chapterIdentifier: String = "",
        chapterNumber: Float = 1.0f,
        url: String
    ) {
        if (!hasNotificationPermission(context)) return

        createNotificationChannel(context)

        val notifId = ((seriesId ?: 0L) * 31 + chapterIdentifier.hashCode()).toInt()

        // 1. Primary Tap Intent -> Opens Chapter in Browser
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_URL", url)
            if (seriesId != null) putExtra("SERIES_ID", seriesId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayChapter = if (chapterTitle.isNotBlank()) chapterTitle else "Chapter $chapterIdentifier"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Chapter: $manhwaTitle")
            .setContentText("$displayChapter is now available!")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$displayChapter is now available for $manhwaTitle. Tap to start reading immediately.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_view,
                "Read Chapter",
                openPendingIntent
            )
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifId, notification)
    }

    // Backwards compatibility overload
    fun showNewChapterNotification(
        context: Context,
        manhwaTitle: String,
        newChapter: Float,
        url: String
    ) {
        showNewChapterNotification(
            context = context,
            seriesId = null,
            manhwaTitle = manhwaTitle,
            chapterTitle = "Chapter ${newChapter.toInt()}",
            chapterIdentifier = newChapter.toInt().toString(),
            chapterNumber = newChapter,
            url = url
        )
    }
}
