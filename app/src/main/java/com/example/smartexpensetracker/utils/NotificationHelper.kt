package com.example.smartexpensetracker.utils

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.smartexpensetracker.MainActivity
import com.example.smartexpensetracker.R

// ─── Notification channels ────────────────────────────────────────────────────

object NotificationHelper {

    const val CHANNEL_REMINDERS    = "note_reminders"
    const val CHANNEL_BUDGET_RESET = "budget_reset"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                "Note Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Alerts for notes with a set reminder time" }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BUDGET_RESET,
                "Budget Reset",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Monthly budget cycle reset notification" }
        )
    }

    // ── Note reminder ─────────────────────────────────────────────────────────

    /**
     * Schedule a local notification for a note at [triggerAtMillis].
     * [noteId] is used as the notification ID so it can be cancelled later.
     */
    fun scheduleNoteReminder(context: Context, noteId: Int, noteText: String, triggerAtMillis: Long) {
        val intent = Intent(context, NoteReminderReceiver::class.java).apply {
            putExtra("note_id",   noteId)
            putExtra("note_text", noteText)
        }
        val pi = PendingIntent.getBroadcast(
            context, noteId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Use setExactAndAllowWhileIdle so it fires even in Doze mode
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
    }

    fun cancelNoteReminder(context: Context, noteId: Int) {
        val intent = Intent(context, NoteReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, noteId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(noteId)
    }

    // ── Budget reset notification ─────────────────────────────────────────────

    fun showBudgetResetNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_BUDGET_RESET)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Budget Reset")
            .setContentText("Your monthly budget has been reset. Start fresh!")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        nm.notify(1001, notif)
    }

    /**
     * Schedule a repeating budget-reset alarm at [resetDayOfMonth].
     * The next occurrence is calculated automatically.
     */
    fun scheduleBudgetResetAlarm(context: Context, resetDayOfMonth: Int) {
        val intent = Intent(context, BudgetResetReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 2000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, resetDayOfMonth.coerceIn(1, 28))
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            // If today is past the reset day this month, schedule for next month
            if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.MONTH, 1)
        }
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
    }

    fun cancelBudgetResetAlarm(context: Context) {
        val intent = Intent(context, BudgetResetReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 2000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
    }
}

// ─── BroadcastReceivers ───────────────────────────────────────────────────────

class NoteReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId   = intent.getIntExtra("note_id", 0)
        val noteText = intent.getStringExtra("note_text") ?: "Reminder"

        val tapIntent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, noteId, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Note Reminder")
            .setContentText(noteText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(noteText))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(noteId, notif)
    }
}

class BudgetResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.showBudgetResetNotification(context)
    }
}