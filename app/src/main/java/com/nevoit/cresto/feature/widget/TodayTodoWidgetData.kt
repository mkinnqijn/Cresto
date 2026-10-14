package com.nevoit.cresto.feature.widget

import com.nevoit.cresto.data.todo.TodoItem
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal const val WIDGET_TODO_LIMIT = 2

internal data class WidgetTodoSection(
    val date: LocalDate,
    val totalCount: Int,
    val todos: List<TodoItem>
)

/** Widget ordering is chronological, independent of the app's user-selected sort or pins. */
internal fun upcomingWidgetSections(
    todos: List<TodoItem>,
    today: LocalDate,
    limit: Int = WIDGET_TODO_LIMIT
): List<WidgetTodoSection> {
    val upcoming = todos.asSequence()
        .filter { todo ->
            val dueDate = todo.dueDate
            dueDate != null &&
                !dueDate.isBefore(today) &&
                !todo.isCompleted &&
                todo.deletedAt == null
        }
        .sortedWith(
            compareBy<TodoItem> { it.dueDate }
                .thenBy { it.startTime == null }
                .thenBy { it.startTime }
                .thenBy { it.creationDateTime }
                .thenBy { it.id }
        )
        .toList()

    val countsByDate = upcoming.groupingBy { requireNotNull(it.dueDate) }.eachCount()
    return upcoming.take(limit.coerceAtLeast(0))
        .groupBy { requireNotNull(it.dueDate) }
        .map { (date, visibleTodos) ->
            WidgetTodoSection(
                date = date,
                totalCount = countsByDate.getValue(date),
                todos = visibleTodos
            )
        }
}

internal fun TodoItem.widgetTime(): String =
    startTime?.format(DateTimeFormatter.ofPattern("HH:mm")).orEmpty()

internal fun widgetDateLabel(
    date: LocalDate,
    today: LocalDate,
    todayLabel: String,
    tomorrowLabel: String
): String {
    val shortDate = "${date.monthValue}/${date.dayOfMonth}"
    return when (date) {
        today -> "$todayLabel $shortDate"
        today.plusDays(1) -> "$tomorrowLabel $shortDate"
        else -> shortDate
    }
}

internal fun nextWidgetMidnight(now: ZonedDateTime): ZonedDateTime =
    now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
