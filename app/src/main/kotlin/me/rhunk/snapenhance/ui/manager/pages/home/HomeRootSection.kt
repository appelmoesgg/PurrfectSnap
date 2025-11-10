package me.rhunk.snapenhance.ui.manager.pages.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.action.EnumQuickActions
import me.rhunk.snapenhance.common.BuildConfig
import me.rhunk.snapenhance.common.action.EnumAction
import me.rhunk.snapenhance.common.ui.TopBarActionButton
import me.rhunk.snapenhance.common.ui.rememberAsyncMutableState
import me.rhunk.snapenhance.common.ui.rememberAsyncMutableStateList
import me.rhunk.snapenhance.common.util.ktx.openLink

import me.rhunk.snapenhance.storage.getQuickTiles
import me.rhunk.snapenhance.storage.setQuickTiles
import me.rhunk.snapenhance.ui.manager.Routes
import me.rhunk.snapenhance.ui.manager.data.UpdateDownloader
import me.rhunk.snapenhance.ui.manager.data.Updater
import me.rhunk.snapenhance.ui.util.ActivityLauncherHelper
import me.rhunk.snapenhance.ui.util.AlertDialogs
import me.rhunk.snapenhance.ui.util.scaleOnPress
import java.text.DateFormat
import kotlin.math.roundToInt

class HomeRootSection : Routes.Route() {
    override val translation by lazy { context.translation.getCategory("manager.sections.home") }

