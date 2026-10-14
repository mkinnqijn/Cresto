package com.nevoit.cresto.feature.widget

import com.nevoit.cresto.data.todo.TodoItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.Duration
import java.time.format.DateTimeFormatter

internal data class WidgetTodoSection(
    val date: LocalDate,
    val totalCount: Int,
    val todos: List<TodoItem>
)

internal const val WIDGET_COMPLETION_UNDO_MINUTES = 3L

/** Widget ordering is chronological, independent of the app's user-selected sort or pins. */
internal fun upcomingWidgetSections(
    todos: List<TodoItem>,
    today: LocalDate,
    now: LocalDateTime = LocalDateTime.now()
): List<WidgetTodoSection> {
    val completionCutoff = now.minusMinutes(WIDGET_COMPLETION_UNDO_MINUTES)
    val upcoming = todos.asSequence()
        .filter { todo ->
            val dueDate = todo.dueDate
            val recentlyCompleted = todo.isCompleted &&
                todo.completedDateTime?.isAfter(completionCutoff) == true
            dueDate != null &&
                !dueDate.isBefore(today) &&
                (!todo.isCompleted || recentlyCompleted) &&
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

    val countsByDate = upcoming.asSequence()
        .filterNot { it.isCompleted }
        .groupingBy { requireNotNull(it.dueDate) }
        .eachCount()
    return upcoming
        .groupBy { requireNotNull(it.dueDate) }
        .map { (date, visibleTodos) ->
            WidgetTodoSection(
                date = date,
                totalCount = countsByDate[date] ?: 0,
                todos = visibleTodos
            )
        }
}

internal fun nextWidgetCompletionExpiryDelayMillis(
    todos: List<TodoItem>,
    today: LocalDate,
    now: LocalDateTime = LocalDateTime.now()
): Long? = todos.asSequence()
    .filter { todo ->
        todo.isCompleted &&
            todo.deletedAt == null &&
            todo.dueDate?.let { !it.isBefore(today) } == true
    }
    .mapNotNull { it.completedDateTime }
    .map { completedAt ->
        Duration.between(
            now,
            completedAt.plusMinutes(WIDGET_COMPLETION_UNDO_MINUTES)
        ).toMillis()
    }
    .filter { it > 0L }
    .minOrNull()

/** Keeps only complete date sections and rows that fit the widget's measured content height. */
internal fun fitWidgetSections(
    sections: List<WidgetTodoSection>,
    availableHeightDp: Float,
    rowHeightDp: Float,
    sectionHeaderHeightDp: Float
): List<WidgetTodoSection> {
    var remainingHeight = availableHeightDp.coerceAtLeast(0f)
    val visibleSections = mutableListOf<WidgetTodoSection>()

    for (section in sections) {
        if (section.todos.isEmpty() || remainingHeight < sectionHeaderHeightDp + rowHeightDp) break

        remainingHeight -= sectionHeaderHeightDp
        val visibleTodos = section.todos.takeWhile {
            if (remainingHeight < rowHeightDp) {
                false
            } else {
                remainingHeight -= rowHeightDp
                true
            }
        }
        if (visibleTodos.isEmpty()) break
        visibleSections += section.copy(todos = visibleTodos)
        if (visibleTodos.size < section.todos.size) break
    }

    return visibleSections
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
