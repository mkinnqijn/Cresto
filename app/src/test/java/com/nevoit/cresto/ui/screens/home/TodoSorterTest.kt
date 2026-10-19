package com.nevoit.cresto.ui.screens.home

import com.nevoit.cresto.data.todo.TodoItem
import com.nevoit.cresto.data.todo.TodoItemWithSubTodos
import com.nevoit.cresto.feature.home.TodoListType
import com.nevoit.cresto.feature.home.buildHomeTodoSections
import com.nevoit.cresto.feature.home.sortTodos
import com.nevoit.cresto.feature.settings.util.SortOption
import com.nevoit.cresto.feature.settings.util.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.LocalTime

class TodoSorterTest {

    @Test
    fun homeSections_putPinnedTodosFirst_withoutDuplicatingThem() {
        val pinnedIncomplete = todo(
            id = 1,
            title = "pinned-incomplete",
            creationDateTime = dt("2026-07-01T08:00:00"),
            isPinned = true
        )
        val pinnedComplete = todo(
            id = 2,
            title = "pinned-complete",
            creationDateTime = dt("2026-07-02T08:00:00"),
            completedDateTime = dt("2026-07-03T08:00:00"),
            isCompleted = true,
            isPinned = true
        )
        val incomplete = todo(
            id = 3,
            title = "incomplete",
            creationDateTime = dt("2026-07-03T08:00:00")
        )
        val complete = todo(
            id = 4,
            title = "complete",
            creationDateTime = dt("2026-07-04T08:00:00"),
            completedDateTime = dt("2026-07-05T08:00:00"),
            isCompleted = true
        )

        val sections = buildHomeTodoSections(
            list = listOf(complete, incomplete, pinnedComplete, pinnedIncomplete),
            option = SortOption.DEFAULT,
            order = SortOrder.DESCENDING
        )

        assertEquals(listOf(1, 2), sections.pinned.map { it.todoItem.id })
        assertEquals(listOf(3), sections.incomplete.map { it.todoItem.id })
        assertEquals(listOf(4), sections.complete.map { it.todoItem.id })
    }

    @Test
    fun flagFallback_usesCreationTime_forIncompleteTodos() {
        val earlyCreated = todo(
            id = 1,
            title = "same-flag",
            flag = 3,
            creationDateTime = dt("2026-03-01T08:00:00"),
            completedDateTime = dt("2026-03-03T08:00:00")
        )
        val laterCreated = todo(
            id = 2,
            title = "same-flag",
            flag = 3,
            creationDateTime = dt("2026-03-02T08:00:00"),
            completedDateTime = dt("2026-03-01T08:00:00")
        )

        val sorted = sortTodos(
            list = listOf(earlyCreated, laterCreated),
            option = SortOption.FLAG,
            order = SortOrder.ASCENDING,
            type = TodoListType.INCOMPLETED
        )

        assertEquals(listOf(1, 2), sorted.map { it.todoItem.id })
    }

    @Test
    fun completedTodos_ignoreSelectedSortAndAlwaysUseNewestCompletionFirst() {
        val earlyCreatedLateCompleted = todo(
            id = 1,
            title = "same-flag",
            flag = 0,
            creationDateTime = dt("2026-03-01T08:00:00"),
            completedDateTime = dt("2026-03-03T08:00:00")
        )
        val laterCreatedEarlyCompleted = todo(
            id = 2,
            title = "same-flag",
            flag = 0,
            creationDateTime = dt("2026-03-02T08:00:00"),
            completedDateTime = dt("2026-03-01T08:00:00")
        )
        val missingCompletionTime = todo(
            id = 3,
            title = "missing-completion-time",
            creationDateTime = dt("2026-03-04T08:00:00")
        )

        SortOption.entries.forEach { option ->
            SortOrder.entries.forEach { order ->
                val sorted = sortTodos(
                    list = listOf(laterCreatedEarlyCompleted, missingCompletionTime, earlyCreatedLateCompleted),
                    option = option,
                    order = order,
                    type = TodoListType.COMPLETED
                )

                assertEquals(listOf(1, 2, 3), sorted.map { it.todoItem.id })
            }
        }
    }

    @Test
    fun titleFallback_usesCreationTime_forIncompleteTodos() {
        val first = todo(
            id = 10,
            title = "same-title",
            creationDateTime = dt("2026-01-10T10:00:00"),
            completedDateTime = dt("2026-01-12T10:00:00")
        )
        val second = todo(
            id = 11,
            title = "same-title",
            creationDateTime = dt("2026-01-11T10:00:00"),
            completedDateTime = dt("2026-01-09T10:00:00")
        )

        val sorted = sortTodos(
            list = listOf(second, first),
            option = SortOption.TITLE,
            order = SortOrder.ASCENDING,
            type = TodoListType.INCOMPLETED
        )

        assertEquals(listOf(10, 11), sorted.map { it.todoItem.id })
    }

    @Test
    fun completedTodos_keepStableOrderWhenCompletionTimesMatch() {
        val first = todo(
            id = 20,
            title = "same-title",
            creationDateTime = dt("2026-01-10T10:00:00"),
            completedDateTime = dt("2026-01-12T10:00:00")
        )
        val second = todo(
            id = 21,
            title = "same-title",
            creationDateTime = dt("2026-01-11T10:00:00"),
            completedDateTime = dt("2026-01-12T10:00:00")
        )

        val sorted = sortTodos(
            list = listOf(second, first),
            option = SortOption.TITLE,
            order = SortOrder.ASCENDING,
            type = TodoListType.COMPLETED
        )

        assertEquals(listOf(21, 20), sorted.map { it.todoItem.id })
    }

    @Test
    fun dueDateSort_putsUntimedFirstThenSortsStartTimeAscendingWithinTheDay() {
        val dueDate = LocalDate.of(2026, 10, 19)
        val untimed = todo(1, "untimed", creationDateTime = dt("2026-01-01T08:00:00"), dueDate = dueDate)
        val later = todo(2, "later", creationDateTime = dt("2026-01-02T08:00:00"), dueDate = dueDate, startTime = LocalTime.of(18, 0))
        val earlier = todo(3, "earlier", creationDateTime = dt("2026-01-03T08:00:00"), dueDate = dueDate, startTime = LocalTime.of(9, 0))

        assertEquals(
            listOf(1, 3, 2),
            sortTodos(
                listOf(later, untimed, earlier),
                SortOption.DUE_DATE,
                SortOrder.DESCENDING,
                TodoListType.INCOMPLETED
            ).map { it.todoItem.id }
        )
    }

    private fun todo(
        id: Int,
        title: String,
        flag: Int = 0,
        creationDateTime: LocalDateTime,
        completedDateTime: LocalDateTime? = null,
        isCompleted: Boolean = false,
        isPinned: Boolean = false,
        dueDate: LocalDate? = null,
        startTime: LocalTime? = null
    ): TodoItemWithSubTodos {
        return TodoItemWithSubTodos(
            todoItem = TodoItem(
                id = id,
                title = title,
                flag = flag,
                creationDateTime = creationDateTime,
                completedDateTime = completedDateTime,
                isCompleted = isCompleted,
                isPinned = isPinned,
                dueDate = dueDate,
                startTime = startTime
            ),
            subTodos = emptyList()
        )
    }

    private fun dt(value: String): LocalDateTime = LocalDateTime.parse(value)
}

