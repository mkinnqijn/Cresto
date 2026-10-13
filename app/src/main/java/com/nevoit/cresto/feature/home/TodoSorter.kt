package com.nevoit.cresto.feature.home

import com.nevoit.cresto.data.todo.TodoItem
import com.nevoit.cresto.data.todo.TodoItemWithSubTodos
import com.nevoit.cresto.feature.settings.util.SortOption
import com.nevoit.cresto.feature.settings.util.SortOrder
import java.text.Collator
import java.time.LocalTime

enum class TodoListType {
    INCOMPLETED,
    COMPLETED
}

data class HomeTodoSections(
    val pinned: List<TodoItemWithSubTodos>,
    val incomplete: List<TodoItemWithSubTodos>,
    val complete: List<TodoItemWithSubTodos>
)

fun buildHomeTodoSections(
    list: List<TodoItemWithSubTodos>,
    option: SortOption,
    order: SortOrder
): HomeTodoSections {
    val pinned = sortTodos(
        list = list.filter { it.todoItem.isPinned && !it.todoItem.isCompleted },
        option = option,
        order = order,
        type = TodoListType.INCOMPLETED
    ) + sortTodos(
        list = list.filter { it.todoItem.isPinned && it.todoItem.isCompleted },
        option = option,
        order = order,
        type = TodoListType.COMPLETED
    )
    val incomplete = sortTodos(
        list = list.filter { !it.todoItem.isPinned && !it.todoItem.isCompleted },
        option = option,
        order = order,
        type = TodoListType.INCOMPLETED
    )
    val complete = sortTodos(
        list = list.filter { !it.todoItem.isPinned && it.todoItem.isCompleted },
        option = option,
        order = order,
        type = TodoListType.COMPLETED
    )
    return HomeTodoSections(
        pinned = pinned,
        incomplete = incomplete,
        complete = complete
    )
}

fun sortTodos(
    list: List<TodoItemWithSubTodos>,
    option: SortOption,
    order: SortOrder,
    type: TodoListType
): List<TodoItemWithSubTodos> {
    val currentSortOption =
        SortOption.entries.getOrElse(option.ordinal) { SortOption.DEFAULT }
    val currentSortOrder =
        SortOrder.entries.getOrElse(order.ordinal) { SortOrder.DESCENDING }

    val collator = Collator.getInstance()
    val comparator = Comparator<TodoItemWithSubTodos> { a, b ->
        val itemA = a.todoItem
        val itemB = b.todoItem

        val baseDateA = when (type) {
            TodoListType.INCOMPLETED -> itemA.creationDateTime
            TodoListType.COMPLETED -> itemA.completedDateTime
        }
        val baseDateB = when (type) {
            TodoListType.INCOMPLETED -> itemB.creationDateTime
            TodoListType.COMPLETED -> itemB.completedDateTime
        }

        val compareByBaseDateTime = when {
            baseDateA == null && baseDateB == null -> 0
            baseDateA == null -> 1
            baseDateB == null -> -1
            currentSortOrder == SortOrder.ASCENDING -> baseDateA.compareTo(baseDateB)
            else -> baseDateB.compareTo(baseDateA)
        }

        val compareResult = when (currentSortOption) {
            SortOption.DEFAULT -> {
                compareByBaseDateTime
            }

            SortOption.DUE_TIME -> {
                val dateA = itemA.dueDate
                val dateB = itemB.dueDate

                if (dateA == null && dateB == null) {
                    compareByBaseDateTime
                } else if (dateA == null) {
                    1
                } else if (dateB == null) {
                    -1
                } else {
                    val dueDateCompare = if (currentSortOrder == SortOrder.ASCENDING) {
                        dateA.compareTo(dateB)
                    } else {
                        dateB.compareTo(dateA)
                    }
                    if (dueDateCompare != 0) {
                        dueDateCompare
                    } else {
                        // 同一天内按具体时间排（全天任务视为 00:00，升序时排在最前）
                        val dueTimeCompare = if (currentSortOrder == SortOrder.ASCENDING) {
                            itemA.sortTime().compareTo(itemB.sortTime())
                        } else {
                            itemB.sortTime().compareTo(itemA.sortTime())
                        }
                        if (dueTimeCompare != 0) dueTimeCompare else compareByBaseDateTime
                    }
                }
            }

            SortOption.FLAG -> {
                val flagA = itemA.flag
                val flagB = itemB.flag
                val isAEmpty = flagA == 0
                val isBEmpty = flagB == 0

                if (isAEmpty && isBEmpty) {
                    compareByBaseDateTime
                } else if (isAEmpty) {
                    1
                } else if (isBEmpty) {
                    -1
                } else {
                    val flagCompare = if (currentSortOrder == SortOrder.ASCENDING) {
                        flagB.compareTo(flagA)
                    } else {
                        flagA.compareTo(flagB)
                    }
                    if (flagCompare != 0) flagCompare else compareByBaseDateTime
                }
            }

            SortOption.TITLE -> {
                val titleCompare = if (currentSortOrder == SortOrder.ASCENDING) {
                    collator.compare(itemA.title, itemB.title)
                } else {
                    collator.compare(itemB.title, itemA.title)
                }
                if (titleCompare != 0) titleCompare else compareByBaseDateTime
            }
        }

        if (compareResult != 0) {
            compareResult
        } else {
            if (currentSortOrder == SortOrder.ASCENDING) {
                itemA.id.compareTo(itemB.id)
            } else {
                itemB.id.compareTo(itemA.id)
            }
        }
    }
    return list.sortedWith(comparator)
}

/**
 * 排序用的“当天时间”：优先开始时间，其次结束时间；两者都没有（全天任务）按 00:00 处理，
 * 这样同一天内会按具体时间先后排列，全天任务排在当天最前面。
 */
private fun TodoItem.sortTime(): LocalTime = startTime ?: endTime ?: LocalTime.MIN
