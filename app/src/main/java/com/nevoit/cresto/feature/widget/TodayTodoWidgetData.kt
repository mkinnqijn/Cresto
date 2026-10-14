package com.nevoit.cresto.feature.widget

import com.nevoit.cresto.data.todo.TodoItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.Duration
import java.time.format.DateTimeFormatter

internal data class WidgetTodoSection(
    val date: LocalDate,
    val totalCount: Int,
    val todos: List<TodoItem>,
    val isOverdue: Boolean = false
)

internal const val WIDGET_COMPLETION_UNDO_MINUTES = 3L

/** Widget ordering is chronological, independent of the app's user-selected sort or pins. */
internal fun upcomingWidgetSections(
    todos: List<TodoItem>,
    today: LocalDate,
    now: LocalDateTime = LocalDateTime.now(),
    hiddenCompletedTokens: Set<String> = emptySet()
): List<WidgetTodoSection> {
    val completionCutoff = now.minusMinutes(WIDGET_COMPLETION_UNDO_MINUTES)
    val nowTime = now.toLocalTime()
    val upcoming = todos.asSequence()
        .filter { todo ->
            val dueDate = todo.dueDate
            val recentlyCompleted = todo.isCompleted &&
                todo.completedDateTime?.isAfter(completionCutoff) == true &&
                todo.widgetCompletionToken() !in hiddenCompletedTokens
            dueDate != null &&
                !dueDate.isBefore(today) &&
                (!todo.isCompleted || recentlyCompleted) &&
                todo.deletedAt == null &&
                !todo.hasWidgetOverdueDeadline(today, nowTime)
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

/** Mirrors the app list's existing overdue/expired rules. */
internal fun TodoItem.isWidgetOverdue(today: LocalDate, nowTime: LocalTime): Boolean {
    if (isCompleted || deletedAt != null) return false
    return hasWidgetOverdueDeadline(today, nowTime)
}

private fun TodoItem.hasWidgetOverdueDeadline(today: LocalDate, nowTime: LocalTime): Boolean {
    val date = dueDate ?: return false
    if (date.isBefore(today)) return true
    if (date != today) return false
    return when {
        endTime != null -> nowTime.isAfter(endTime)
        startTime != null -> nowTime.isAfter(startTime)
        else -> false
    }
}

/** One merged overdue section, with the most recently expired items first. */
internal fun overdueWidgetSection(
    todos: List<TodoItem>,
    today: LocalDate,
    now: LocalDateTime = LocalDateTime.now(),
    limit: Int,
    hiddenCompletedTokens: Set<String> = emptySet()
): WidgetTodoSection? {
    val completionCutoff = now.minusMinutes(WIDGET_COMPLETION_UNDO_MINUTES)
    val overdue = todos.asSequence()
        .filter { todo ->
            val recentlyCompleted = todo.isCompleted &&
                todo.completedDateTime?.isAfter(completionCutoff) == true &&
                todo.widgetCompletionToken() !in hiddenCompletedTokens
            todo.deletedAt == null &&
                todo.hasWidgetOverdueDeadline(today, now.toLocalTime()) &&
                (!todo.isCompleted || recentlyCompleted)
        }
        .sortedWith(
            compareByDescending<TodoItem> { it.widgetExpiryDateTime(today) }
                .thenByDescending { it.creationDateTime }
                .thenByDescending { it.id }
        )
        .toList()
    if (overdue.isEmpty()) return null
    return WidgetTodoSection(
        date = today,
        totalCount = overdue.count { !it.isCompleted },
        todos = overdue.take(limit.coerceAtLeast(1)),
        isOverdue = true
    )
}

private fun TodoItem.widgetExpiryDateTime(today: LocalDate): LocalDateTime {
    val date = requireNotNull(dueDate)
    val time = endTime ?: startTime
    return when {
        time != null -> date.atTime(time)
        date.isBefore(today) -> date.plusDays(1).atStartOfDay()
        else -> date.atStartOfDay()
    }
}

/** Delay until the next incomplete item becomes overdue today. */
internal fun nextWidgetOverdueTransitionDelayMillis(
    todos: List<TodoItem>,
    today: LocalDate,
    now: LocalDateTime = LocalDateTime.now()
): Long? = todos.asSequence()
    .filter { todo ->
        !todo.isCompleted && todo.deletedAt == null && todo.dueDate == today
    }
    .mapNotNull { todo -> (todo.endTime ?: todo.startTime)?.let(today::atTime) }
    .map { transition -> Duration.between(now, transition).toMillis() }
    .filter { it >= 0L }
    .minOrNull()

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
    rowHeightDp: (TodoItem) -> Float,
    sectionHeaderHeightDp: Float
): List<WidgetTodoSection> {
    var remainingHeight = availableHeightDp.coerceAtLeast(0f)
    val visibleSections = mutableListOf<WidgetTodoSection>()

    for (section in sections) {
        val firstRowHeight = section.todos.firstOrNull()?.let(rowHeightDp) ?: break
        if (remainingHeight < sectionHeaderHeightDp + firstRowHeight) break

        remainingHeight -= sectionHeaderHeightDp
        val visibleTodos = section.todos.takeWhile { todo ->
            val height = rowHeightDp(todo)
            if (remainingHeight < height) {
                false
            } else {
                remainingHeight -= height
                true
            }
        }
        if (visibleTodos.isEmpty()) break
        visibleSections += section.copy(todos = visibleTodos)
        if (visibleTodos.size < section.todos.size) break
    }

    return visibleSections
}

internal fun TodoItem.widgetTime(): String {
    val start = startTime ?: return ""
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val startText = start.format(formatter)
    return endTime?.let { end -> "$startText - ${end.format(formatter)}" } ?: startText
}

internal fun TodoItem.widgetCompletionToken(): String =
    "$id:${completedDateTime?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).orEmpty()}"

internal fun widgetDateLabel(
    date: LocalDate,
    today: LocalDate,
    todayLabel: String,
    tomorrowLabel: String,
    dayAfterTomorrowLabel: String
): String {
    val shortDate = "${date.monthValue}/${date.dayOfMonth}"
    return when (date) {
        today -> "$todayLabel $shortDate"
        today.plusDays(1) -> "$tomorrowLabel $shortDate"
        today.plusDays(2) -> "$dayAfterTomorrowLabel $shortDate"
        else -> shortDate
    }
}

internal fun nextWidgetMidnight(now: ZonedDateTime): ZonedDateTime =
    now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
