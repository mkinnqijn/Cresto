package com.nevoit.cresto.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle

internal data class WidgetLauncherOptions(
    val minWidthDp: Int,
    val maxWidthDp: Int,
    val minHeightDp: Int,
    val maxHeightDp: Int
) {
    fun widthForOrientation(orientation: Int): Int = when (orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> maxWidthDp.takeIf { it > 0 } ?: minWidthDp
        else -> minWidthDp.takeIf { it > 0 } ?: maxWidthDp
    }

    fun heightForOrientation(orientation: Int): Int = when (orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> minHeightDp.takeIf { it > 0 } ?: maxHeightDp
        else -> maxHeightDp.takeIf { it > 0 } ?: minHeightDp
    }
}

/** Keeps launcher dimensions stable between data-only updates of the same widget instance. */
internal object TodayTodoWidgetLayoutState {
    private const val PREFS_NAME = "today_todo_widget_layout"

    fun recordOptions(
        context: Context,
        appWidgetId: Int,
        options: Bundle,
        triggerSource: String
    ) {
        val snapshot = options.toLauncherOptions()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(key(appWidgetId, "min_width"), snapshot.minWidthDp)
            .putInt(key(appWidgetId, "max_width"), snapshot.maxWidthDp)
            .putInt(key(appWidgetId, "min_height"), snapshot.minHeightDp)
            .putInt(key(appWidgetId, "max_height"), snapshot.maxHeightDp)
            .putString(key(appWidgetId, "source"), triggerSource)
            .apply()
    }

    fun recordCurrentOptions(context: Context, appWidgetId: Int, triggerSource: String) {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        recordOptions(context, appWidgetId, options, triggerSource)
    }

    fun recordTrigger(context: Context, appWidgetId: Int, triggerSource: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(key(appWidgetId, "source"), triggerSource)
            .apply()
    }

    fun readOptions(context: Context, appWidgetId: Int): WidgetLauncherOptions? {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val minHeightKey = key(appWidgetId, "min_height")
        if (!preferences.contains(minHeightKey)) return null
        return WidgetLauncherOptions(
            minWidthDp = preferences.getInt(key(appWidgetId, "min_width"), 0),
            maxWidthDp = preferences.getInt(key(appWidgetId, "max_width"), 0),
            minHeightDp = preferences.getInt(minHeightKey, 0),
            maxHeightDp = preferences.getInt(key(appWidgetId, "max_height"), 0)
        )
    }

    fun readTrigger(context: Context, appWidgetId: Int): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key(appWidgetId, "source"), null)
            ?: "unknown"

    fun remove(context: Context, appWidgetId: Int) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        listOf("min_width", "max_width", "min_height", "max_height", "source")
            .forEach { suffix -> editor.remove(key(appWidgetId, suffix)) }
        editor.apply()
    }

    private fun key(appWidgetId: Int, suffix: String): String = "${appWidgetId}_$suffix"
}

internal fun Bundle.toLauncherOptions(): WidgetLauncherOptions = WidgetLauncherOptions(
    minWidthDp = getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
    maxWidthDp = getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
    minHeightDp = getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
    maxHeightDp = getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
)
