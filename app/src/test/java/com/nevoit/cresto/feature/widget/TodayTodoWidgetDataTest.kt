package com.nevoit.cresto.feature.widget

import com.nevoit.cresto.data.todo.TodoItem
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayTodoWidgetDataTest {
    private val today = LocalDate.of(2026, 10, 13)
    private fun todo(id: Int, time: String? = null) = TodoItem(
        id = id, title = "Task $id", dueDate = today,
        creationDateTime = today.atStartOfDay(), startTime = time?.let(LocalTime::parse)
    )

    @Test fun `only dated incomplete non-deleted todos from today onward are included`() {
        val input = listOf(todo(1), todo(2).copy(isCompleted = true),
            todo(3).copy(deletedAt = today.atStartOfDay()),
            todo(4).copy(dueDate = today.minusDays(1)),
            todo(5).copy(dueDate = today.plusDays(1)), todo(6).copy(dueDate = null))
        assertEquals(
            listOf(1, 5),
            upcomingWidgetSections(input, today).flatMap { section -> section.todos }.map { it.id }
        )
    }

    @Test fun `date then start time wins over pins and untimed items come last that day`() {
        val input = listOf(todo(1).copy(isPinned = true),
            todo(2, "18:30").copy(endTime = LocalTime.of(20, 30)),
            todo(3, "15:30").copy(isPinned = true), todo(4, "09:00"),
            todo(5).copy(endTime = LocalTime.of(8, 0)), todo(6, "23:59"))
        assertEquals(
            listOf(4, 3, 2, 6, 1, 5),
            upcomingWidgetSections(input, today, limit = 10)
                .flatMap { section -> section.todos }
                .map { it.id }
        )
    }

    @Test fun `ties use creation time then id even if Room emits a different order`() {
        val input = listOf(todo(3, "09:00"), todo(2, "09:00"),
            todo(1, "09:00").copy(creationDateTime = today.atTime(1, 0)))
        val expected = listOf(2, 3, 1)
        assertEquals(expected, upcomingWidgetSections(input, today, limit = 10).single().todos.map { it.id })
        assertEquals(expected, upcomingWidgetSections(input.reversed(), today, limit = 10).single().todos.map { it.id })
    }

    @Test fun `time formatting hides untimed and uses only range start`() {
        assertEquals("09:00", todo(1, "09:00").widgetTime())
        assertEquals("18:30", todo(2, "18:30").copy(endTime = LocalTime.of(20, 30)).widgetTime())
        assertEquals("", todo(3).widgetTime())
        assertEquals("", todo(4).copy(endTime = LocalTime.NOON).widgetTime())
    }

    @Test fun `two visible items can span dates and sections keep each dates full count`() {
        val input = listOf(
            todo(1),
            todo(2).copy(dueDate = today.plusDays(1)),
            todo(3).copy(dueDate = today.plusDays(1)),
            todo(4).copy(dueDate = today.plusDays(2))
        )
        val sections = upcomingWidgetSections(input, today)
        assertEquals(listOf(today, today.plusDays(1)), sections.map { it.date })
        assertEquals(listOf(1, 2), sections.map { it.totalCount })
        assertEquals(listOf(1, 2), sections.flatMap { it.todos }.map { it.id })
    }

    @Test fun `two item limit does not reduce a same-day total count`() {
        val sections = upcomingWidgetSections(listOf(todo(1), todo(2), todo(3)), today)
        assertEquals(3, sections.single().totalCount)
        assertEquals(listOf(1, 2), sections.single().todos.map { it.id })
    }

    @Test fun `crossing midnight drops the previous date`() {
        val input = listOf(todo(1), todo(2).copy(dueDate = today.plusDays(1)))
        assertEquals(
            listOf(2),
            upcomingWidgetSections(input, today.plusDays(1)).flatMap { it.todos }.map { it.id }
        )
    }

    @Test fun `date labels distinguish today tomorrow and later dates`() {
        assertEquals("Today 10/13", widgetDateLabel(today, today, "Today", "Tomorrow"))
        assertEquals("Tomorrow 10/14", widgetDateLabel(today.plusDays(1), today, "Today", "Tomorrow"))
        assertEquals("10/15", widgetDateLabel(today.plusDays(2), today, "Today", "Tomorrow"))
    }

    @Test fun `midnight is local next day including daylight saving changes`() {
        val zone = ZoneId.of("America/New_York")
        val spring = ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, zone)
        val fall = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, zone)
        assertEquals(23L, Duration.between(spring, nextWidgetMidnight(spring)).toHours())
        assertEquals(25L, Duration.between(fall, nextWidgetMidnight(fall)).toHours())
        assertEquals(LocalTime.MIDNIGHT, nextWidgetMidnight(spring).toLocalTime())
    }
}
