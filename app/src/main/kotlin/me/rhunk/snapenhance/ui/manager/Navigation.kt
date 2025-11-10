package me.rhunk.snapenhance.ui.manager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import me.rhunk.snapenhance.RemoteSideContext
import kotlin.math.round
import kotlin.math.PI
import kotlin.math.sin

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
    androidx.compose.animation.ExperimentalAnimationApi::class
)
class Navigation(
    private val context: RemoteSideContext,
    private val navController: NavHostController,
    val routes: Routes = Routes(context).also { it.navController = navController }
) {
    private val translation by lazy { context.translation.getCategory("manager.navigation") }
    var openBottomBarCustomization by mutableStateOf(false)
    @Composable
    fun TopBar() {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = remember(navBackStackEntry) { routes.getCurrentRoute(navBackStackEntry) }
        if (currentRoute?.routeInfo?.hasOwnTopBar == true) return
        val canGoBack = remember(navBackStackEntry) {
            currentRoute?.let { !it.routeInfo.primary || it.routeInfo.childIds.contains(routes.currentDestination) } == true
        }
        TopAppBar(
            title = {
                currentRoute?.apply {
                    title?.invoke() ?: routeInfo.translatedKey?.value?.let {
                        Text(
                            text = it,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            navigationIcon = {
                val backButtonAnimation by animateFloatAsState(if (canGoBack) 1f else 0f, label = "backButton")
                Box(
                    modifier = Modifier
                        .graphicsLayer { alpha = backButtonAnimation }
                        .width(lerp(0.dp, 48.dp, backButtonAnimation))
                        .height(48.dp)
                ) {
                    IconButton(onClick = { if (canGoBack) navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            },
            actions = {
                currentRoute?.topBarActions?.invoke(this)
                if (currentRoute?.routeInfo?.id == routes.settings.routeInfo.id) {
                    IconButton(onClick = { openBottomBarCustomization = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = null)
                    }
                }
            }
        )
    }
    @Composable
    fun FloatingBottomBar() {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = remember(navBackStackEntry) { routes.getCurrentRoute(navBackStackEntry) }
        val availableRoutes = remember {
            listOf(routes.tasks, routes.features, routes.home, routes.social, routes.scripting, routes.friendTracker)
        }
        val availableRouteMap = remember(availableRoutes) { availableRoutes.associateBy { it.routeInfo.id } }
        val prefs = remember { context.sharedPreferences }
        val defaultOrder = remember { listOf("tasks", "features", "home", "social", "scripts") }
        fun loadSelected(): List<String> {
            val raw = prefs.getString("manager_nav_tabs", null)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            val cleaned = raw.filter { availableRouteMap.containsKey(it) }
            val list = (if (cleaned.isNotEmpty()) cleaned else defaultOrder).distinct()
            return list.take(5)
        }
        var defaultTabId by remember { mutableStateOf(prefs.getString("manager_default_tab", "home") ?: "home") }
        fun saveDefault(id: String) {
            defaultTabId = id
            prefs.edit().putString("manager_default_tab", id).apply()
        }
        fun saveSelected(ids: List<String>) {
            if (defaultTabId !in ids) {
                val candidate = when {
                    "home" in ids -> "home"
                    ids.isNotEmpty() -> ids.first()
                    else -> defaultTabId
                }
                saveDefault(candidate)
            }
            prefs.edit().putString("manager_nav_tabs", ids.joinToString(",")).apply()
        }
        var selectedTabIds by remember { mutableStateOf(loadSelected()) }
        val selectedRoutes = remember(selectedTabIds) { selectedTabIds.mapNotNull { availableRouteMap[it] } }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            val baseItemWidth = 92.dp
            val containerPadding = 24.dp
            val targetBarWidth = if (selectedRoutes.size < 5) baseItemWidth * selectedRoutes.size.toFloat() + containerPadding else null
            val animatedBarWidth by animateDpAsState(targetValue = targetBarWidth ?: 0.dp, label = "barWidth")
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                modifier = Modifier
                    .then(if (targetBarWidth != null) Modifier.width(animatedBarWidth) else Modifier.fillMaxWidth())
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(24.dp),
                        spotColor = MaterialTheme.colorScheme.primary,
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
            ) {
                Box(Modifier.fillMaxWidth().height(80.dp)) {
                    var barWidthPx by remember { mutableStateOf(0f) }
                    val itemCount = selectedRoutes.size.coerceAtLeast(1)
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val selectedIndex = remember(currentRoute, selectedRoutes) {
                        val index = selectedRoutes.indexOf(currentRoute)
                        if (index >= 0) index else null // indexOf returns -1 when not found, replace with null
                    }

                    selectedIndex?.let { // Null check
                        val itemWidthPx =
                            remember(barWidthPx, itemCount) { if (itemCount > 0) barWidthPx / itemCount else 0f }
                        val offsetAnim = remember { Animatable(0f) }
                        var lastSelectedIndex by remember { mutableStateOf(selectedIndex) }
                        LaunchedEffect(itemWidthPx) {
                            if (itemWidthPx > 0f) {
                                offsetAnim.snapTo(selectedIndex * itemWidthPx)
                            }
                        }
                        LaunchedEffect(selectedIndex, itemWidthPx) {
                            if (itemWidthPx <= 0f) return@LaunchedEffect
                            val dist = kotlin.math.abs(selectedIndex - lastSelectedIndex).coerceAtLeast(1)
                            val damping = when {
                                dist >= 3 -> 0.65f
                                dist == 2 -> 0.75f
                                else -> 0.90f
                            }
                            val stiffness = Spring.StiffnessMediumLow
                            offsetAnim.animateTo(
                                targetValue = selectedIndex * itemWidthPx,
                                animationSpec = spring(dampingRatio = damping, stiffness = stiffness)
                            )
                            lastSelectedIndex = selectedIndex
                        }
                        val horizontalInset = 8.dp
                        val indicatorWidth = with(density) { itemWidthPx.toDp() } - horizontalInset * 2
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { barWidthPx = it.size.width.toFloat() }
                        ) {
                            val motionProgress = remember { Animatable(1f) }
                            LaunchedEffect(selectedIndex) {
                                motionProgress.snapTo(0f)
                                val dist = kotlin.math.abs(selectedIndex - lastSelectedIndex).coerceAtLeast(1)
                                val dur = when {
                                    dist >= 3 -> 440
                                    dist == 2 -> 380
                                    else -> 320
                                }
                                motionProgress.animateTo(
                                    1f,
                                    animationSpec = tween(durationMillis = dur, easing = FastOutSlowInEasing)
                                )
                            }
                            val pulse = sin(PI * motionProgress.value).toFloat()
                            val distForScale = kotlin.math.abs(selectedIndex - lastSelectedIndex).coerceAtLeast(1)
                            val scaleXBase = 0.18f
                            val scaleXExtra = 0.06f
                            val scaleYBase = 0.06f
                            val scaleYExtra = 0.02f
                            val mult = (distForScale - 1).coerceAtLeast(0)
                            val scaleXAnim = 1f + (scaleXBase + scaleXExtra * mult) * pulse
                            val scaleYAnim = 1f - (scaleYBase + scaleYExtra * mult) * pulse
                            if (barWidthPx > 0f && itemCount > 0) {
                                val offsetX = with(density) { offsetAnim.value.toDp() } + horizontalInset
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(indicatorWidth.coerceAtLeast(0.dp))
                                        .offset(x = offsetX)
                                        .padding(vertical = 8.dp)
                                        .graphicsLayer { scaleX = scaleXAnim; scaleY = scaleYAnim }
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                                        .border(
                                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)),
                                            RoundedCornerShape(14.dp)
                                        )
                                )
                            }
                        }
                    }
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        selectedRoutes.forEach { route ->
                            NavigationBarItem(
                                alwaysShowLabel = true,
                                icon = { Icon(imageVector = route.routeInfo.icon, contentDescription = null) },
                                label = {
                                    val label = context.translation["manager.routes.${route.routeInfo.key.substringBefore("/")}"]
                                    val isLong = label.length > 11
                                    Text(
                                        text = label,
                                        textAlign = TextAlign.Center,
                                        fontSize = 12.sp,
                                        maxLines = if (isLong) 2 else 1,
                                        overflow = if (isLong) TextOverflow.Ellipsis else TextOverflow.Clip,
                                        softWrap = isLong,
                                        modifier = if (isLong) Modifier.widthIn(max = 80.dp).wrapContentWidth(Alignment.CenterHorizontally) else Modifier.wrapContentWidth(Alignment.CenterHorizontally)
                                    )
                                },
                                selected = currentRoute == route,
                                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
                                onClick = { route.navigateReset() }
                            )
                        }
                    }
                }
            }
            if (openBottomBarCustomization) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(onDismissRequest = { openBottomBarCustomization = false }, sheetState = sheetState) {
                    Column(Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(112.dp)
                                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                        )
                                    )
                                )
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                Text(text = translation["customize_bottom_bar_title"], style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(4.dp))
                                Text(text = translation["customize_bottom_bar_subtitle"], style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 8.dp)
                                    .width(36.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(text = translation["shown_tabs_title"], style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(Modifier.height(8.dp))
                        if (selectedTabIds.isEmpty()) {
                            Text(text = translation["no_tabs_selected_text"], style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val haptic = LocalHapticFeedback.current
                            var draggingId by remember { mutableStateOf<String?>(null) }
                            var dragDelta by remember { mutableStateOf(0f) }
                            var dragStartIndex by remember { mutableStateOf(-1) }
                            var rowHeight by remember { mutableStateOf(0) }
                            val listState = rememberLazyListState()
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                contentPadding = PaddingValues(bottom = 8.dp),
                                state = listState
                            ) {
                                itemsIndexed(selectedTabIds, key = { _, id -> id }) { index, id ->
                                    val route = availableRouteMap[id] ?: return@itemsIndexed
                                    val label = context.translation["manager.routes.${route.routeInfo.key.substringBefore("/")}"]
                                    val isDragging = draggingId == id
                                    ElevatedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp)
                                            .animateItem()
                                            .zIndex(if (isDragging) 1f else 0f)
                                            .graphicsLayer { if (isDragging) { scaleX = 1.02f; scaleY = 1.02f } }
                                            .onGloballyPositioned { if (rowHeight == 0) rowHeight = it.size.height }
                                            .pointerInput(id) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        draggingId = id
                                                        dragStartIndex = selectedTabIds.indexOf(id)
                                                        dragDelta = 0f
                                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                    },
                                                    onDrag = { _: PointerInputChange, dragAmount ->
                                                        dragDelta += dragAmount.y
                                                        if (rowHeight > 0 && dragStartIndex >= 0) {
                                                            val currentIndex = selectedTabIds.indexOf(id)
                                                            val deltaRows = round(dragDelta / rowHeight.toFloat()).toInt()
                                                            val targetIndex = (dragStartIndex + deltaRows).coerceIn(0, selectedTabIds.lastIndex)
                                                            if (targetIndex != currentIndex) {
                                                                val list = selectedTabIds.toMutableList()
                                                                list.removeAt(currentIndex)
                                                                list.add(targetIndex, id)
                                                                selectedTabIds = list
                                                                saveSelected(selectedTabIds)
                                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                            }
                                                        }
                                                    },
                                                    onDragEnd = { draggingId = null; dragDelta = 0f; dragStartIndex = -1 },
                                                    onDragCancel = { draggingId = null; dragDelta = 0f; dragStartIndex = -1 }
                                                )
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(10.dp)
                                                .fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Filled.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.width(8.dp))
                                            Icon(route.routeInfo.icon, contentDescription = null)
                                            Spacer(Modifier.width(12.dp))
                                            Text(text = label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            val defaultEligible = remember { setOf("tasks","features","home","social","scripts") }
                                            RadioButton(selected = defaultTabId == id, onClick = { if (id in defaultEligible) saveDefault(id) }, enabled = id in defaultEligible)
                                            IconButton(
                                                onClick = {
                                                    if (selectedTabIds.size > 1 && id != defaultTabId && id != "home") {
                                                        selectedTabIds = selectedTabIds.toMutableList().also { it.removeAt(index) }
                                                        saveSelected(selectedTabIds)
                                                    }
                                                },
                                                enabled = id != defaultTabId && id != "home"
                                            ) { Icon(Icons.Filled.Close, contentDescription = null) }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(text = translation["available_tabs_title"], style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableRoutes.forEach { route ->
                                val id = route.routeInfo.id
                                val already = selectedTabIds.contains(id)
                                val label = context.translation["manager.routes.${route.routeInfo.key.substringBefore("/")}"]
                                AnimatedVisibility(
                                    visible = !already && selectedTabIds.size < 5,
                                    enter = scaleIn(tween(160), initialScale = 0.95f) + fadeIn(tween(180)) + slideInVertically(tween(180), initialOffsetY = { it / 3 }),
                                    exit = scaleOut(tween(120)) + fadeOut(tween(120)) + slideOutVertically(tween(120))
                                ) {
                                    AssistChip(
                                        onClick = {
                                            if (!already && selectedTabIds.size < 5) {
                                                selectedTabIds = selectedTabIds + id
                                                saveSelected(selectedTabIds)
                                            }
                                        },
                                        label = { Text(text = label) },
                                        leadingIcon = { Icon(route.routeInfo.icon, contentDescription = null) },
                                        enabled = true
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(onClick = {
                                selectedTabIds = defaultOrder
                                saveSelected(selectedTabIds)
                            }) { Text(text = translation["reset_button"], style = MaterialTheme.typography.labelLarge) }
                            Button(onClick = { openBottomBarCustomization = false }) { Text(text = translation["done_button"], style = MaterialTheme.typography.labelLarge) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
    @Composable
    fun Fab() {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        remember(navBackStackEntry) { routes.getCurrentRoute(navBackStackEntry) }?.floatingActionButton?.invoke()
    }
    @Composable
    fun NavContent(paddingValues: PaddingValues, startDestination: String) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        ) {
            routes.getRoutes().filter { it.parentRoute == null }.forEach { route ->
                val children = routes.getRoutes().filter { it.parentRoute == route }
                if (children.isEmpty()) {
                    val isSummaryScreen = route.routeInfo.id == Routes.CONFIG_IMPORT_CONFIRMATION_ROUTE ||
                            route.routeInfo.id == Routes.CONFIG_EXPORT_SUMMARY_ROUTE ||
                            route.routeInfo.id == Routes.FRIEND_TRACKER_CONFIG_EXPORT_ROUTE ||
                            route.routeInfo.id == Routes.FRIEND_TRACKER_CONFIG_IMPORT_ROUTE
                    val isAddRuleScreen = route.routeInfo.id.startsWith("edit_rule")
                    val animatedRoutes = setOf("friend_tracker_catalog", "manage_friend_tracker_repos", "manage_script_repos", "manage_repos")
                    val isAnimatedRoute = animatedRoutes.contains(route.routeInfo.id)
                    val addRuleEnterAnimation = slideInHorizontally(animationSpec = tween(400)) { it }
                    val addRuleExitAnimation = slideOutHorizontally(animationSpec = tween(400)) { -it }
                    val addRulePopEnterAnimation = slideInHorizontally(animationSpec = tween(400)) { -it }
                    val addRulePopExitAnimation = slideOutHorizontally(animationSpec = tween(400)) { it }
                    val animatedRouteEnter = slideInHorizontally(animationSpec = tween(400)) { it }
                    val animatedRouteExit = slideOutHorizontally(animationSpec = tween(400)) { -it }
                    val animatedRoutePopEnter = slideInHorizontally(animationSpec = tween(400)) { -it }
                    val animatedRoutePopExit = slideOutHorizontally(animationSpec = tween(400)) { it }
                    composable(
                        route.routeInfo.id,
                        enterTransition = {
                            when {
                                isSummaryScreen -> slideInHorizontally { it }
                                isAddRuleScreen -> addRuleEnterAnimation
                                isAnimatedRoute -> animatedRouteEnter
                                else -> fadeIn(tween(100))
                            }
                        },
                        exitTransition = {
                            when {
                                isSummaryScreen -> slideOutHorizontally { -it }
                                isAddRuleScreen -> addRuleExitAnimation
                                isAnimatedRoute -> animatedRouteExit
                                else -> fadeOut(tween(100))
                            }
                        },
                        popEnterTransition = {
                            when {
                                isSummaryScreen -> slideInHorizontally { -it }
                                isAddRuleScreen -> addRulePopEnterAnimation
                                isAnimatedRoute -> animatedRoutePopEnter
                                else -> fadeIn(tween(100))
                            }
                        },
                        popExitTransition = {
                            when {
                                isSummaryScreen -> slideOutHorizontally { it }
                                isAddRuleScreen -> addRulePopExitAnimation
                                isAnimatedRoute -> animatedRoutePopExit
                                else -> fadeOut(tween(100))
                            }
                        }
                    ) { route.content.invoke(it) }
                    route.customComposables.invoke(this)
                } else {
                    navigation("main_" + route.routeInfo.id, route.routeInfo.id) {
                        composable("main_" + route.routeInfo.id) { route.content.invoke(it) }
                        children.forEach { child -> composable(child.routeInfo.id) { child.content.invoke(it) } }
                        route.customComposables.invoke(this)
                    }
                }
            }
        }
    }

    @Composable fun FloatingActionButton() = Fab()
    @Composable fun Content(paddingValues: PaddingValues, startDestination: String) = NavContent(paddingValues, startDestination)
}