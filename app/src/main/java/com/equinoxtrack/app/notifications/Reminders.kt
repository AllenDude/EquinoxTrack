package com.equinoxtrack.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.equinoxtrack.app.MainActivity
import com.equinoxtrack.app.R
import com.equinoxtrack.app.appContainer
import com.equinoxtrack.app.domain.Money
import com.equinoxtrack.app.ui.DeepLinks
import com.equinoxtrack.app.ui.common.Fmt
import com.equinoxtrack.app.widget.WidgetUpdater
import java.time.LocalDate
import java.util.concurrent.TimeUnit

object Reminders {
    const val CHANNEL_ID = "bill_reminders"
    private const val WORK_NAME = "equinox-daily"
    private const val PREFS = "reminder_log"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Bill reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Reminders before bills and subscriptions are due"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Periodic work: bill reminders + a widget refresh so Safe-to-Spend rolls over each day. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DailyWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Posts at most one reminder per bill occurrence per day. */
    @SuppressLint("MissingPermission")
    suspend fun checkBills(context: Context) {
        val container = context.appContainer
        val settings = container.settings.current()
        if (!settings.billReminders || !canNotify(context)) return
        val ledger = container.loadLedger()
        val today = LocalDate.now()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sent = prefs.getStringSet("sent", emptySet()).orEmpty().filter { it.endsWith(":$today") }.toMutableSet()
        val manager = NotificationManagerCompat.from(context)

        ledger.billDues(today.plusDays(7)).filter { it.overdue || it.daysUntil <= it.bill.reminderDaysBefore }.forEach { due ->
            val key = "${due.bill.id}:${due.dueDate}:$today"
            if (key in sent) return@forEach
            val intent = Intent(context, MainActivity::class.java)
                .setData(Uri.parse("equinoxtrack://bill/${due.bill.id}"))
                .putExtra(DeepLinks.EXTRA, DeepLinks.BILLS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending = PendingIntent.getActivity(context, due.bill.id.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val amount = if (settings.hideBalances) "" else " · ${Money.format(due.amountMinor, due.bill.currency)}"
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(due.bill.name)
                .setContentText(Fmt.dueLabel(due.daysUntil) + amount)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            manager.notify(due.bill.id.toInt(), notification)
            sent += key
        }
        prefs.edit().putStringSet("sent", sent).apply()
    }
}

class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { Reminders.checkBills(applicationContext) }
        runCatching { WidgetUpdater.updateAll(applicationContext) }
        return Result.success()
    }
}
