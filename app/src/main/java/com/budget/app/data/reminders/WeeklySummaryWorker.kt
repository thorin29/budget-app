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
import com.budget.app.data.SessionState
import com.budget.app.data.Settings
import com.budget.app.data.WeeklyLogic
import com.budget.app.ui.theme.BrandBlue
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * The weekly summary: once each Sunday evening, the week's everyday spending,
 * the month's pace, what's left after bills, and anything coming up. Off
 * unless turned on under More. Needs the server; offline, it tries next run.
 */
class WeeklySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as BudgetApp).container
        if (c.settings.get(Settings.WEEKLY_SUMMARY) != "on") return Result.success()
        val now = LocalDateTime.now()
        if (now.dayOfWeek != DayOfWeek.SUNDAY || now.hour < EVENING_HOUR) return Result.success()
        val sunday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).toString()
        if (c.settings.get(Settings.WEEKLY_SUMMARY_LAST) == sunday) return Result.success()

        if (c.session.state.value is SessionState.Loading) c.session.load()
        if (c.session.state.value !is SessionState.SignedIn) return Result.success()
        val insights = try {
            c.api.insights()
        } catch (_: Exception) {
            return Result.success()
        }
        c.settings.set(Settings.WEEKLY_SUMMARY_LAST, sunday)
        WeeklyLogic.message(insights)?.let { m ->
            if (!BillReminderWorker.canPost(applicationContext)) return Result.success()
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            if (manager != null && manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Weekly summary", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "A Sunday evening summary, if you turn it on under More"
                    },
                )
            }
            val open = PendingIntent.getActivity(
                applicationContext, 3,
                Intent(applicationContext, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_TAB, "insights")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_budget)
                .setColor(BrandBlue)
                .setContentTitle(m.title)
                .setContentText(m.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(m.body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
            }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "weekly-summary"
        private const val CHANNEL = "weekly-summary"
        private const val NOTIFICATION_ID = 2003
        private const val EVENING_HOUR = 18

        fun sync(context: Context, on: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!on) {
                wm.cancelUniqueWork(NAME)
            } else {
                val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(1, TimeUnit.HOURS).build()
                wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }
    }
}
