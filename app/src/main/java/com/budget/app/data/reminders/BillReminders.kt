package com.budget.app.data.reminders

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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.budget.app.BudgetApp
import com.budget.app.MainActivity
import com.budget.app.R
import com.budget.app.data.ReminderLead
import com.budget.app.data.ReminderLogic
import com.budget.app.data.ReminderMessage
import com.budget.app.data.SessionState
import com.budget.app.data.Settings
import com.budget.app.data.sync.Overlay
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * Bill reminders. Runs about hourly; the first run after 8 AM each day looks
 * at this month (and next, when the lead reaches into it), skips anything
 * already paid or waiting to send, and posts one notification for the bills
 * due. Works from the saved copy when offline.
 */
class BillReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as BudgetApp).container
        val lead = ReminderLead.from(c.settings.get(Settings.REMINDER_LEAD))
        if (lead == ReminderLead.OFF) return Result.success()

        val now = LocalDateTime.now()
        if (now.hour < MORNING_HOUR) return Result.success()
        val today = now.toLocalDate()
        if (c.settings.get(Settings.REMINDER_LAST_DAY) == today.toString()) return Result.success()

        if (c.session.state.value is SessionState.Loading) c.session.load()
        if (c.session.state.value !is SessionState.SignedIn) return Result.success()

        val target = today.plusDays(lead.days.toLong())
        val pending = c.local.queueSnapshot()
        val views = listOf(YearMonth.from(today), YearMonth.from(target)).distinct().mapNotNull { ym ->
            val view = try {
                c.api.month(ym.year, ym.monthValue).also { c.local.saveMonth(it) }
            } catch (_: Exception) {
                c.local.loadMonth(ym.year, ym.monthValue)
            }
            view?.let { Overlay.apply(it, pending) }
        }
        // Nothing to go on yet (never loaded, offline): try again next run.
        if (views.isEmpty()) return Result.success()

        c.settings.set(Settings.REMINDER_LAST_DAY, today.toString())
        ReminderLogic.message(ReminderLogic.dueOn(views, today, lead), lead)?.let { post(applicationContext, it) }
        return Result.success()
    }

    companion object {
        private const val NAME = "bill-reminders"
        private const val CHANNEL = "reminders"
        private const val NOTIFICATION_ID = 2001
        private const val MORNING_HOUR = 8

        /** Starts or stops the hourly job to match the setting. */
        fun sync(context: Context, lead: ReminderLead) {
            val wm = WorkManager.getInstance(context)
            if (lead == ReminderLead.OFF) {
                wm.cancelUniqueWork(NAME)
            } else {
                val request = PeriodicWorkRequestBuilder<BillReminderWorker>(1, TimeUnit.HOURS).build()
                wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }

        fun canPost(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return false
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        private fun post(context: Context, message: ReminderMessage) {
            if (!canPost(context)) return
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager != null && manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Bill reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Bills coming due, if you turn reminders on under More"
                    },
                )
            }
            val open = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val style = NotificationCompat.InboxStyle().also { s -> message.lines.forEach { s.addLine(it) } }
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_budget)
                .setColor(com.budget.app.ui.theme.BrandBlue)
                .setContentTitle(message.title)
                .setContentText(message.body)
                .setStyle(if (message.lines.size > 1) style else NotificationCompat.BigTextStyle().bigText(message.body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
                // Permission withdrawn; nothing to do.
            }
        }
    }
}
