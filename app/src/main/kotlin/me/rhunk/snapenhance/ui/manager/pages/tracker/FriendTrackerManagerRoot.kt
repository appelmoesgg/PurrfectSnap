package me.rhunk.snapenhance.ui.manager.pages.tracker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Store
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.common.ui.rememberAsyncMutableState
import me.rhunk.snapenhance.common.ui.rememberAsyncMutableStateList
import me.rhunk.snapenhance.common.ui.rememberAsyncUpdateDispatcher
import me.rhunk.snapenhance.common.util.snap.BitmojiSelfie
import me.rhunk.snapenhance.storage.*
import me.rhunk.snapenhance.ui.manager.Routes
import me.rhunk.snapenhance.ui.util.ActivityLauncherHelper
import me.rhunk.snapenhance.ui.util.coil.BitmojiImage
import me.rhunk.snapenhance.ui.util.openFile
import me.rhunk.snapenhance.ui.util.pagerTabIndicatorOffset


@OptIn(ExperimentalFoundationApi::class)
class FriendTrackerManagerRoot : Routes.Route() {
    enum class FilterType {
        CONVERSATION, USERNAME, EVENT
    }

    override val translation by lazy { context.translation.getCategory("manager.friend_tracker") }
    private val titles by lazy {
        listOf(
            translation["rules_tab"],
            translation["logs_tab"]
        )
    }
    private var currentPage by mutableIntStateOf(0)
    private lateinit var logDeleteAction : () -> Unit
    private lateinit var exportAction : () -> Unit

    override val topBarActions: @Composable RowScope.() -> Unit = {
        var showExportDialog by remember { mutableStateOf(false) }
        var showSingleExportDialog by remember { mutableStateOf(false) }
        var showImportDialog by remember { mutableStateOf(false) }
        var showInvalidImportTypeDialog by remember { mutableStateOf(false) }

        if (showExportDialog) {
            ChoiceDialog(
                onDismissRequest = { showExportDialog = false },
                title = translation["export_dialog_title"],
                choices = listOf(
                    translation["bulk_export_button"] to { Icon(Icons.Default.UploadFile, translation["bulk_export_button"]) },
                    translation["individual_export_button"] to { Icon(Icons.Default.FileOpen, translation["individual_export_button"]) }
                ),
                onChoiceSelected = { index ->
                    showExportDialog = false
                    when (index) {
                        0 -> routes.friendTrackerConfigExport.navigate()
                        1 -> showSingleExportDialog = true
                    }
                }
            )
        }

        if (showSingleExportDialog) {
            val rules = rememberAsyncMutableStateList(defaultValue = emptyList()) {
                context.database.getTrackerRulesDesc()
            }
            SelectRuleDialog(
                onDismissRequest = { showSingleExportDialog = false },
                rules = rules,
                onRuleSelected = { rule ->
                    showSingleExportDialog = false
                    routes.friendTrackerConfigExport.navigate {
                        this["rule_id"] = rule.id.toString()
                    }
                },
                translation = translation
            )
        }

        fun handleImport(type: me.rhunk.snapenhance.common.data.ExportType) {
            routes.activityLauncher.openFile("application/json") { uri ->
                runCatching {
                    val content = context.androidContext.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: return@runCatching
                    val exportedData = context.gson.fromJson(content, me.rhunk.snapenhance.common.data.ExportedTrackerData::class.java)
                    if (exportedData.type != type) {
                        showInvalidImportTypeDialog = true
                        return@runCatching
                    }
                    routes.friendTrackerConfigJsonForImport = content
                    routes.friendTrackerConfigImport.navigate()
                }.onFailure {
                    context.longToast("Failed to read file: ${it.message}")
                }
            }
        }

        if (showInvalidImportTypeDialog) {
            AlertDialog(
                onDismissRequest = { showInvalidImportTypeDialog = false },
                title = { Text(translation["invalid_import_type_dialog_title"]) },
                text = { Text(translation["invalid_import_type_dialog_text"]) },
                confirmButton = {
                    Button(onClick = { showInvalidImportTypeDialog = false }) {
                        Text(translation["button.ok"])
                    }
                }
            )
        }

        if (showImportDialog) {
            ChoiceDialog(
                onDismissRequest = { showImportDialog = false },
                title = translation["import_dialog_title"],
                choices = listOf(
                    translation["bulk_import_button"] to { Icon(Icons.Default.UploadFile, translation["bulk_import_button"]) },
                    translation["individual_import_button"] to { Icon(Icons.Default.FileOpen, translation["individual_import_button"]) }
                ),
                onChoiceSelected = { index ->
                    showImportDialog = false
                    when (index) {
                        0 -> handleImport(me.rhunk.snapenhance.common.data.ExportType.BULK)
                        1 -> handleImport(me.rhunk.snapenhance.common.data.ExportType.SINGLE)
                    }
                }
            )
        }

        if (currentPage == 0) {
            IconButton(onClick = {
                showImportDialog = true
            }) {
                Icon(Icons.Default.FolderOpen, contentDescription = translation["import_button_description"])
            }
            IconButton(onClick = {
                showExportDialog = true
            }) {
                Icon(Icons.Default.SaveAlt, contentDescription = translation["export_button_description"])
            }
        }
    }

