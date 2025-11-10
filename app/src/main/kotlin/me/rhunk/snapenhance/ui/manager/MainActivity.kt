@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
package me.rhunk.snapenhance.ui.manager

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.background
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.SharedContextHolder
import me.rhunk.snapenhance.common.ui.AppMaterialTheme
import me.rhunk.snapenhance.common.ui.ThemeMode
import me.rhunk.snapenhance.common.ui.ThemePreferences
import me.rhunk.snapenhance.ui.util.ActivityLauncherHelper
import android.content.IntentFilter

class MainActivity : ComponentActivity() {
    private lateinit var navController: NavHostController
    private lateinit var managerContext: RemoteSideContext

    companion object {
        const val RESTART_ACTION = "me.rhunk.snapenhance.RESTART"
    }

    private val restartReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            if (intent.action == RESTART_ACTION) {
                recreate()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::navController.isInitialized.not()) return
        intent.getStringExtra("route")?.let { route ->
            navController.popBackStack()
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = true
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        super.onCreate(savedInstanceState)
        registerReceiver(restartReceiver, IntentFilter(RESTART_ACTION), RECEIVER_EXPORTED)
        managerContext = SharedContextHolder.remote(this).apply {
            activity = this@MainActivity
            checkForRequirements()
        }
        val routes = Routes(managerContext)
        routes.activityLauncher = ActivityLauncherHelper(this)
        routes.getRoutes().forEach { it.init() }

        setContent {
            val context = LocalContext.current
            // ThemeMode is tracked directly
            val themeMode by ThemePreferences.getThemeModeFlow(context).collectAsState(initial = ThemeMode.SYSTEM)
            // USE THE CORRECT CONTROLLER (not accompanist):
            navController = rememberNavController()
            val navigation = remember {
                Navigation(managerContext, navController, routes.also {
                    it.navController = navController
                })
            }
            val startDestination = remember {
                intent.getStringExtra("route") ?: run {
                    val def = managerContext.sharedPreferences.getString("manager_default_tab", "home") ?: "home"
                    val allowed = setOf("tasks", "features", "home", "social", "scripts")
                    if (def in allowed) def else "home"
                }
            }
            AppMaterialTheme(themeMode = themeMode) {
                val background = MaterialTheme.colorScheme.background
                val isLight = background.luminance() > 0.5f
                val view = LocalView.current
                @Suppress("DEPRECATION")
                SideEffect {
                    val window = (view.context as Activity).window
                    // Modern coloring:
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    window.statusBarColor = Color.Transparent.toArgb()
                    window.navigationBarColor = Color.Transparent.toArgb()
                    val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = isLight
                    insetsController.isAppearanceLightNavigationBars = isLight
                }
                // Floating bottom bar height and vertical spacing so floating action buttons and scrolling content remain readable:
                val bottomPadding = 80.dp + 16.dp +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                routes.bottomPadding = bottomPadding
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val fullscreenRoutes = remember {
                    listOf(
                        Routes.CONFIG_IMPORT_CONFIRMATION_ROUTE,
                        Routes.CONFIG_EXPORT_SUMMARY_ROUTE,
                        Routes.FRIEND_TRACKER_CONFIG_EXPORT_ROUTE,
                        Routes.FRIEND_TRACKER_CONFIG_IMPORT_ROUTE
                    )
                }
                val isFullscreen = currentRoute in fullscreenRoutes
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        if (!isFullscreen) {
                            navigation.TopBar()
                        }
                    },
                    floatingActionButton = {
                        if (!isFullscreen) {
                            Box(Modifier.padding(bottom = bottomPadding)) {
                                navigation.Fab()
                            }
                        }
                    },
                    // Disable automatic padding so content can draw behind the bottom bar
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    Box(Modifier.fillMaxSize()) {
                        val contentPadding = if (!isFullscreen) {
                            PaddingValues(
                                top = innerPadding.calculateTopPadding(),
                                start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                                end = innerPadding.calculateEndPadding(LayoutDirection.Ltr),
                                bottom = innerPadding.calculateBottomPadding()
                            )
                        } else {
                            PaddingValues(0.dp)
                        }
                        navigation.NavContent(contentPadding, startDestination)
                        if (!isFullscreen) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(routes.bottomPadding)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.background
                                            )
                                        )
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                            ) {
                                navigation.FloatingBottomBar()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(restartReceiver)
    }
}
