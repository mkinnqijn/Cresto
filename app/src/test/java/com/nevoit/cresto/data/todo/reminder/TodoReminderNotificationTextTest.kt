package com.nevoit.cresto.data.todo.reminder

import com.nevoit.cresto.R
import com.nevoit.cresto.data.todo.TodoItem
import com.nevoit.cresto.data.todo.TodoReminderMode
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoReminderNotificationTextTest {
    @Test
    fun dueDateReminderUsesTheDateAtDelivery() {
        val dueDate = LocalDate.of(2026, 8, 8)
        val todo = TodoItem(
            title = "Tomorrow's task",
            dueDate = dueDate,
            reminderMode = TodoReminderMode.BeforeDueDate,
            reminderDayOffset = 0,
            reminderTime = LocalTime.of(8, 0)
        )

        assertEquals("Due date: Tomorrow", format(todo, dueDate.minusDays(1)))
        assertEquals("Due date: Today", format(todo, dueDate))
    }

    @Test
    fun startReminderUsesTheDateAtDelivery() {
        val dueDate = LocalDate.of(2026, 8, 8)
        val todo = TodoItem(
            title = "Timed task",
            dueDate = dueDate,
            startTime = LocalTime.of(8, 0),
            reminderMode = TodoReminderMode.BeforeStart,
            reminderOffsetMinutes = 0
        )

        assertEquals("Starts at Today 08:00", format(todo, dueDate))
    }

    @Test
    fun rangeDateReminderUsesStartDateLanguage() {
        val startDate = LocalDate.of(2026, 10, 19)
        val todo = TodoItem(
            title = "Multi-day task",
            dueDate = startDate,
            endDate = startDate.plusDays(5),
            reminderMode = TodoReminderMode.BeforeDueDate,
            reminderDayOffset = 0,
            reminderTime = LocalTime.of(8, 0)
        )

        assertEquals("Start date: Today", format(todo, startDate))
    }

    private fun format(todo: TodoItem, today: LocalDate): String {
        return todo.buildReminderNotificationText(::testString, today)
    }

    private fun testString(resourceId: Int, formatArgs: List<Any>): String {
        return when (resourceId) {
            R.string.today -> "Today"
            R.string.tomorrow -> "Tomorrow"
            R.string.reminder_notification_date_format -> "${formatArgs[0]}/${formatArgs[1]}"
            R.string.reminder_notification_start_time -> "Starts at ${formatArgs[0]}"
            R.string.reminder_notification_time_range -> "Time: ${formatArgs[0]}-${formatArgs[1]}"
            R.string.reminder_notification_days_until_due -> {
                "Due on ${formatArgs[0]}, in ${formatArgs[1]} days"
            }

            R.string.reminder_notification_days_until_start -> {
                "Starts on ${formatArgs[0]}, in ${formatArgs[1]} days"
            }

            R.string.reminder_notification_due_date -> "Due date: ${formatArgs[0]}"
            R.string.reminder_notification_start_date -> "Start date: ${formatArgs[0]}"
            R.string.reminder_notification_reminder_time -> "Reminder time: ${formatArgs[0]}"
            R.string.reminder_notification_default_content -> "Reminder"
            else -> error("Unexpected string resource: $resourceId")
        }
    }
}
