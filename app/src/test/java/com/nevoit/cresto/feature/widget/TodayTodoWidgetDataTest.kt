package com.nevoit.cresto.feature.widget

import android.content.res.Configuration
import com.nevoit.cresto.data.todo.TodoItem
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
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

    @Test fun `date then untimed items then start time wins over pins`() {
        val input = listOf(todo(1).copy(isPinned = true),
            todo(2, "18:30").copy(endTime = LocalTime.of(20, 30)),
            todo(3, "15:30").copy(isPinned = true), todo(4, "09:00"),
            todo(5).copy(endTime = LocalTime.of(8, 0)), todo(6, "23:59"))
        assertEquals(
            listOf(1, 5, 4, 3, 2, 6),
            upcomingWidgetSections(input, today, today.atStartOfDay())
                .flatMap { section -> section.todos }
                .map { it.id }
        )
    }

    @Test fun `multi-day todo appears once under its start date`() {
        val item = todo(1, "09:00").copy(endDate = today.plusDays(2))
        val sections = upcomingWidgetSections(listOf(item), today, today.atStartOfDay())

        assertEquals(listOf(today), sections.map { it.date })
        assertEquals(listOf(1), sections.flatMap { it.todos }.map { it.id })
        assertEquals("10/13 - 10/15", item.widgetDateRange())
    }

    @Test fun `ongoing multi-day todo appears once under today`() {
        val item = todo(1, "09:00").copy(
            dueDate = today.minusDays(2),
            endDate = today.plusDays(1)
        )

        assertEquals(
            listOf(today),
            upcomingWidgetSections(listOf(item), today, today.atTime(20, 0)).map { it.date }
        )
        assertEquals(false, item.isWidgetOverdue(today, LocalTime.of(20, 0)))
    }

    @Test fun `future multi-day todo appears once under its start date`() {
        val item = todo(1).copy(
            dueDate = today.plusDays(2),
            endDate = today.plusDays(5)
        )

        val sections = upcomingWidgetSections(listOf(item), today, today.atStartOfDay())

        assertEquals(listOf(today.plusDays(2)), sections.map { it.date })
        assertEquals(listOf(1), sections.flatMap { it.todos }.map { it.id })
        assertEquals("10/15 - 10/18", item.widgetDateRange())
    }

    @Test fun `single-day date grouping remains unchanged beside a multi-day item`() {
        val multiDay = todo(1).copy(endDate = today.plusDays(3))
        val tomorrow = todo(2).copy(dueDate = today.plusDays(1))
        val later = todo(3).copy(dueDate = today.plusDays(3))

        val sections = upcomingWidgetSections(
            listOf(multiDay, tomorrow, later),
            today,
            today.atStartOfDay()
        )

        assertEquals(listOf(today, today.plusDays(1), today.plusDays(3)), sections.map { it.date })
        assertEquals(listOf(1, 2, 3), sections.flatMap { it.todos }.map { it.id })
    }

    @Test fun `ties use creation time then id even if Room emits a different order`() {
        val input = listOf(todo(3, "09:00"), todo(2, "09:00"),
            todo(1, "09:00").copy(creationDateTime = today.atTime(1, 0)))
        val expected = listOf(2, 3, 1)
        assertEquals(expected, upcomingWidgetSections(input, today, today.atStartOfDay()).single().todos.map { it.id })
        assertEquals(expected, upcomingWidgetSections(input.reversed(), today, today.atStartOfDay()).single().todos.map { it.id })
    }

    @Test fun `time formatting hides untimed and shows complete ranges`() {
        assertEquals("09:00", todo(1, "09:00").widgetTime())
        assertEquals("18:30 - 20:30", todo(2, "18:30").copy(endTime = LocalTime.of(20, 30)).widgetTime())
        assertEquals("", todo(3).widgetTime())
        assertEquals("", todo(4).copy(endTime = LocalTime.NOON).widgetTime())
    }

    @Test fun `all upcoming items are grouped by date with each dates full count`() {
        val input = listOf(
            todo(1),
            todo(2).copy(dueDate = today.plusDays(1)),
            todo(3).copy(dueDate = today.plusDays(1)),
            todo(4).copy(dueDate = today.plusDays(2))
        )
        val sections = upcomingWidgetSections(input, today)
        assertEquals(listOf(today, today.plusDays(1), today.plusDays(2)), sections.map { it.date })
        assertEquals(listOf(1, 2, 1), sections.map { it.totalCount })
        assertEquals(listOf(1, 2, 3, 4), sections.flatMap { it.todos }.map { it.id })
    }

    @Test fun `same-day section keeps every upcoming item`() {
        val sections = upcomingWidgetSections(listOf(todo(1), todo(2), todo(3)), today)
        assertEquals(3, sections.single().totalCount)
        assertEquals(listOf(1, 2, 3), sections.single().todos.map { it.id })
    }

    @Test fun `recently completed todo remains for undo without increasing pending count`() {
        val now = LocalDateTime.of(2026, 10, 13, 12, 0)
        val sections = upcomingWidgetSections(
            listOf(
                todo(1),
                todo(2).copy(isCompleted = true, completedDateTime = now.minusMinutes(2)),
                todo(3).copy(isCompleted = true, completedDateTime = now.minusMinutes(3))
            ),
            today,
            now
        )

        assertEquals(listOf(1, 2), sections.single().todos.map { it.id })
        assertEquals(1, sections.single().totalCount)
        assertEquals(
            60_000L,
            nextWidgetCompletionExpiryDelayMillis(
                sections.single().todos,
                today,
                now
            )
        )
    }

    @Test fun `widget hidden completion token only removes that completed occurrence`() {
        val now = LocalDateTime.of(2026, 10, 13, 12, 0)
        val completed = todo(2).copy(
            isCompleted = true,
            completedDateTime = now.minusMinutes(1)
        )

        val sections = upcomingWidgetSections(
            todos = listOf(todo(1), completed),
            today = today,
            now = now,
            hiddenCompletedTokens = setOf(completed.widgetCompletionToken())
        )

        assertEquals(listOf(1), sections.single().todos.map { it.id })
        assertEquals(1, sections.single().totalCount)
    }

    @Test fun `height fitting only emits dates with at least one complete todo row`() {
        val sections = upcomingWidgetSections(
            listOf(
                todo(1),
                todo(2),
                todo(3).copy(dueDate = today.plusDays(1)),
                todo(4).copy(dueDate = today.plusDays(1))
            ),
            today
        )

        val short = fitWidgetSections(
            sections, 101f, fullRowHeightDp = { 40f }, compactRowHeightDp = { 40f },
            sectionHeaderHeightDp = 20f
        )
        val tall = fitWidgetSections(
            sections, 205f, fullRowHeightDp = { 40f }, compactRowHeightDp = { 40f },
            sectionHeaderHeightDp = 20f
        )

        assertEquals(listOf(1, 2), short.flatMap { it.todos }.map { it.id })
        assertEquals(listOf(1, 2, 3, 4), tall.flatMap { it.todos }.map { it.id })
        assertEquals(listOf(2, 2), tall.map { it.totalCount })
        assertEquals(true, tall.all { it.todos.isNotEmpty() })
    }

    @Test fun `height fitting accounts for different todo row heights`() {
        val sections = upcomingWidgetSections(listOf(todo(1), todo(2), todo(3)), today)
        val visible = fitWidgetSections(
            sections = sections,
            availableHeightDp = 135f,
            fullRowHeightDp = { todo -> if (todo.id == 2) 70f else 40f },
            compactRowHeightDp = { todo -> if (todo.id == 2) 70f else 40f },
            sectionHeaderHeightDp = 20f
        )

        assertEquals(listOf(1, 2), visible.single().todos.map { it.id })
    }

    @Test fun `height fitting uses compact final row instead of leaving usable space blank`() {
        val sections = upcomingWidgetSections(listOf(todo(1), todo(2)), today)
        val visible = fitWidgetSections(
            sections = sections,
            availableHeightDp = 121f,
            fullRowHeightDp = { todo -> if (todo.id == 2) 90f else 60f },
            compactRowHeightDp = { 40f },
            sectionHeaderHeightDp = 20f
        )

        assertEquals(listOf(1, 2), visible.single().todos.map { it.id })
        assertEquals(setOf(2), visible.single().compactTodoIds)
    }

    @Test fun `height fitting includes next date when its header and compact first row fit`() {
        val sections = upcomingWidgetSections(
            listOf(todo(1), todo(2).copy(dueDate = today.plusDays(1))),
            today
        )
        val visible = fitWidgetSections(
            sections = sections,
            availableHeightDp = 113f,
            fullRowHeightDp = { todo -> if (todo.id == 2) 80f else 40f },
            compactRowHeightDp = { 30f },
            sectionHeaderHeightDp = 20f
        )

        assertEquals(listOf(1, 2), visible.flatMap { it.todos }.map { it.id })
        assertEquals(setOf(2), visible.last().compactTodoIds)
    }

    @Test fun `height fitting never leaves a date header without a minimum row`() {
        val sections = upcomingWidgetSections(
            listOf(todo(1), todo(2).copy(dueDate = today.plusDays(1))),
            today
        )
        val visible = fitWidgetSections(
            sections = sections,
            availableHeightDp = 112f,
            fullRowHeightDp = { todo -> if (todo.id == 2) 80f else 40f },
            compactRowHeightDp = { 30f },
            sectionHeaderHeightDp = 20f
        )

        assertEquals(listOf(1), visible.flatMap { it.todos }.map { it.id })
        assertEquals(1, visible.size)
    }

    @Test fun `same height and data always make the same later date decision`() {
        val laterDate = today.plusDays(5)
        val sections = upcomingWidgetSections(
            listOf(todo(1), todo(2).copy(dueDate = laterDate)),
            today
        )

        val runs = List(10) {
            val traces = mutableListOf<WidgetSectionFitTrace>()
            val visible = fitWidgetSections(
                sections = sections,
                availableHeightDp = 113f,
                fullRowHeightDp = { todo -> if (todo.id == 2) 80f else 40f },
                compactRowHeightDp = { 30f },
                sectionHeaderHeightDp = 20f,
                onSectionDecision = traces::add
            )
            visible to traces
        }

        assertEquals(1, runs.distinct().size)
        val (visible, traces) = runs.first()
        assertEquals(listOf(1, 2), visible.flatMap { it.todos }.map { it.id })
        assertEquals(WidgetFirstRowFit.COMPACT, traces.single { it.date == laterDate }.firstRowFit)
        assertEquals(53f, traces.single { it.date == laterDate }.remainingHeightDp)
    }

    @Test fun `launcher options select a stable size for each orientation`() {
        val options = WidgetLauncherOptions(
            minWidthDp = 250,
            maxWidthDp = 430,
            minHeightDp = 140,
            maxHeightDp = 510
        )

        assertEquals(250, options.widthForOrientation(Configuration.ORIENTATION_PORTRAIT))
        assertEquals(510, options.heightForOrientation(Configuration.ORIENTATION_PORTRAIT))
        assertEquals(430, options.widthForOrientation(Configuration.ORIENTATION_LANDSCAPE))
        assertEquals(140, options.heightForOrientation(Configuration.ORIENTATION_LANDSCAPE))
    }

    @Test fun `crossing midnight drops the previous date`() {
        val input = listOf(todo(1), todo(2).copy(dueDate = today.plusDays(1)))
        assertEquals(
            listOf(2),
            upcomingWidgetSections(input, today.plusDays(1)).flatMap { it.todos }.map { it.id }
        )
    }

    @Test fun `date labels distinguish today tomorrow day after tomorrow and later dates`() {
        assertEquals("Today 10/13", widgetDateLabel(today, today, "Today", "Tomorrow", "Day after tomorrow"))
        assertEquals("Tomorrow 10/14", widgetDateLabel(today.plusDays(1), today, "Today", "Tomorrow", "Day after tomorrow"))
        assertEquals("Day after tomorrow 10/15", widgetDateLabel(today.plusDays(2), today, "Today", "Tomorrow", "Day after tomorrow"))
        assertEquals("10/16", widgetDateLabel(today.plusDays(3), today, "Today", "Tomorrow", "Day after tomorrow"))
    }

    @Test fun `overdue predicate matches app date and time rules`() {
        val noon = LocalTime.NOON
        assertEquals(true, todo(1).copy(dueDate = today.minusDays(1)).isWidgetOverdue(today, noon))
        assertEquals(true, todo(2, "11:00").isWidgetOverdue(today, noon))
        assertEquals(false, todo(3, "11:00").copy(endTime = noon).isWidgetOverdue(today, noon))
        assertEquals(true, todo(3, "11:00").copy(endTime = LocalTime.of(11, 59)).isWidgetOverdue(today, noon))
        assertEquals(false, todo(4).isWidgetOverdue(today, noon))
        assertEquals(false, todo(5).copy(dueDate = today.plusDays(1)).isWidgetOverdue(today, noon))
        assertEquals(false, todo(6, "11:00").copy(isCompleted = true).isWidgetOverdue(today, noon))
        assertEquals(false, todo(7, "11:00").copy(deletedAt = today.atStartOfDay()).isWidgetOverdue(today, noon))
    }

    @Test fun `overdue section merges dates limits rows and puts newest expiry first`() {
        val now = today.atTime(15, 0)
        val section = requireNotNull(overdueWidgetSection(
            todos = listOf(
                todo(1).copy(dueDate = today.minusDays(1), startTime = LocalTime.of(23, 0)),
                todo(2).copy(dueDate = today.minusDays(1)),
                todo(3, "14:00"),
                todo(4, "12:00").copy(endTime = LocalTime.of(16, 0)),
                todo(5).copy(dueDate = today.plusDays(1))
            ),
            today = today,
            now = now,
            limit = 2
        ))
        assertEquals(true, section.isOverdue)
        assertEquals(3, section.totalCount)
        assertEquals(listOf(3, 2), section.todos.map { it.id })
    }

    @Test fun `recently completed overdue todo remains for undo and can be hidden`() {
        val now = today.atTime(15, 0)
        val pending = todo(1, "14:00")
        val completed = todo(2).copy(
            dueDate = today.minusDays(1),
            isCompleted = true,
            completedDateTime = now.minusMinutes(2)
        )
        val section = requireNotNull(overdueWidgetSection(
            listOf(pending, completed), today, now, 8
        ))
        assertEquals(1, section.totalCount)
        assertEquals(listOf(1, 2), section.todos.map { it.id })
        val hidden = requireNotNull(overdueWidgetSection(
            listOf(pending, completed), today, now, 8,
            setOf(completed.widgetCompletionToken())
        ))
        assertEquals(listOf(1), hidden.todos.map { it.id })
    }

    @Test fun `next overdue transition uses end time when present`() {
        val now = today.atTime(15, 0)
        val delay = nextWidgetOverdueTransitionDelayMillis(
            listOf(
                todo(1, "14:00").copy(endTime = LocalTime.of(15, 30)),
                todo(2, "16:00"),
                todo(3)
            ),
            today,
            now
        )
        assertEquals(30 * 60 * 1000L, delay)
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