    private lateinit var activityLauncherHelper: ActivityLauncherHelper

    override val init: () -> Unit = {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
    }

    override val floatingActionButton: @Composable () -> Unit = {
        when (currentPage) {
            1 -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ExtendedFloatingActionButton(
                        icon = { Icon(Icons.Default.SaveAlt, contentDescription = translation["export_button_description"]) },
                        expanded = true,
                        text = { Text(translation["export_button"]) },
                        onClick = {
                            context.coroutineScope.launch { exportAction() }
                        }
                    )
                    ExtendedFloatingActionButton(
                        icon = { Icon(Icons.Default.DeleteOutline, contentDescription = translation["delete_button_description"]) },
                        expanded = true,
                        text = { Text(translation["delete_button"]) },
                        onClick = {
                            context.coroutineScope.launch { logDeleteAction() }
                        }
                    )
                }
            }
            0 -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                    ExtendedFloatingActionButton(
                        icon = { Icon(Icons.Default.Store, contentDescription = translation["catalog_button_description"]) },
                        expanded = true,
                        text = { Text(translation["catalog_button"]) },
                        onClick = { routes.friendTrackerCatalog.navigate() }
                    )
                    ExtendedFloatingActionButton(
                        icon = { Icon(Icons.Default.Add, contentDescription = translation["add_rule_button_description"]) },
                        expanded = true,
                        text = { Text(translation["add_rule_button"]) },
                        onClick = { routes.editRule.navigate() }
                    )
                }
            }
        }
    }

    @Composable
    private fun ConfigRulesTab() {
        val updateRules = rememberAsyncUpdateDispatcher()
        val rules = rememberAsyncMutableStateList(defaultValue = listOf(), updateDispatcher = updateRules) {
            context.database.getTrackerRulesDesc()
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = routes.bottomPadding)
            ) {
                item {
                    if (rules.isEmpty()) {
                        Text(translation["no_rules_found"], modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Light)
                    }
                }
                items(rules, key = { it.id }) { rule ->
                    val ruleName by rememberAsyncMutableState(defaultValue = rule.name) {
                        context.database.getTrackerRule(rule.id)?.name ?: translation["empty_rule_name"]
                    }
                    val eventCount by rememberAsyncMutableState(defaultValue = 0) {
                        context.database.getTrackerEvents(rule.id).size
                    }
                    val scopeCount by rememberAsyncMutableState(defaultValue = 0) {
                        context.database.getRuleTrackerScopes(rule.id).size
                    }
                    var enabled by rememberAsyncMutableState(defaultValue = rule.enabled) {
                        context.database.getTrackerRule(rule.id)?.enabled ?: false
                    }

                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                routes.editRule.navigate {
                                    this["rule_id"] = rule.id.toString()
                                }
                            }
                            .padding(5.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(ruleName, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text(buildString {
                                    append(eventCount)
                                    append(" ")
                                    append(translation["events_suffix"])
                                    if (scopeCount > 0) {
                                        append(", ")
                                        append(scopeCount)
                                        append(" ")
                                        append(translation["scopes_suffix"])
                                    }
                                }, fontSize = 13.sp, fontWeight = FontWeight.Light)
                            }

                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val scopesBitmoji = rememberAsyncMutableStateList(defaultValue = emptyList()) {
                                    context.database.getRuleTrackerScopes(rule.id, limit = 10).mapNotNull {
                                        context.database.getFriendInfo(it.key)?.let { friend ->
                                            friend.selfieId to friend.bitmojiId
                                        }
                                    }.take(3)
                                }

                                Row {
                                    scopesBitmoji.forEachIndexed { index, friend ->
                                        Box(
                                            modifier = Modifier
                                                .offset(x = (-index * 20).dp + (scopesBitmoji.size * 20).dp - 20.dp)
                                        ) {
                                            BitmojiImage(
                                                size = 50,
                                                modifier = Modifier
                                                    .border(
                                                        BorderStroke(1.dp, Color.White),
                                                        CircleShape
                                                    )
                                                    .background(Color.White, CircleShape)
                                                    .clip(CircleShape),
                                                context = context,
                                                url = BitmojiSelfie.getBitmojiSelfie(friend.first, friend.second, BitmojiSelfie.BitmojiSelfieType.NEW_THREE_D),
                                            )
                                        }
                                    }
                                }

                                Box(modifier = Modifier
                                    .padding(start = 5.dp, end = 5.dp)
                                    .height(50.dp)
                                    .width(1.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(5.dp)
                                    )
                                )

                                Switch(
                                    checked = enabled,
                                    onCheckedChange = {
                                        enabled = it
                                        context.database.setTrackerRuleState(rule.id, it)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }


    @OptIn(ExperimentalFoundationApi::class)
    override val content: @Composable (NavBackStackEntry) -> Unit = {
        val coroutineScope = rememberCoroutineScope()
        val pagerState = rememberPagerState(initialPage = 0) { titles.size }
        currentPage = pagerState.currentPage

        Column {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                titles.forEachIndexed { i, text ->
                    val shape = when (i) {
                        0 -> RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                        titles.lastIndex -> RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
                        else -> RoundedCornerShape(0.dp)
                    }
                    SegmentedButton(
                        selected = pagerState.currentPage == i,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(i)
                            }
                        },
                        shape = shape,
                        modifier = Modifier.weight(1f),
                        icon = {},
                        label = { Text(text) }
                    )
                }
            }

            HorizontalPager(
                modifier = Modifier.weight(1f),
                state = pagerState
            ) { page ->
                when (page) {
                    1 -> LogsTab(
                        context = context,
                        activityLauncherHelper = activityLauncherHelper,
                        deleteAction = { logDeleteAction = it },
                        exportAction = { exportAction = it },
                        bottomPadding = routes.bottomPadding
                    )
                    0 -> ConfigRulesTab()
                }
            }
        }
    }
}