    companion object {
        val cardMargin = 10.dp
    }
    private lateinit var activityLauncherHelper: ActivityLauncherHelper
    data class QaCard(val id: String, val name: String, val icon: ImageVector, val action: (Routes) -> Unit)
    private val cardEntries by lazy {
        val list = mutableListOf<QaCard>()
        EnumQuickActions.entries.forEach { q ->
            val name = context.translation["actions.${q.key}.name"]
            list.add(QaCard(id = "quick.${q.key}", name = name, icon = q.icon, action = q.action))
        }
        EnumAction.entries.forEach { a ->
            val name = context.translation["actions.${a.key}.name"]
            list.add(QaCard(id = "action.${a.key}", name = name, icon = a.icon, action = { context.launchActionIntent(a) }))
        }
        list
    }
    private val cards by lazy {
        EnumQuickActions.entries.map {
            (context.translation["actions.${it.key}.name"] to it.icon) to it.action
        }.associate {
            it.first to it.second
        }.toMutableMap().apply {
            EnumAction.entries.forEach { action ->
                this[context.translation["actions.${action.key}.name"] to action.icon] = {
                    context.launchActionIntent(action)
                }
            }
        }
    }
    @Composable
    private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
        OutlinedCard(
            modifier = Modifier
                .padding(start = cardMargin, end = cardMargin)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 10.dp)
            ) {
                content()
            }
        }
    }
    @Composable
    fun ExternalLinkIcon(
        modifier: Modifier = Modifier,
        size: Dp = 32.dp,
        imageVector: ImageVector,
        onClick: (() -> Unit)? = null,
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(50))
                .scaleOnPress(interactionSource)
                .then(
                    if (onClick != null)
                        Modifier.clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onClick() }
                    else Modifier
                )
                .then(modifier)
        )
    }
    private fun resolveTileKey(name: String): String {
        val entry = cardEntries.firstOrNull { it.name == name }
        return entry?.id ?: name
    }
    private fun getTileSpan(name: String): Pair<Int, Int> {
        val prefs = context.sharedPreferences
        val key = resolveTileKey(name)
        val raw = prefs.getString("quick_tile_size_$key", null) ?: "1x1"
        val parts = raw.split('x')
        val w = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(1, 3) ?: 1
        val h = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 3) ?: 1
        return w to h
    }
    private fun setTileSpan(name: String, w: Int, h: Int) {
        val prefs = context.sharedPreferences
        val key = resolveTileKey(name)
        prefs.edit().putString("quick_tile_size_$key", "${w.coerceIn(1,3)}x${h.coerceIn(1,3)}").apply()
    }
    private fun clearTileSpan(name: String) {
        val prefs = context.sharedPreferences
        val key = resolveTileKey(name)
        prefs.edit().remove("quick_tile_size_$key").apply()
    }

    private fun getTileOffset(name: String): Pair<Float, Float> {
        val prefs = context.sharedPreferences
        val key = resolveTileKey(name)
        val raw = prefs.getString("quick_tile_offset_$key", null)
        if (raw == null) return 0f to 0f
        val parts = raw.split(',')
        val x = parts.getOrNull(0)?.toFloatOrNull() ?: 0f
        val y = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
        return x to y
    }

    private fun setTileOffset(name: String, x: Float, y: Float) {
        val prefs = context.sharedPreferences
        val key = resolveTileKey(name)
        prefs.edit().putString("quick_tile_offset_$key", "$x,$y").apply()
    }

    private fun clearTileOffset(name: String) {
        val prefs = context.sharedPreferences
        val key = resolveTileKey(name)
        prefs.edit().remove("quick_tile_offset_$key").apply()
    }

    override val title: @Composable (() -> Unit)? = {}
    override val init: () -> Unit = {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
    }
    override val topBarActions: @Composable (RowScope.() -> Unit) = {
        TopBarActionButton(
            onClick = {
                routes.homeLogs.navigate()
            },
            icon = Icons.Filled.BugReport,
            text = context.translation["manager.routes.home_logs"]
        )
        Spacer(modifier = Modifier.width(8.dp))
        TopBarActionButton(
            onClick = {
                routes.settings.navigate()
            },
            icon = Icons.Filled.Settings,
            text = context.translation["manager.routes.home_settings"]
        )
    }

    @OptIn(ExperimentalLayoutApi::class, ExperimentalAnimationApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
    override val content: @Composable (NavBackStackEntry) -> Unit = {
        val avenirNext = remember {
            FontFamily(Font(R.font.avenir_next_medium, FontWeight.Medium))
        }
        val selectedTiles = rememberAsyncMutableStateList(defaultValue = listOf()) {
            context.database.getQuickTiles().filter { it.isNotBlank() }
        }
        val latestUpdate by rememberAsyncMutableState(defaultValue = null) { Updater.latestRelease }
        var showQuickActionsMenu by remember { mutableStateOf(false) }
        var editMode by remember { mutableStateOf(false) }
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState, enabled = !editMode)
        ) {
            Text(
                "PurrfectSnap",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 8.dp)
                    .align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = avenirNext,
                textAlign = TextAlign.Center
            )
            Text(
                text = translation.format(
                    "version_title",
                    "versionName" to BuildConfig.VERSION_NAME
                ),
                fontSize = 14.sp,
                fontFamily = avenirNext,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(15.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 5.dp)
            ) {
                ExternalLinkIcon(
                    onClick = { context.androidContext.openLink("https://t.me/purrfectsnap") },
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_telegram),
                )
                ExternalLinkIcon(
                    onClick = { context.androidContext.openLink("https://github.com/particle-box/PurrfectSnap") },
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_github),
                )
                ExternalLinkIcon(
                    onClick = { context.androidContext.openLink("https://github.com/particle-box/PurrfectSnap/wiki") },
                    modifier = Modifier.offset(x = (-3).dp),
                    size = 40.dp,
                    imageVector = Icons.AutoMirrored.Filled.Help,
                )
            }
            if (latestUpdate != null) {
                Spacer(modifier = Modifier.height(10.dp))
                InfoCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = translation["update_title"],
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                fontSize = 12.sp,
                                text = translation.format(
                                    "update_content",
                                    "version" to (latestUpdate?.versionName ?: "unknown")
                                ),
                                lineHeight = 20.sp,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val downloadState by UpdateDownloader.downloadState.collectAsState()
                        val downloadProgress by UpdateDownloader.downloadProgress.collectAsState()
                        val coroutineScope = rememberCoroutineScope()

                        AnimatedContent(
                            targetState = downloadState,
                            modifier = Modifier.height(40.dp)
                        ) { state ->
                            when (state) {
                                UpdateDownloader.DownloadState.IDLE -> {
                                    IconButton(
                                        onClick = {
                                            val latest = latestUpdate ?: return@IconButton
                                            if (latest.workflowId == null) {
                                                context.androidContext.openLink(latest.releaseUrl)
                                                return@IconButton
                                            }
                                            val supportedAbis = android.os.Build.SUPPORTED_ABIS
                                            var abiName: String? = null
                                            for (abi in supportedAbis) {
                                                when (abi) {
                                                    "arm64-v8a" -> {
                                                        abiName = "armv8"
                                                        break
                                                    }
                                                    "armeabi-v7a" -> {
                                                        abiName = "armv7"
                                                        break
                                                    }
                                                }
                                            }

                                            if (abiName != null) {
                                                val artifactName = "purrfectsnap-${abiName}-debug"
                                                val downloadUrl = "https://nightly.link/particle-box/PurrfectSnap/actions/runs/${latest.workflowId}/$artifactName.zip"
                                                UpdateDownloader.downloadAndInstall(context.androidContext, downloadUrl, "$artifactName.zip", coroutineScope)
                                            } else {
                                                android.widget.Toast.makeText(context.androidContext, "Your device architecture is not supported for automatic updates.", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        modifier = Modifier.scaleOnPress(remember { MutableInteractionSource() })
                                    ) {
                                        Icon(imageVector = Icons.Default.Download, contentDescription = translation["download_icon_description"])
                                    }
                                }
                                UpdateDownloader.DownloadState.DOWNLOADING -> {
                                    CircularProgressIndicator(progress = { downloadProgress })
                                }
                                UpdateDownloader.DownloadState.COMPLETED -> {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = translation["completed_icon_description"])
                                }
                                UpdateDownloader.DownloadState.FAILED -> {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = translation["failed_icon_description"])
                                }
                            }
                        }
                    }
                }
            }
            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(10.dp))
                InfoCard {
                    Text(
                        text = translation["debug_build_summary_title"],
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    val buildSummary = buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Light
                            )
                        ) {
                            append(
                                remember {
                                    translation.format(
                                        "debug_build_summary_content",
                                        "versionName" to BuildConfig.VERSION_NAME,
                                        "versionCode" to BuildConfig.VERSION_CODE.toString(),
                                    )
                                }
                            )
                            append(" - ")
                        }
                        withLink(
                            LinkAnnotation.Clickable(
                                "git_hash",
                                linkInteractionListener = {
                                    context.androidContext.openLink("https://github.com/particle-box/PurrfectSnap/commit/${BuildConfig.GIT_HASH}")
                                }
                            )
                        ) {
                            withStyle(
                                style = SpanStyle(
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                append(BuildConfig.GIT_HASH.substring(0, 7))
                            }
                        }
                    }
                    Text(text = buildSummary)
                    Text(
                        fontSize = 12.sp,
                        text = remember {
                            translation.format(
                                "debug_build_summary_date",
                                "date" to DateFormat.getDateTimeInstance().format(BuildConfig.BUILD_TIMESTAMP),
                                "days" to ((System.currentTimeMillis() - BuildConfig.BUILD_TIMESTAMP) / 86400000).toInt().toString()
                            )
                        },
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Light
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            AnimatedContent(targetState = selectedTiles.isNotEmpty(), label = "QuickActionsAnim") { hasQuickActions ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!hasQuickActions) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                tonalElevation = 2.dp,
                                shadowElevation = 4.dp,
                                modifier = Modifier.align(Alignment.Center)
                            ) {
                                Text(
                                    translation["quick_actions_title"],
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Widgets,
                                    contentDescription = translation["quick_actions_icon_description"],
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No quick actions added yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { showQuickActionsMenu = true },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = translation["add_quick_action_description"],
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Add")
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                translation["quick_actions_title"],
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Start,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { showQuickActionsMenu = true },
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_manage),
                                    contentDescription = translation["manage_quick_actions_description"]
                                )
                            }
                            FilterChip(
                                selected = editMode,
                                onClick = { editMode = !editMode },
                                label = { Text(if (editMode) "Done" else "Edit") },
                                leadingIcon = { Icon(Icons.Filled.DragHandle, contentDescription = null) }
                            )
                        }
                        val spacing = 6.dp
                        var spanTick by remember { mutableIntStateOf(0) }

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = cardMargin)
                        ) {
                            val density = LocalDensity.current
                            val baseCell = remember { (maxWidth - (spacing * 2)) / 3f }
                            val baseCellPx = with(density) { baseCell.toPx() }

                            val (tilePositions, totalHeight) = remember(selectedTiles.size, spanTick) {
                                val positions = mutableMapOf<String, Offset>()
                                var currentX = 0f
                                var currentY = 0f
                                var rowMaxHeight = 0f
                                val screenWidthPx = with(density) { maxWidth.toPx() }

                                selectedTiles.forEach { tileName ->
                                    cards.entries.find { entry -> entry.key.first == tileName }?.let {
                                        val card = it.key
                                        val (wSpan, hSpan) = getTileSpan(card.first)
                                        val tileWidthPx = with(density) { (baseCell * wSpan + spacing * (wSpan - 1)).toPx() }
                                        val tileHeightPx = with(density) { (baseCell * hSpan + spacing * (hSpan - 1)).toPx() }

                                        if (currentX + tileWidthPx > screenWidthPx) {
                                            currentX = 0f
                                            currentY += rowMaxHeight
                                            rowMaxHeight = 0f
                                        }

                                        positions[tileName] = Offset(currentX, currentY)
                                        currentX += tileWidthPx + with(density) { spacing.toPx() }
                                        if (tileHeightPx > rowMaxHeight) {
                                            rowMaxHeight = tileHeightPx
                                        }
                                    }
                                }
                                positions to (currentY + rowMaxHeight)
                            }

                            Box(modifier = Modifier.height(with(density) { totalHeight.toDp() })) {
                                remember(selectedTiles.size, context.translation.loadedLocale) {
                                    selectedTiles.mapNotNull {
                                        cards.entries.find { entry -> entry.key.first == it }
                                    }
                                }.forEach { (card, action) ->
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val _tick = spanTick
                                    val (wSpan, hSpan) = getTileSpan(card.first)
                                    val tileWidth = baseCell * wSpan + spacing * (wSpan - 1)
                                    val tileHeight = baseCell * hSpan + spacing * (hSpan - 1)
                                    val tileWidthPx = with(density) { tileWidth.toPx() }
                                    val tileHeightPx = with(density) { tileHeight.toPx() }

                                    var offsetX by remember(card.first) { mutableStateOf(0f) }
                                    var offsetY by remember(card.first) { mutableStateOf(0f) }
                                    var isDragging by remember { mutableStateOf(false) }

                                    LaunchedEffect(card.first, spanTick) {
                                        val (x, y) = getTileOffset(card.first)
                                        if (x != 0f || y != 0f) {
                                            offsetX = x
                                            offsetY = y
                                        } else {
                                            val pos = tilePositions[card.first]
                                            if (pos != null) {
                                                offsetX = pos.x
                                                offsetY = pos.y
                                                setTileOffset(card.first, offsetX, offsetY)
                                            }
                                        }
                                    }

                                    val animatedOffsetX by animateFloatAsState(
                                        targetValue = offsetX,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        ),
                                        label = "offsetX"
                                    )
                                    val animatedOffsetY by animateFloatAsState(
                                        targetValue = offsetY,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        ),
                                        label = "offsetY"
                                    )

                                    val currentOffsetX = if (isDragging) offsetX else animatedOffsetX
                                    val currentOffsetY = if (isDragging) offsetY else animatedOffsetY

                                    val baseModifier = Modifier
                                        .offset { IntOffset(currentOffsetX.roundToInt(), currentOffsetY.roundToInt()) }
                                        .width(tileWidth)
                                        .height(tileHeight)
                                        .padding(all = 6.dp)

                                    val editModifier = baseModifier.then(
                                        Modifier.pointerInput(card.first, tileWidthPx, tileHeightPx) {
                                            var originalOffsetX = 0f
                                            var originalOffsetY = 0f
                                            detectDragGestures(
                                                onDragStart = {
                                                    isDragging = true
                                                    originalOffsetX = offsetX
                                                    originalOffsetY = offsetY
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    offsetX += dragAmount.x
                                                    offsetY += dragAmount.y
                                                },
                                                onDragEnd = {
                                                    isDragging = false
                                                    var targetTile: String? = null
                                                    var maxOverlap = 0f
                                                    val tileRect = Rect(Offset(offsetX, offsetY), Size(tileWidthPx, tileHeightPx))

                                                    for (otherTileName in selectedTiles) {
                                                        if (otherTileName == card.first) continue
                                                        val (otherOffsetX, otherOffsetY) = getTileOffset(otherTileName)
                                                        val (otherWSpan, otherHSpan) = getTileSpan(otherTileName)
                                                        val otherTileWidth = baseCell * otherWSpan + spacing * (otherWSpan - 1)
                                                        val otherTileHeight = baseCell * otherHSpan + spacing * (otherHSpan - 1)
                                                        val otherRect = Rect(Offset(otherOffsetX, otherOffsetY), Size(with(density) { otherTileWidth.toPx() }, with(density) { otherTileHeight.toPx() }))
                                                        val intersectRect = tileRect.intersect(otherRect)
                                                        val overlapArea = intersectRect.width * intersectRect.height
                                                        if (overlapArea > maxOverlap) {
                                                            maxOverlap = overlapArea
                                                            targetTile = otherTileName
                                                        }
                                                    }

                                                    if (targetTile != null) {
                                                        val (wSpan, hSpan) = getTileSpan(card.first)
                                                        val (targetWSpan, targetHSpan) = getTileSpan(targetTile!!)
                                                        if (wSpan == targetWSpan && hSpan == targetHSpan) {
                                                            // swap
                                                            val (targetOffsetX, targetOffsetY) = getTileOffset(targetTile!!)
                                                            setTileOffset(card.first, targetOffsetX, targetOffsetY)
                                                            setTileOffset(targetTile!!, originalOffsetX, originalOffsetY)
                                                            spanTick++ // this will trigger recomposition for all tiles
                                                        } else {
                                                            // revert
                                                            offsetX = originalOffsetX
                                                            offsetY = originalOffsetY
                                                        }
                                                    } else {
                                                        setTileOffset(card.first, offsetX, offsetY)
                                                    }
                                                }
                                            )
                                        }
                                    )
                                    val viewModifier = baseModifier.then(Modifier.scaleOnPress(interactionSource))

                                    if (editMode) {
                                        ElevatedCard(
                                            modifier = editModifier
                                        ) {
                                            Box(Modifier.fillMaxSize()) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(all = 5.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.SpaceEvenly,
                                                ) {
                                                    Icon(
                                                        imageVector = card.second, contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(50.dp)
                                                    )
                                                    Text(
                                                        text = card.first,
                                                        lineHeight = 16.sp,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        textAlign = TextAlign.Center,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                // Drag handle for resizing
                                                var dxAccResize by remember(card.first, spanTick) { mutableStateOf(0f) }
                                                var dyAccResize by remember(card.first, spanTick) { mutableStateOf(0f) }
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .size(28.dp)
                                                        .pointerInput(card.first, spanTick) {
                                                            detectDragGestures(
                                                                onDragStart = {
                                                                    dxAccResize = 0f
                                                                    dyAccResize = 0f
                                                                },
                                                                onDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    dxAccResize += dragAmount.x
                                                                    dyAccResize += dragAmount.y

                                                                    var newW = wSpan
                                                                    var newH = hSpan
                                                                    val step = baseCellPx / 2f

                                                                    while (dxAccResize > step) {
                                                                        newW = (wSpan + 1).coerceIn(1, 3)
                                                                        dxAccResize -= step
                                                                    }
                                                                    while (dxAccResize < -step) {
                                                                        newW = (wSpan - 1).coerceIn(1, 3)
                                                                        dxAccResize += step
                                                                    }
                                                                    while (dyAccResize > step) {
                                                                        newH = (hSpan + 1).coerceIn(1, 3)
                                                                        dyAccResize -= step
                                                                    }
                                                                    while (dyAccResize < -step) {
                                                                        newH = (hSpan - 1).coerceIn(1, 3)
                                                                        dyAccResize += step
                                                                    }

                                                                    if (newW != wSpan || newH != hSpan) {
                                                                        setTileSpan(card.first, newW, newH)
                                                                        spanTick++
                                                                        selectedTiles.forEach { clearTileOffset(it) }
                                                                    }
                                                                }
                                                            )
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Filled.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    } else {
                                        ElevatedCard(
                                            modifier = viewModifier,
                                            onClick = { action(routes) },
                                            interactionSource = interactionSource
                                        ) {
                                            Box(Modifier.fillMaxSize()) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(all = 5.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.SpaceEvenly,
                                                ) {
                                                    Icon(
                                                        imageVector = card.second, contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(50.dp)
                                                    )
                                                    Text(
                                                        text = card.first,
                                                        lineHeight = 16.sp,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        textAlign = TextAlign.Center,
                                                        overflow = TextOverflow.Ellipsis,
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
            if (showQuickActionsMenu) {
                QuickActionsDialog(
                    quickActions = cards,
                    selectedQuickActions = selectedTiles,
                    onDismiss = { showQuickActionsMenu = false },
                    onSave = { newList ->
                        val previous = selectedTiles.toList()
                        val removed = previous.filter { it !in newList }
                        removed.forEach { clearTileSpan(it); clearTileOffset(it) }
                        newList.forEach { clearTileOffset(it) }
                        selectedTiles.clear()
                        selectedTiles.addAll(newList)
                        if (newList.isEmpty()) {
                            editMode = false
                        }
                        context.coroutineScope.launch {
                            context.database.setQuickTiles(selectedTiles)
                        }
                        showQuickActionsMenu = false
                    },
                    translation = translation
                )
            }
            Spacer(modifier = Modifier.height(routes.bottomPadding))
        }
    }
}
