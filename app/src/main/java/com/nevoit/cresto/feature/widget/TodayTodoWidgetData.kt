package com.nevoit.cresto.feature.widget

import com.nevoit.cresto.data.todo.TodoItem
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Widget ordering is chronological, independent of the app's user-selected sort or pins. */
internal fun todayWidgetTodos(todos: List<TodoItem>, today: LocalDate): List<TodoItem> =
    todos.filter { it.dueDate == today && !it.isCompleted && it.deletedAt == null }
        .sortedWith(
            compareBy<TodoItem> { it.startTime == null }
                .thenBy { it.startTime }
                .thenBy { it.creationDateTime }
                .thenBy { it.id }
        )

internal fun TodoItem.widgetTime(): String =
    startTime?.format(DateTimeFormatter.ofPattern("HH:mm")).orEmpty()

internal fun nextWidgetMidnight(now: ZonedDateTime): ZonedDateTime =
    now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
