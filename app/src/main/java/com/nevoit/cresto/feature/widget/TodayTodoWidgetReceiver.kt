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
        TodayTodoWidgetRefresh.request(context)
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
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        TodayTodoWidgetRefresh.request(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TodayTodoWidgetRefresh.ACTION_MIDNIGHT,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                TodayTodoWidgetRefresh.request(context)
                TodayTodoWidgetRefresh.scheduleMidnight(context)
            }
            else -> super.onReceive(context, intent)
        }
    }
}

