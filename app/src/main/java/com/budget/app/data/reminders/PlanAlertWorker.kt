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
import com.budget.app.data.Money
import com.budget.app.data.PlanAlerts
import com.budget.app.data.SessionState
import com.budget.app.data.Settings
import com.budget.app.ui.theme.BrandBlue
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Spending plan alerts (server 0.42+): on payday morning, what the payday plan
 * says to do; and once per pay period, when the money left runs out. Each
 * posts at most once. Needs the server; offline, it tries next run.
 */
class PlanAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as BudgetApp).container
        if (LocalDateTime.now().hour < MORNING_HOUR) return Result.success()
        if (c.session.state.value is SessionState.Loading) c.session.load()
        if (c.session.state.value !is SessionState.SignedIn) return Result.success()
        val plan = try {
            c.api.envelopes(false)
        } catch (_: Exception) {
            return Result.success()
        }
        val today = LocalDate.now().toString()
        if (plan.period.from == today && c.settings.get(Settings.PLAN_PAYDAY_LAST) != today) {
            PlanAlerts.payday(plan)?.let { (title, body) ->
                c.settings.set(Settings.PLAN_PAYDAY_LAST, today)
                post(title, body, PAYDAY_ID)
            }
        }
        if ((plan.standing == "empty" || plan.standing == "short") && c.settings.get(Settings.PLAN_EMPTY_LAST) != plan.period.from) {
            c.settings.set(Settings.PLAN_EMPTY_LAST, plan.period.from)
            val left = plan.allLeftCents ?: plan.flexibleLeftCents
            post(
                if (left < 0) "${Money.whole(-left)} over until payday" else "Nothing left to spend until payday",
                plan.message ?: "Anything more goes on a card you can't pay in full.",
                EMPTY_ID,
            )
        }
        return Result.success()
    }

    private fun post(title: String, body: String, id: Int) {
        if (!BillReminderWorker.canPost(applicationContext)) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager != null && manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Spending plan", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "The payday plan, and when the money left until payday runs out"
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
        private const val PAYDAY_ID = 2004
        private const val EMPTY_ID = 2005
        private const val MORNING_HOUR = 8

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PlanAlertWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
