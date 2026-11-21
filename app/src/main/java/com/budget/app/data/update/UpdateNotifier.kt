package com.budget.app.data.update

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
import com.budget.app.MainActivity
import com.budget.app.R
import com.budget.app.data.Settings

/**
 * "Update available — Version X is ready to install." Posted once per new
 * version: the last notified versionCode is saved, so the same version never
 * notifies twice. Tapping it opens the app on the Update screen.
 */
object UpdateNotifier {
    private const val CHANNEL = "updates"
    private const val ID = 1001
    const val EXTRA_OPEN = "open"
    const val OPEN_UPDATE = "update"

    suspend fun notifyOnce(context: Context, settings: Settings, info: UpdateInfo) {
        // Off unless turned on under More → Notifications; the More tab's dot still shows.
        if (settings.get(com.budget.app.data.AlertKind.UPDATE.setting) != "on") return
        val last = settings.get(Settings.UPDATE_NOTIFIED)?.toIntOrNull() ?: 0
        if (info.versionCode <= last) return
        if (!canPost(context)) return

        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN, OPEN_UPDATE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_budget)
                .setColor(com.budget.app.ui.theme.BrandBlue)
            .setContentTitle("Update available")
            .setContentText("Version ${info.versionName} is ready to install.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID, notification)
            settings.set(Settings.UPDATE_NOTIFIED, info.versionCode.toString())
        } catch (_: SecurityException) {
            // Permission withdrawn between the check and the post; try next time.
        }
    }

    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a new version of Ledger is ready to install"
            },
        )
    }
}
