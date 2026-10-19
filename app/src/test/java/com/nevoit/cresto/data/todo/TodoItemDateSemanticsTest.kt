package com.nevoit.cresto.data.todo

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoItemDateSemanticsTest {
    private val today = LocalDate.of(2026, 10, 19)

    @Test
    fun multiDayItemIsActiveAndNotOverdueBetweenStartAndEnd() {
        val item = TodoItem(
            title = "Range",
            dueDate = today,
            endDate = today.plusDays(5),
            startTime = LocalTime.of(9, 0)
        )

        assertTrue(item.isActiveOn(today))
        assertTrue(item.isActiveOn(today.plusDays(3)))
        assertFalse(item.isOverdueAt(today, LocalTime.of(20, 0)))
        assertFalse(item.isOverdueAt(today.plusDays(5), LocalTime.of(8, 59)))
        assertTrue(item.isOverdueAt(today.plusDays(5), LocalTime.of(9, 1)))
    }

    @Test
    fun normalTodoKeepsSingleDayDeadlineSemantics() {
        val item = TodoItem(
            title = "Single day",
            dueDate = today,
            startTime = LocalTime.of(9, 0)
        )

        assertTrue(item.isActiveOn(today))
        assertTrue(item.isOverdueAt(today, LocalTime.of(9, 1)))
    }
}
