package com.nevoit.cresto.feature.widget

import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
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
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity as actionStartWidgetActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
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
private const val ACTION_WIDGET_ADD_TODO = "com.nevoit.cresto.action.WIDGET_ADD_TODO"
internal val WidgetRefreshKey = longPreferencesKey("widget_refresh")
internal val WidgetTodoIdKey = ActionParameters.Key<Int>(EXTRA_TODO_ID)
internal val WidgetTodoCompletedKey = ActionParameters.Key<Boolean>("widget_todo_completed")

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
        val optionHeight = (id as? AppWidgetId)?.let { appWidgetId ->
            val options = AppWidgetManager.getInstance(context)
                .getAppWidgetOptions(appWidgetId.appWidgetId)
            maxOf(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            ).takeIf { it > 0 }?.dp
        }
        provideContent {
            // The revision makes the date window advance after midnight or a time-zone change.
            val revision = currentState(WidgetRefreshKey)
            val today = remember(revision) { LocalDate.now() }
            val todos by repository.allTodos.collectAsState(initial = initialTodos)
            TodayTodoWidgetContent(
                context = context,
                today = today,
                sections = upcomingWidgetSections(todos.map { it.todoItem }, today),
                optionHeight = optionHeight
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
    sections: List<WidgetTodoSection>,
    optionHeight: Dp?
) {
    val colors = rememberTodayTodoWidgetColors(context)
    val localHeight = LocalSize.current.height
    val widgetHeight = optionHeight?.takeIf { it > localHeight } ?: localHeight
    val expanded = widgetHeight >= 320.dp
    val spacious = widgetHeight >= 180.dp
    val visibleSections = fitWidgetSections(
        sections = sections,
        availableHeightDp = (widgetHeight - when {
            expanded -> 76.dp
            spacious -> 65.dp
            else -> 59.dp
        }).value,
        rowHeightDp = when {
            expanded -> 47f
            spacious -> 39f
            else -> 34f
        },
        sectionHeaderHeightDp = when {
            expanded -> 20f
            spacious -> 17f
            else -> 15f
        }
    )
    val addIntent = Intent(context, MainActivity::class.java)
        .setAction(ACTION_WIDGET_ADD_TODO)
        .putExtra(EXTRA_WIDGET_ADD_TODO, true)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    Column(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground()
            .background(colors.background).cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(
                horizontal = if (expanded) 20.dp else 16.dp,
                vertical = when {
                    expanded -> 16.dp
                    spacious -> 12.dp
                    else -> 10.dp
                }
            )
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = context.getString(R.string.todos),
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = colors.content,
                    fontSize = if (expanded) 22.sp else 19.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Box(GlanceModifier.size(if (expanded) 40.dp else 36.dp).clickable(actionStartWidgetActivity(addIntent)),
                contentAlignment = Alignment.Center) {
                Image(ImageProvider(R.drawable.ic_add), context.getString(R.string.add_new_todo),
                    modifier = GlanceModifier.size(if (expanded) 26.dp else 24.dp),
                    colorFilter = ColorFilter.tint(colors.primary))
            }
        }
        Spacer(GlanceModifier.height(if (expanded) 7.dp else if (spacious) 5.dp else 2.dp))
        if (visibleSections.isEmpty()) {
            Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.CenterStart) {
                Text(context.getString(R.string.upcoming_widget_empty),
                    style = TextStyle(color = colors.secondary, fontSize = 14.sp))
            }
        } else {
            Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                visibleSections.forEachIndexed { sectionIndex, section ->
                    Column(GlanceModifier.fillMaxWidth()) {
                        if (sectionIndex > 0) {
                            WidgetDivider(colors = colors)
                            Spacer(GlanceModifier.height(2.dp))
                        }
                        WidgetDateHeader(
                            context = context,
                            today = today,
                            section = section,
                            colors = colors,
                            expanded = expanded
                        )
                        // RemoteViews limits the number of direct children in a container.
                        // Chunk rows into nested columns so a tall widget can keep growing.
                        section.todos.chunked(8).forEachIndexed { chunkIndex, chunk ->
                            Column(GlanceModifier.fillMaxWidth()) {
                                chunk.forEachIndexed { todoIndex, todo ->
                                    Column(GlanceModifier.fillMaxWidth()) {
                                        if (chunkIndex > 0 || todoIndex > 0) {
                                            WidgetDivider(colors = colors, startPadding = 26.dp)
                                        }
                                        TodayTodoRow(
                                            context = context,
                                            todo = todo,
                                            colors = colors,
                                            spacious = spacious,
                                            expanded = expanded
                                        )
                                    }
                                }
                            }
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
    colors: TodayTodoWidgetColors,
    expanded: Boolean
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
            style = TextStyle(
                color = colors.secondary,
                fontSize = if (expanded) 14.sp else 12.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
        Text(
            text = count,
            style = TextStyle(color = colors.secondary, fontSize = if (expanded) 14.sp else 12.sp),
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
private fun TodayTodoRow(
    context: Context,
    todo: TodoItem,
    colors: TodayTodoWidgetColors,
    spacious: Boolean,
    expanded: Boolean
) {
    val location = todo.location.trim()
    val hasTime = todo.startTime != null
    val hasLocation = location.isNotEmpty()
    Row(
        modifier = GlanceModifier.fillMaxWidth()
            .clickable(actionStartActivity<DetailActivity>(actionParametersOf(WidgetTodoIdKey to todo.id)))
            .padding(vertical = if (expanded) 8.dp else if (spacious) 6.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(if (expanded) 30.dp else 26.dp)
                .clickable(
                    actionRunCallback<ToggleWidgetTodoCompletionAction>(
                        actionParametersOf(
                            WidgetTodoIdKey to todo.id,
                            WidgetTodoCompletedKey to !todo.isCompleted
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(
                    if (todo.isCompleted) R.drawable.ic_checkmark_circle
                    else R.drawable.ic_checkbox_circle_outline
                ),
                contentDescription = context.getString(
                    if (todo.isCompleted) R.string.restore else R.string.done
                ),
                modifier = GlanceModifier.size(if (expanded) 20.dp else 17.dp),
                colorFilter = ColorFilter.tint(
                    if (todo.isCompleted) colors.primary else colors.secondary
                )
            )
        }
        Spacer(GlanceModifier.width(if (expanded) 11.dp else 9.dp))
        Text(
            text = todo.title,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(
                color = if (todo.isCompleted) colors.secondary else colors.content,
                fontSize = if (expanded) 16.sp else 14.sp,
                textDecoration = if (todo.isCompleted) TextDecoration.LineThrough
                else TextDecoration.None
            ),
            maxLines = if (spacious) 2 else 1
        )
        if (hasTime || hasLocation) {
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.width(if (expanded) 124.dp else if (spacious) 104.dp else 88.dp)) {
                if (hasTime) {
                    WidgetMetadata(
                        icon = R.drawable.ic_clock,
                        text = todo.widgetTime(),
                        colors = colors,
                        expanded = expanded
                    )
                }
                if (hasTime && hasLocation) {
                    Spacer(GlanceModifier.height(2.dp))
                }
                if (hasLocation) {
                    WidgetMetadata(
                        icon = R.drawable.ic_location,
                        text = location,
                        colors = colors,
                        expanded = expanded
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetMetadata(
    icon: Int,
    text: String,
    colors: TodayTodoWidgetColors,
    expanded: Boolean
) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            modifier = GlanceModifier.size(if (expanded) 17.dp else 15.dp),
            colorFilter = ColorFilter.tint(colors.secondary)
        )
        Spacer(GlanceModifier.width(3.dp))
        Text(
            text = text,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = colors.secondary, fontSize = if (expanded) 14.sp else 12.sp),
            maxLines = 1
        )
    }
}
