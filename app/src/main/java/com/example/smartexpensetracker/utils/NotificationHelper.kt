package com.example.smartexpensetracker.utils

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.smartexpensetracker.MainActivity
import com.example.smartexpensetracker.R

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } else {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
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