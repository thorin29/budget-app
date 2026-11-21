package com.budget.app.data.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.budget.app.BudgetApp
import com.budget.app.MainActivity
import com.budget.app.R
import com.budget.app.data.AlertKind
import com.budget.app.data.AlertRules
import com.budget.app.data.SessionState
import com.budget.app.data.remote.AlertDto
import com.budget.app.ui.theme.BrandBlue
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Notifications (server 0.101+). About every 30 minutes, while any kind is on, asks the server
 * what could notify now, keeps the kinds turned on under More → Notifications and posts each new
 * one once. Quiet hours hold them until 7 AM. Everything is off until the person turns it on.
 */
class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as BudgetApp).container
        val enabled = AlertKind.entries.filter { it != AlertKind.UPDATE && c.settings.get(it.setting) == "on" }.map { it.key }.toSet()
        if (enabled.isEmpty()) return Result.success()
        if (c.session.state.value is SessionState.Loading) c.session.load()
        if (c.session.state.value !is SessionState.SignedIn) return Result.success()
        val alerts = try {
            c.api.alerts().alerts
        } catch (_: Exception) {
            return Result.success()
        }
        val seen = c.settings.get(AlertKind.SEEN)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
        val quiet = c.settings.get(AlertKind.QUIET) != "off"
        val now = AlertRules.pick(alerts, enabled, seen.toSet(), LocalDateTime.now(), quiet)
        if (now.isEmpty()) return Result.success()
        if (!BillReminderWorker.canPost(applicationContext)) return Result.success()
        now.forEach { post(applicationContext, it) }
        c.settings.set(AlertKind.SEEN, AlertRules.remember(seen, now.map { it.key }).joinToString("\n"))
        return Result.success()
    }

    companion object {
        private const val NAME = "alerts"
        private const val CHANNEL = "alerts"
        const val EXTRA_OPEN_SCREEN = "open_screen"

        /** Runs while any notification is on; stops when none is. */
        fun sync(context: Context, anyOn: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!anyOn) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<AlertWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** The older reminder jobs, replaced by this one. */
        fun cancelOld(context: Context) {
            val wm = WorkManager.getInstance(context)
            listOf("bill-reminders", "card-reminders", "weekly-summary", "plan-alerts").forEach { wm.cancelUniqueWork(it) }
        }

        private fun post(context: Context, a: AlertDto) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager != null && manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Ledger", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Bills, cards, budgets and the bank, as chosen under More → Notifications"
                    },
                )
            }
            val open = PendingIntent.getActivity(
                context, a.key.hashCode(),
                Intent(context, MainActivity::class.java)
                    .putExtra(EXTRA_OPEN_SCREEN, a.open)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_budget)
                .setColor(BrandBlue)
                .setContentTitle(a.title)
                .setContentText(a.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(a.body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(a.key.hashCode(), n)
            } catch (_: SecurityException) {
            }
        }
    }
}
