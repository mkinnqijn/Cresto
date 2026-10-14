package com.nevoit.cresto.feature.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
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
import androidx.glance.color.ColorProvider as DayNightColorProvider

const val EXTRA_WIDGET_ADD_TODO = "widget_add_todo"
internal val WidgetRefreshKey = longPreferencesKey("widget_refresh")
private val TodoIdKey = ActionParameters.Key<Int>(EXTRA_TODO_ID)

private data class TodayTodoWidgetColors(
    val background: ColorProvider,
    val content: ColorProvider,
    val secondary: ColorProvider,
    val divider: ColorProvider,
    val primary: ColorProvider
)

/** One medium widget filling the launcher's allocated space, including 4 x 2 / 4 x 3. */
object TodayTodoWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = GlobalContext.get().get<TodoRepository>()
        val initialTodos = repository.allTodos.first()
        provideContent {
            // The revision makes the date window advance after midnight or a time-zone change.
            val revision = currentState(WidgetRefreshKey)
            val today = remember(revision) { LocalDate.now() }
            val todos by repository.allTodos.collectAsState(initial = initialTodos)
            TodayTodoWidgetContent(
                context = context,
                today = today,
                sections = upcomingWidgetSections(todos.map { it.todoItem }, today)
            )
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
            divider = DayNightColorProvider(
                day = lightColors.content.copy(alpha = 0.08f),
                night = darkColors.content.copy(alpha = 0.12f)
            ),
            primary = DayNightColorProvider(
                day = lightColors.primary,
                night = darkColors.primary
            )
        )
    }
}

@Composable
private fun TodayTodoWidgetContent(
    context: Context,
    today: LocalDate,
    sections: List<WidgetTodoSection>
) {
    val colors = rememberTodayTodoWidgetColors(context)
    val spacious = LocalSize.current.height >= 180.dp
    val addIntent = Intent(context, MainActivity::class.java)
        .putExtra(EXTRA_WIDGET_ADD_TODO, true)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    Column(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground()
            .background(colors.background).cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 16.dp, vertical = if (spacious) 12.dp else 10.dp)
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = context.getString(R.string.todos),
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = colors.content,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Box(GlanceModifier.size(36.dp).clickable(actionStartWidgetActivity(addIntent)),
                contentAlignment = Alignment.Center) {
                Image(ImageProvider(R.drawable.ic_add), context.getString(R.string.add_new_todo),
                    modifier = GlanceModifier.size(24.dp), colorFilter = ColorFilter.tint(colors.primary))
            }
        }
        Spacer(GlanceModifier.height(if (spacious) 5.dp else 2.dp))
        if (sections.isEmpty()) {
            Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.CenterStart) {
                Text(context.getString(R.string.upcoming_widget_empty),
                    style = TextStyle(color = colors.secondary, fontSize = 14.sp))
            }
        } else {
            Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                sections.forEachIndexed { sectionIndex, section ->
                    if (sectionIndex > 0) {
                        WidgetDivider(colors = colors)
                        Spacer(GlanceModifier.height(2.dp))
                    }
                    WidgetDateHeader(
                        context = context,
                        today = today,
                        section = section,
                        colors = colors
                    )
                    section.todos.forEachIndexed { todoIndex, todo ->
                        TodayTodoRow(todo = todo, colors = colors, spacious = spacious)
                        if (todoIndex < section.todos.lastIndex) {
                            WidgetDivider(colors = colors, startPadding = 26.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetDateHeader(
    context: Context,
    today: LocalDate,
    section: WidgetTodoSection,
    colors: TodayTodoWidgetColors
) {
    val label = widgetDateLabel(
        date = section.date,
        today = today,
        todayLabel = context.getString(R.string.today),
        tomorrowLabel = context.getString(R.string.tomorrow)
    )
    val count = context.resources.getQuantityString(
        R.plurals.widget_date_total,
        section.totalCount,
        section.totalCount
    )
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = colors.secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            maxLines = 1
        )
        Text(
            text = count,
            style = TextStyle(color = colors.secondary, fontSize = 12.sp),
            maxLines = 1
        )
    }
}

@Composable
private fun WidgetDivider(colors: TodayTodoWidgetColors, startPadding: Dp = 0.dp) {
    Box(
        modifier = GlanceModifier.fillMaxWidth()
            .padding(start = startPadding)
            .height(1.dp)
            .background(colors.divider)
    ) {}
}

@Composable
private fun TodayTodoRow(todo: TodoItem, colors: TodayTodoWidgetColors, spacious: Boolean) {
    val location = todo.location.trim()
    val hasTime = todo.startTime != null
    val hasLocation = location.isNotEmpty()
    Row(
        modifier = GlanceModifier.fillMaxWidth()
            .clickable(actionStartActivity<DetailActivity>(actionParametersOf(TodoIdKey to todo.id)))
            .padding(vertical = if (spacious) 6.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Decorative only: the entire row opens details, with no completion action.
        Image(ImageProvider(R.drawable.ic_checkbox_circle_outline), null,
            modifier = GlanceModifier.size(17.dp),
            colorFilter = ColorFilter.tint(colors.secondary))
        Spacer(GlanceModifier.width(9.dp))
        Text(
            text = todo.title,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = colors.content, fontSize = 14.sp),
            maxLines = if (spacious) 2 else 1
        )
        if (hasTime || hasLocation) {
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.width(if (spacious) 104.dp else 88.dp)) {
                if (hasTime) {
                    WidgetMetadata(
                        icon = R.drawable.ic_clock,
                        text = todo.widgetTime(),
                        colors = colors
                    )
                }
                if (hasTime && hasLocation) {
                    Spacer(GlanceModifier.height(2.dp))
                }
                if (hasLocation) {
                    WidgetMetadata(
                        icon = R.drawable.ic_location,
                        text = location,
                        colors = colors
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetMetadata(icon: Int, text: String, colors: TodayTodoWidgetColors) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            modifier = GlanceModifier.size(15.dp),
            colorFilter = ColorFilter.tint(colors.secondary)
        )
        Spacer(GlanceModifier.width(3.dp))
        Text(
            text = text,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = colors.secondary, fontSize = 12.sp),
            maxLines = 1
        )
    }
}
