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
import java.time.LocalDate
import java.time.LocalDateTime
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
    fun flagFallback_usesCompletedTime_forCompletedTodos() {
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

        val sorted = sortTodos(
            list = listOf(earlyCreatedLateCompleted, laterCreatedEarlyCompleted),
            option = SortOption.FLAG,
            order = SortOrder.ASCENDING,
            type = TodoListType.COMPLETED
        )

        assertEquals(listOf(2, 1), sorted.map { it.todoItem.id })
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
    fun titleFallback_usesCompletedTime_forCompletedTodos() {
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
            completedDateTime = dt("2026-01-09T10:00:00")
        )

        val sorted = sortTodos(
            list = listOf(first, second),
            option = SortOption.TITLE,
            order = SortOrder.ASCENDING,
            type = TodoListType.COMPLETED
        )

        assertEquals(listOf(21, 20), sorted.map { it.todoItem.id })
    }

    @Test
    fun dueTime_sortsByTimeWithinTheSameDay() {
        // 同一天的三条：09:00 < 15:25 < 20:30；创建时间刻意打乱，证明不再退化成按创建时间排
        val evening = todo(
            id = 1,
            title = "evening",
            creationDateTime = dt("2026-10-01T12:00:00"),
            dueDate = LocalDate.parse("2026-10-16"),
            startTime = LocalTime.parse("20:30")
        )
        val morning = todo(
            id = 2,
            title = "morning",
            creationDateTime = dt("2026-10-01T23:00:00"),
            dueDate = LocalDate.parse("2026-10-16"),
            startTime = LocalTime.parse("09:00")
        )
        val afternoon = todo(
            id = 3,
            title = "afternoon",
            creationDateTime = dt("2026-10-01T01:00:00"),
            dueDate = LocalDate.parse("2026-10-16"),
            startTime = LocalTime.parse("15:25")
        )

        val ascending = sortTodos(
            list = listOf(evening, morning, afternoon),
            option = SortOption.DUE_TIME,
            order = SortOrder.ASCENDING,
            type = TodoListType.INCOMPLETED
        )
        assertEquals(listOf(2, 3, 1), ascending.map { it.todoItem.id })

        val descending = sortTodos(
            list = listOf(morning, afternoon, evening),
            option = SortOption.DUE_TIME,
            order = SortOrder.DESCENDING,
            type = TodoListType.INCOMPLETED
        )
        assertEquals(listOf(1, 3, 2), descending.map { it.todoItem.id })
    }

    @Test
    fun dueTime_treatsAllDayTodosAsStartOfDay_andFallsBackToEndTime() {
        val allDay = todo(
            id = 1,
            title = "all-day",
            creationDateTime = dt("2026-10-01T08:00:00"),
            dueDate = LocalDate.parse("2026-10-16")
        )
        val endTimeOnly = todo(
            id = 2,
            title = "end-time-only",
            creationDateTime = dt("2026-10-01T08:00:00"),
            dueDate = LocalDate.parse("2026-10-16"),
            endTime = LocalTime.parse("18:30")
        )
        val timed = todo(
            id = 3,
            title = "timed",
            creationDateTime = dt("2026-10-01T08:00:00"),
            dueDate = LocalDate.parse("2026-10-16"),
            startTime = LocalTime.parse("20:30")
        )

        val sorted = sortTodos(
            list = listOf(timed, endTimeOnly, allDay),
            option = SortOption.DUE_TIME,
            order = SortOrder.ASCENDING,
            type = TodoListType.INCOMPLETED
        )

        // 全天（00:00）→ 只有结束时间（18:30）→ 有开始时间（20:30）
        assertEquals(listOf(1, 2, 3), sorted.map { it.todoItem.id })
    }

    @Test
    fun dueTime_sortsDatesBeforeTimes_andKeepsUndatedTodosLast() {
        val undated = todo(
            id = 1,
            title = "undated",
            creationDateTime = dt("2026-10-01T08:00:00")
        )
        val laterDayEarlyTime = todo(
            id = 2,
            title = "later-day",
            creationDateTime = dt("2026-10-01T08:00:00"),
            dueDate = LocalDate.parse("2026-10-18"),
            startTime = LocalTime.parse("09:00")
        )
        val earlierDayLateTime = todo(
            id = 3,
            title = "earlier-day",
            creationDateTime = dt("2026-10-01T08:00:00"),
            dueDate = LocalDate.parse("2026-10-16"),
            startTime = LocalTime.parse("20:30")
        )

        val sorted = sortTodos(
            list = listOf(undated, laterDayEarlyTime, earlierDayLateTime),
            option = SortOption.DUE_TIME,
            order = SortOrder.ASCENDING,
            type = TodoListType.INCOMPLETED
        )

        // 日期优先（10/16 在 10/18 前），没有截止日期的排最后
        assertEquals(listOf(3, 2, 1), sorted.map { it.todoItem.id })
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
        startTime: LocalTime? = null,
        endTime: LocalTime? = null
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
                startTime = startTime,
                endTime = endTime
            ),
            subTodos = emptyList()
        )
    }

    private fun dt(value: String): LocalDateTime = LocalDateTime.parse(value)
}

