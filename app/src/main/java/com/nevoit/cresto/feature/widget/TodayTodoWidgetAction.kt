package com.nevoit.cresto.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.nevoit.cresto.data.todo.TodoRepository
import com.nevoit.cresto.data.todo.reminder.TodoAlarmScheduler
import java.time.LocalDateTime
import org.koin.core.context.GlobalContext

class ToggleWidgetTodoCompletionAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val todoId = parameters[WidgetTodoIdKey] ?: return
        val shouldComplete = parameters[WidgetTodoCompletedKey] ?: return
        val koin = GlobalContext.get()
        val repository = koin.get<TodoRepository>()
        val alarmScheduler = koin.get<TodoAlarmScheduler>()
        val todo = repository.getTodoByIdSnapshot(todoId) ?: return
        val now = LocalDateTime.now()

        if (!shouldComplete && todo.isCompleted &&
            todo.completedDateTime?.isAfter(now.minusMinutes(WIDGET_COMPLETION_UNDO_MINUTES)) != true
        ) {
            glanceId.recordWidgetTrigger(context, "completion_undo_expired")
            TodayTodoWidget.update(context, glanceId)
            return
        }

        if (todo.isCompleted != shouldComplete) {
            alarmScheduler.cancel(todo)
            val result = repository.updateCompletedStatusByIds(
                ids = listOf(todoId),
                isCompleted = shouldComplete,
                completedDateTime = if (shouldComplete) now else null
            )

            if (shouldComplete) {
                result.insertedTodos.forEach(alarmScheduler::schedule)
            } else {
                result.updatedTodos.forEach(alarmScheduler::schedule)
                alarmScheduler.cancelAll(result.deletedTodos.map { it.id })
            }
        }

        glanceId.recordWidgetTrigger(context, "completion_toggled")
        TodayTodoWidget.update(context, glanceId)
    }
}

class HideCompletedTodoFromWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val completionToken = parameters[WidgetTodoCompletionTokenKey] ?: return
        updateAppWidgetState(context, glanceId) { state ->
            state[WidgetHiddenCompletedKey] =
                state[WidgetHiddenCompletedKey].orEmpty() + completionToken
        }
        glanceId.recordWidgetTrigger(context, "completed_hidden")
        TodayTodoWidget.update(context, glanceId)
    }
}

private fun GlanceId.recordWidgetTrigger(context: Context, source: String) {
    (this as? AppWidgetId)?.appWidgetId?.let { appWidgetId ->
        TodayTodoWidgetLayoutState.recordTrigger(context, appWidgetId, source)
    }
}
