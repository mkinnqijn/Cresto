package com.nevoit.cresto.feature.widget

import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class TodayTodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTodoWidget

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        TodayTodoWidgetRefresh.scheduleMidnight(context)
        TodayTodoWidgetRefresh.request(context, "receiver_enabled")
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            if (TodayTodoWidgetLayoutState.readOptions(context, appWidgetId) == null) {
                TodayTodoWidgetLayoutState.recordCurrentOptions(
                    context,
                    appWidgetId,
                    "receiver_update_initial_size"
                )
            } else {
                TodayTodoWidgetLayoutState.recordTrigger(context, appWidgetId, "receiver_update")
            }
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        TodayTodoWidgetRefresh.cancel(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        TodayTodoWidgetLayoutState.recordOptions(
            context,
            appWidgetId,
            newOptions,
            "receiver_options_changed"
        )
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        TodayTodoWidgetRefresh.request(context, "receiver_options_changed")
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { TodayTodoWidgetLayoutState.remove(context, it) }
        super.onDeleted(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TodayTodoWidgetRefresh.ACTION_MIDNIGHT,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                TodayTodoWidgetRefresh.request(context, "receiver_${intent.action}")
                TodayTodoWidgetRefresh.scheduleMidnight(context)
            }
            else -> super.onReceive(context, intent)
        }
    }
}

