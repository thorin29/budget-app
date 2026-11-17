package com.budget.app.data.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.budget.app.BudgetApp
import com.budget.app.MainActivity
import com.budget.app.R
import com.budget.app.data.PlanAlerts
import com.budget.app.data.SessionState
import com.budget.app.data.Settings
import com.budget.app.ui.theme.BrandBlue
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * The running-low alert (server 0.43+): one alert on the month's total, when
 * safe to spend drops under $200, and another if it goes over. Never per
 * budget. Each posts at most once a month. Needs the server; offline, it
 * tries next run.
 */
class PlanAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as BudgetApp).container
        if (LocalDateTime.now().hour < MORNING_HOUR) return Result.success()
        if (c.session.state.value is SessionState.Loading) c.session.load()
        if (c.session.state.value !is SessionState.SignedIn) return Result.success()
        val plan = try {
            c.api.plan()
        } catch (_: Exception) {
            return Result.success()
        }
        // Checking running short on a coming due date: once per date.
        runCatching { c.api.cash() }.getOrNull()?.let { cash ->
            val on = cash.shortOn
            if (!cash.missing && on != null && c.settings.get(Settings.PLAN_PAYDAY_LAST) != "short:$on") {
                c.settings.set(Settings.PLAN_PAYDAY_LAST, "short:$on")
                val step = cash.steps.firstOrNull { it.date == on && it.balanceCents < 0 }
                post(
                    "Checking runs short on ${com.budget.app.data.Dates.short(on) ?: on}",
                    "${step?.let { "${it.label} takes ${com.budget.app.data.Money.whole(-it.cents)} and " } ?: ""}checking would drop to ${com.budget.app.data.Money.whole(cash.lowCents)}. Move money in or pay something later before then.",
                    SHORT_ID,
                )
            }
        }
        val level = PlanAlerts.level(plan) ?: return Result.success()
        val key = "${plan.month}:$level"
        // Low then over is two alerts; low twice is one.
        val last = c.settings.get(Settings.PLAN_EMPTY_LAST)
        if (last == key || (level == "low" && last == "${plan.month}:over")) return Result.success()
        PlanAlerts.words(plan)?.let { (title, body) ->
            c.settings.set(Settings.PLAN_EMPTY_LAST, key)
            post(title, body, EMPTY_ID)
        }
        return Result.success()
    }

    private fun post(title: String, body: String, id: Int) {
        if (!BillReminderWorker.canPost(applicationContext)) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager != null && manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Spending plan", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When what's safe to spend this month runs low, and when checking would run short on a due date"
                },
            )
        }
        val open = PendingIntent.getActivity(
            applicationContext, 4,
            Intent(applicationContext, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, "insights")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_budget)
            .setColor(BrandBlue)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        private const val NAME = "plan-alerts"
        private const val CHANNEL = "spending-plan"
        private const val EMPTY_ID = 2005
        private const val SHORT_ID = 2004
        private const val MORNING_HOUR = 8

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PlanAlertWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
