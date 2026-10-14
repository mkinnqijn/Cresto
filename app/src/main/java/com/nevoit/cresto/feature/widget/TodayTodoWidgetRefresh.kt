package com.nevoit.cresto.feature.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.room.InvalidationTracker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nevoit.cresto.data.todo.TodoDatabase
import java.time.ZonedDateTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import com.nevoit.cresto.data.todo.TodoRepository
import org.koin.core.context.GlobalContext

/** Event-driven updates shared by every mutation path, including imports and details. */
object TodayTodoWidgetRefresh {
    const val ACTION_MIDNIGHT = "com.nevoit.cresto.widget.MIDNIGHT"
    private const val WORK_NAME = "today-todo-widget-refresh"
    private const val COMPLETION_EXPIRY_WORK_NAME = "today-todo-widget-completion-expiry"
    private const val OVERDUE_TRANSITION_WORK_NAME = "today-todo-widget-overdue-transition"

    fun observe(context: Context, database: TodoDatabase) {
        val appContext = context.applicationContext
        database.invalidationTracker.addObserver(object : InvalidationTracker.Observer("todo_items") {
            override fun onInvalidated(tables: Set<String>) {
                request(appContext)
            }
        })
        request(appContext)
        scheduleMidnight(appContext)
    }

    fun hasWidgets(context: Context): Boolean = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, TodayTodoWidgetReceiver::class.java)).isNotEmpty()

    fun request(context: Context) {
        if (!hasWidgets(context)) return
        // Append instead of KEEP: a mutation during an active refresh must not be dropped.
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<TodayTodoWidgetRefreshWorker>().build()
        )
    }

    fun scheduleCompletionExpiry(context: Context, delayMillis: Long) {
        if (!hasWidgets(context) || delayMillis <= 0L) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            COMPLETION_EXPIRY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<TodayTodoWidgetRefreshWorker>()
                .setInitialDelay(delayMillis + 250L, TimeUnit.MILLISECONDS)
                .build()
        )
    }

    fun scheduleOverdueTransition(context: Context, delayMillis: Long?) {
        val workManager = WorkManager.getInstance(context)
        if (!hasWidgets(context) || delayMillis == null) {
            workManager.cancelUniqueWork(OVERDUE_TRANSITION_WORK_NAME)
            return
        }
        workManager.enqueueUniqueWork(
            OVERDUE_TRANSITION_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<TodayTodoWidgetRefreshWorker>()
                .setInitialDelay(delayMillis + 250L, TimeUnit.MILLISECONDS)
                .build()
        )
    }

    private fun midnightIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, TodayTodoWidgetReceiver::class.java).setAction(ACTION_MIDNIGHT),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun scheduleMidnight(context: Context) {
        if (!hasWidgets(context)) return
        val alarm = context.getSystemService(AlarmManager::class.java)
        // One non-wakeup, inexact alarm per local day: no exact-alarm permission or polling.
        // A sleeping device can deliver this after waking; system periodic updates are a fallback.
        alarm.setWindow(
            AlarmManager.RTC,
            nextWidgetMidnight(ZonedDateTime.now()).toInstant().toEpochMilli(),
            10 * 60 * 1000L,
            midnightIntent(context)
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(midnightIntent(context))
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(COMPLETION_EXPIRY_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(OVERDUE_TRANSITION_WORK_NAME)
    }
}

class TodayTodoWidgetRefreshWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val ids = GlanceAppWidgetManager(applicationContext)
            .getGlanceIds(TodayTodoWidget::class.java)
        ids.forEach { id ->
            updateAppWidgetState(applicationContext, id) { state ->
                state[WidgetRefreshKey] = (state[WidgetRefreshKey] ?: 0L) + 1L
            }
            TodayTodoWidget.update(applicationContext, id)
        }
        val repository = GlobalContext.get().get<TodoRepository>()
        val todos = repository.allTodos.first().map { it.todoItem }
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        nextWidgetCompletionExpiryDelayMillis(
            todos = todos,
            today = today,
            now = now
        )?.let { delay ->
            TodayTodoWidgetRefresh.scheduleCompletionExpiry(applicationContext, delay)
        }
        TodayTodoWidgetRefresh.scheduleOverdueTransition(
            applicationContext,
            nextWidgetOverdueTransitionDelayMillis(todos, today, now)
        )
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
