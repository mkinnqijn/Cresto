package com.nevoit.cresto.data.todo.reminder

import android.content.Context
import com.nevoit.cresto.R
import com.nevoit.cresto.data.todo.TodoItem
import com.nevoit.cresto.data.todo.TodoReminderMode
import com.nevoit.cresto.data.todo.hasDateRange
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal fun TodoItem.buildReminderNotificationText(
    context: Context,
    today: LocalDate = LocalDate.now()
): String {
    return buildReminderNotificationText(
        getString = { resourceId, formatArgs ->
            context.getString(resourceId, *formatArgs.toTypedArray())
        },
        today = today
    )
}

internal fun TodoItem.buildReminderNotificationText(
    getString: (resourceId: Int, formatArgs: List<Any>) -> String,
    today: LocalDate = LocalDate.now()
): String {
    fun text(resourceId: Int, vararg formatArgs: Any): String {
        return getString(resourceId, formatArgs.toList())
    }

    fun formatDate(date: LocalDate): String {
        return when (date) {
            today -> text(R.string.today)
            today.plusDays(1) -> text(R.string.tomorrow)
            else -> text(
                R.string.reminder_notification_date_format,
                date.monthValue,
                date.dayOfMonth
            )
        }
    }

    fun formatDateTime(date: LocalDate?, time: LocalTime): String {
        val timeText = time.format(REMINDER_NOTIFICATION_TIME_FORMATTER)
        return if (date == null) timeText else "${formatDate(date)} $timeText"
    }

    return when (reminderMode) {
        TodoReminderMode.BeforeStart -> {
            val start = startTime ?: return text(R.string.reminder_notification_default_content)
            val startText = formatDateTime(dueDate, start)
            val endText = endTime?.format(REMINDER_NOTIFICATION_TIME_FORMATTER)

            if (endText == null) {
                text(R.string.reminder_notification_start_time, startText)
            } else {
                text(R.string.reminder_notification_time_range, startText, endText)
            }
        }

        TodoReminderMode.BeforeDueDate -> {
            val date = dueDate
            val dayOffset = reminderDayOffset
            val time = reminderTime

            when {
                date != null && dayOffset != null && dayOffset > 0 -> {
                    text(
                        if (hasDateRange()) {
                            R.string.reminder_notification_days_until_start
                        } else {
                            R.string.reminder_notification_days_until_due
                        },
                        formatDate(date),
                        dayOffset
                    )
                }

                date != null -> {
                    text(
                        if (hasDateRange()) {
                            R.string.reminder_notification_start_date
                        } else {
                            R.string.reminder_notification_due_date
                        },
                        formatDate(date)
                    )
                }

                time != null -> {
                    text(
                        R.string.reminder_notification_reminder_time,
                        time.format(REMINDER_NOTIFICATION_TIME_FORMATTER)
                    )
                }

                else -> text(R.string.reminder_notification_default_content)
            }
        }

        null -> text(R.string.reminder_notification_default_content)
    }
}

private val REMINDER_NOTIFICATION_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