@Composable
private fun SelectRuleDialog(
    onDismissRequest: () -> Unit,
    rules: List<me.rhunk.snapenhance.common.data.TrackerRule>,
    onRuleSelected: (me.rhunk.snapenhance.common.data.TrackerRule) -> Unit,
    translation: me.rhunk.snapenhance.common.bridge.wrapper.LocaleWrapper
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(translation["manager.friend_tracker.select_rule_to_export_title"], style = MaterialTheme.typography.headlineSmall)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rules) { rule ->
                        ElevatedCard(
                            onClick = { onRuleSelected(rule) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = rule.name,
                                modifier = Modifier.padding(16.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                TextButton(onClick = onDismissRequest) {
                    Text(translation["button.cancel"])
                }
            }
        }
    }
}

@Composable
private fun ChoiceDialog(
    onDismissRequest: () -> Unit,
    title: String,
    choices: List<Pair<String, @Composable () -> Unit>>,
    onChoiceSelected: (Int) -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.headlineSmall)
                choices.forEachIndexed { index, (text, icon) ->
                    SelectButton(
                        onClick = { onChoiceSelected(index) },
                        text = text,
                        leadingIcon = icon
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectButton(
    onClick: () -> Unit,
    text: String,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (leadingIcon != null) {
                leadingIcon()
            }
            Text(text = text, modifier = Modifier.weight(1f))
        }
    }
}
