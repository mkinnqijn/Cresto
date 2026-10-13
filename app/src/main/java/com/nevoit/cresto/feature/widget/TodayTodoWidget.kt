package com.nevoit.cresto.feature.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity as actionStartWidgetActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.nevoit.cresto.MainActivity
import com.nevoit.cresto.R
import com.nevoit.cresto.data.todo.EXTRA_TODO_ID
import com.nevoit.cresto.data.todo.TodoItem
import com.nevoit.cresto.data.todo.TodoRepository
import com.nevoit.cresto.feature.detail.DetailActivity
import com.nevoit.cresto.feature.settings.util.SettingsManager
import com.nevoit.cresto.theme.resolveAppColors
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.glance.color.ColorProvider as DayNightColorProvider

const val EXTRA_WIDGET_ADD_TODO = "widget_add_todo"
internal val WidgetRefreshKey = longPreferencesKey("widget_refresh")
private val TodoIdKey = ActionParameters.Key<Int>(EXTRA_TODO_ID)

private data class TodayTodoWidgetColors(
    val background: ColorProvider,
    val content: ColorProvider,
    val secondary: ColorProvider,
    val primary: ColorProvider
)

/** One medium widget filling the launcher's allocated space, including 4 x 2 / 4 x 3. */
object TodayTodoWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = GlobalContext.get().get<TodoRepository>()
        val initialDate = LocalDate.now()
        val initialTodos = repository.getTodosByDate(initialDate).first()
        provideContent {
            // updateAll does not restart a live Glance session. The revision also switches
            // the date-bound Room query after midnight or a time-zone change.
            val revision = currentState(WidgetRefreshKey)
            val today = remember(revision) { LocalDate.now() }
            val query = remember(today) { repository.getTodosByDate(today) }
            val todos by query.collectAsState(
                initial = if (today == initialDate) initialTodos else emptyList()
            )
            TodayTodoWidgetContent(context, today, todayWidgetTodos(todos.map { it.todoItem }, today))
        }
    }
}

@Composable
private fun rememberTodayTodoWidgetColors(context: Context): TodayTodoWidgetColors {
    val dynamicColor = SettingsManager.isUseDynamicColorState.value
    val customPrimaryEnabled = SettingsManager.isCustomPrimaryColorEnabledState.value
    val themePrimaryColorArgb = SettingsManager.themePrimaryColorState.intValue

    return remember(context, dynamicColor, customPrimaryEnabled, themePrimaryColorArgb) {
        val lightColors = resolveAppColors(
            context = context,
            isDark = false,
            dynamicColor = dynamicColor,
            customPrimaryEnabled = customPrimaryEnabled,
            themePrimaryColorArgb = themePrimaryColorArgb
        )
        val darkColors = resolveAppColors(
            context = context,
            isDark = true,
            dynamicColor = dynamicColor,
            customPrimaryEnabled = customPrimaryEnabled,
            themePrimaryColorArgb = themePrimaryColorArgb
        )
        TodayTodoWidgetColors(
            background = DayNightColorProvider(
                day = lightColors.background,
                night = darkColors.background
            ),
            content = DayNightColorProvider(
                day = lightColors.content,
                night = darkColors.content
            ),
            secondary = DayNightColorProvider(
                day = lightColors.content.copy(alpha = 0.45f),
                night = darkColors.content.copy(alpha = 0.5f)
            ),
            primary = DayNightColorProvider(
                day = lightColors.primary,
                night = darkColors.primary
            )
        )
    }
}

@Composable
private fun TodayTodoWidgetContent(context: Context, today: LocalDate, todos: List<TodoItem>) {
    val colors = rememberTodayTodoWidgetColors(context)
    val locale = context.resources.configuration.locales[0]
    val date = today.format(DateTimeFormatter.ofPattern(
        DateFormat.getBestDateTimePattern(locale, "MMMd"), locale
    ))
    val count = context.resources.getQuantityString(R.plurals.today_widget_count, todos.size, todos.size)
    val addIntent = Intent(context, MainActivity::class.java)
        .putExtra(EXTRA_WIDGET_ADD_TODO, true)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    Column(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground()
            .background(colors.background).cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>()).padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Text(context.getString(R.string.today),
                    style = TextStyle(color = colors.content, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1)
                Spacer(GlanceModifier.height(2.dp))
                Text("$date · $count", style = TextStyle(color = colors.secondary, fontSize = 12.sp), maxLines = 1)
            }
            Box(GlanceModifier.size(48.dp).clickable(actionStartWidgetActivity(addIntent)),
                contentAlignment = Alignment.Center) {
                Image(ImageProvider(R.drawable.ic_add), context.getString(R.string.add_new_todo),
                    modifier = GlanceModifier.size(28.dp), colorFilter = ColorFilter.tint(colors.primary))
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        if (todos.isEmpty()) {
            Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.CenterStart) {
                Text(context.getString(R.string.today_widget_empty),
                    style = TextStyle(color = colors.secondary, fontSize = 14.sp))
            }
        } else {
            LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(todos, itemId = { it.id.toLong() }) { todo ->
                    TodayTodoRow(todo, colors)
                }
            }
        }
    }
}

@Composable
private fun TodayTodoRow(todo: TodoItem, colors: TodayTodoWidgetColors) {
    Row(
        modifier = GlanceModifier.fillMaxWidth()
            .clickable(actionStartActivity<DetailActivity>(actionParametersOf(TodoIdKey to todo.id)))
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Decorative only: the entire row opens details, with no completion action.
        Image(ImageProvider(R.drawable.ic_checkbox_circle_outline), null,
            modifier = GlanceModifier.padding(top = 2.dp).size(18.dp),
            colorFilter = ColorFilter.tint(colors.secondary))
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (todo.startTime != null) {
                    Text(todo.widgetTime(),
                        style = TextStyle(color = colors.secondary, fontSize = 13.sp), maxLines = 1)
                    Spacer(GlanceModifier.width(8.dp))
                }
                Text(todo.title, modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = colors.content, fontSize = 14.sp), maxLines = 1)
            }
            val location = todo.location.trim()
            if (location.isNotEmpty()) {
                Spacer(GlanceModifier.height(3.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(R.drawable.ic_location), null,
                        modifier = GlanceModifier.size(16.dp), colorFilter = ColorFilter.tint(colors.secondary))
                    Spacer(GlanceModifier.width(3.dp))
                    Text(location, modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = colors.secondary, fontSize = 12.sp), maxLines = 1)
                }
            }
        }
    }
}
