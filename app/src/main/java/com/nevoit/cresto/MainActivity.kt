package com.nevoit.cresto

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nevoit.cresto.feature.guide.GuideActivity
import com.nevoit.cresto.feature.main.MainScreen
import com.nevoit.cresto.feature.screenextract.ScreenExtractEvents
import com.nevoit.cresto.feature.settings.util.SettingsManager
import com.nevoit.cresto.feature.widget.EXTRA_WIDGET_ADD_TODO
import com.nevoit.cresto.feature.widget.TodayTodoWidgetRefresh
import com.nevoit.cresto.theme.GlasenseTheme
import com.nevoit.cresto.util.NotificationPermissionCompat
import com.nevoit.glasense.core.utility.clearBackground

private const val REQUEST_POST_NOTIFICATIONS = 1001

/**
 * The main activity of the application.
 */
class MainActivity : AppCompatActivity() {
    private var widgetAddRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This makes the app display behind the system bars.

        requestNotificationPermissionIfNeeded()
        handleScreenExtractErrorIntent(intent)
        handleWidgetIntent(intent)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        val isFirstRun = SettingsManager.isFirstRun

        if (isFirstRun) {
            startActivity(Intent(this, GuideActivity::class.java))
            finish()
            return
        }

        setContent {
            GlasenseTheme {
                MainScreen(
                    widgetAddRequested = widgetAddRequested,
                    onWidgetAddHandled = { widgetAddRequested = false }
                )
            }
        }
        clearBackground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleScreenExtractErrorIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_WIDGET_ADD_TODO, false) == true) {
            widgetAddRequested = true
            intent.removeExtra(EXTRA_WIDGET_ADD_TODO)
        }
    }

    private fun handleScreenExtractErrorIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(ScreenExtractEvents.EXTRA_SHOW_ERROR_DIALOG, false) != true) {
            return
        }
        val message = intent.getStringExtra(ScreenExtractEvents.EXTRA_ERROR_MESSAGE)
            ?: getString(R.string.extract_screen_failed)
        ScreenExtractEvents.emitError(message)
        intent.removeExtra(ScreenExtractEvents.EXTRA_SHOW_ERROR_DIALOG)
        intent.removeExtra(ScreenExtractEvents.EXTRA_ERROR_MESSAGE)
    }

    override fun onStop() {
        super.onStop()
        TodayTodoWidgetRefresh.request(applicationContext)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (!NotificationPermissionCompat.shouldRequestPostNotificationsPermission()) {
            return
        }

        if (NotificationPermissionCompat.hasPostNotificationsPermission(this)) {
            return
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(NotificationPermissionCompat.POST_NOTIFICATIONS),
            REQUEST_POST_NOTIFICATIONS
        )
    }
}
