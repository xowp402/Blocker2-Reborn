package com.blocker2.app.accessibility

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
import com.blocker2.app.MainActivity

/**
 * Shows a privacy-preserving notification when an app receives a timed block.
 * The matched word and page contents are deliberately never included in the notification.
 */
object BlockNotificationManager {
    private const val CHANNEL_ID = "active_app_blocks"
    private const val CHANNEL_NAME = "مسدودسازی برنامه‌ها"
    private const val NOTIFICATION_ID_BASE = 20_000

    fun showTimedBlock(
        context: Context,
        packageName: String,
        expiresAt: Long,
        currentTimeMillis: Long = System.currentTimeMillis()
    ) {
        val remainingMillis = (expiresAt - currentTimeMillis).coerceAtLeast(0L)
        if (remainingMillis <= 0L) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        createChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_BLOCKED_PACKAGE, packageName)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            packageName.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val minutes = ((remainingMillis + 59_999L) / 60_000L).coerceAtLeast(1L)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Blocker2؛ برنامه موقتاً مسدود شد")
            .setContentText("برای حفظ تمرکز، دسترسی به این برنامه محدود شده است.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "برای حفظ تمرکز، دسترسی به این برنامه تا پایان زمان تعیین‌شده محدود شده است. " +
                        "زمان باقی‌مانده هنگام فعال‌سازی: حدود $minutes دقیقه."
                )
            )
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(remainingMillis)
            .build()

        manager.notify(NOTIFICATION_ID_BASE + packageName.hashCode().and(0x7fff), notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "اعلان هنگام شروع مسدودسازی زمان‌دار برنامه‌ها"
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    const val EXTRA_BLOCKED_PACKAGE = "com.blocker2.app.extra.BLOCKED_PACKAGE"
}
