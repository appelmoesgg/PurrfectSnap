@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
package me.rhunk.snapenhance.ui.manager.pages.tracker

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.common.data.*
import me.rhunk.snapenhance.common.ui.rememberAsyncMutableState
import me.rhunk.snapenhance.common.ui.rememberAsyncMutableStateList
import me.rhunk.snapenhance.storage.*
import me.rhunk.snapenhance.ui.manager.Routes
import me.rhunk.snapenhance.ui.manager.components.AestheticDialog
import me.rhunk.snapenhance.ui.manager.pages.social.AddFriendDialog

class EditRule : Routes.Route() {
    override val translation by lazy { context.translation.getCategory("manager.friend_tracker") }
    @Composable
    fun ActionCheckbox(
        text: String,
        checked: MutableState<Boolean>,
        onChanged: (Boolean) -> Unit = {}
    ) {
        Row(
            modifier = Modifier.clickable {
                checked.value = !checked.value
                onChanged(checked.value)
            },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                modifier = Modifier.height(30.dp),
                checked = checked.value,
                onCheckedChange = { checked.value = it; onChanged(it) }
            )
            Text(text, fontSize = 12.sp)
        }
    }
    @Composable
    fun ConditionCheckboxes(
        params: TrackerRuleActionParams
    ) {
        ActionCheckbox(
            text = translation["condition_only_inside_conversation"],
            checked = remember { mutableStateOf(params.onlyInsideConversation) },
            onChanged = { params.onlyInsideConversation = it }
        )
        ActionCheckbox(
            text = translation["condition_only_outside_conversation"],
            checked = remember { mutableStateOf(params.onlyOutsideConversation) },
            onChanged = { params.onlyOutsideConversation = it }
        )
        ActionCheckbox(
            text = translation["condition_only_when_app_active"],
            checked = remember { mutableStateOf(params.onlyWhenAppActive) },
            onChanged = { params.onlyWhenAppActive = it }
        )
        ActionCheckbox(
            text = translation["condition_only_when_app_inactive"],
            checked = remember { mutableStateOf(params.onlyWhenAppInactive) },
            onChanged = { params.onlyWhenAppInactive = it }
        )
        ActionCheckbox(
            text = translation["condition_no_push_notification_when_app_active"],
            checked = remember { mutableStateOf(params.noPushNotificationWhenAppActive) },
            onChanged = { params.noPushNotificationWhenAppActive = it }
        )
    }
    @Composable
    fun AddEventDialog(
        onDismissRequest: () -> Unit,
        onEventAdd: (TrackerRuleEvent) -> Unit
    ) {
        val expanded = remember { mutableStateOf(false) }
        val currentEventType = remember { mutableStateOf(TrackerEventType.CONVERSATION_ENTER.key) }
        val addEventActions = remember { mutableStateOf(emptySet<TrackerRuleAction>()) }
        val addEventActionParams = remember { TrackerRuleActionParams() }
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(dismissOnClickOutside = true, usePlatformDefaultWidth = false)
        ) {
            Card {
                Column(
                    Modifier
                        .padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(translation["add_event_dialog_title"], style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = translation["button.close"])
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    ExposedDropdownMenuBox(
                        expanded = expanded.value,
                        onExpandedChange = { expanded.value = !expanded.value },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = context.translation["tracker_events.${currentEventType.value}"],
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(translation["event_type_label"]) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value) },
                        )
                        ExposedDropdownMenu(
                            expanded = expanded.value,
                            onDismissRequest = { expanded.value = false }
                        ) {
                            TrackerEventType.entries.forEach { eventType ->
                                DropdownMenuItem(
                                    onClick = {
                                        currentEventType.value = eventType.key
                                        expanded.value = false
                                    },
                                    text = { Text(context.translation["tracker_events.${eventType.key}"]) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(translation["triggers_title"], style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TrackerRuleAction.entries.forEach { action ->
                            ActionCheckbox(
                                context.translation["tracker_actions.${action.key}"],
                                checked = remember { mutableStateOf(addEventActions.value.contains(action)) }
                            ) {
                                if (it) addEventActions.value += action else addEventActions.value -= action
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(translation["conditions_title"], style = MaterialTheme.typography.titleMedium)
                    ConditionCheckboxes(addEventActionParams)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            onEventAdd(
                                TrackerRuleEvent(
                                    id = -1,
                                    enabled = true,
                                    eventType = currentEventType.value,
                                    params = addEventActionParams.copy(),
                                    actions = addEventActions.value.toList()
                                )
                            )
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text(translation["add_button"]) }
                }
            }
        }
    }
    override val title: @Composable () -> Unit = {}
    @OptIn(ExperimentalFoundationApi::class)
    override val content: @Composable (NavBackStackEntry) -> Unit = { navBackStackEntry ->
        val coroutineScope = rememberCoroutineScope()
        val currentRuleId = navBackStackEntry.arguments?.getString("rule_id")?.toIntOrNull()
        val events = rememberAsyncMutableStateList<TrackerRuleEvent>(defaultValue = emptyList()) {
            currentRuleId?.let { ruleId -> context.database.getTrackerEvents(ruleId) } ?: emptyList()
        }
        val eventsToDelete = remember { mutableStateListOf<TrackerRuleEvent>() }
        var currentScopeType by remember { mutableStateOf(TrackerScopeType.BLACKLIST) }
        val scopes = rememberAsyncMutableStateList<String>(defaultValue = emptyList()) {
            currentRuleId?.let { ruleId ->
                context.database.getRuleTrackerScopes(ruleId).also { map ->
                    currentScopeType = if (map.isEmpty()) TrackerScopeType.WHITELIST else map.values.first()
                }.map { entry -> entry.key }
            } ?: emptyList()
        }
        val ruleName = rememberAsyncMutableState<String>(defaultValue = "", keys = arrayOf(currentRuleId)) {
            currentRuleId?.let { ruleId -> context.database.getTrackerRule(ruleId)?.name ?: translation["default_rule_name"] } ?: translation["default_rule_name"]
        }
        val authorName = rememberAsyncMutableState<String>(defaultValue = "", keys = arrayOf(currentRuleId)) {
            currentRuleId?.let { ruleId -> context.database.getTrackerRule(ruleId)?.author ?: "" } ?: ""
        }
        val initialRuleState by remember(ruleName.value.isNotBlank() || events.isNotEmpty() || scopes.isNotEmpty()) {
            mutableStateOf(
                mapOf(
                    "name" to ruleName.value,
                    "author" to authorName.value,
                    "scopes" to scopes.toList(),
                    "events" to events.toList(),
                    "scopeType" to currentScopeType
                )
            )
        }
        val isDirty by remember {
            derivedStateOf {
                initialRuleState["name"] != ruleName.value ||
                initialRuleState["author"] != authorName.value ||
                initialRuleState["scopes"] != scopes.toList() ||
                initialRuleState["events"] != events.toList() ||
                initialRuleState["scopeType"] != currentScopeType
            }
        }
        var deleteConfirmation by remember { mutableStateOf(false) }
        var showDuplicateNameDialog by remember { mutableStateOf(false) }
        var showEventsEmptyDialog by remember { mutableStateOf(false) }
        var showDiscardDialog by remember { mutableStateOf(false) }
        var addFriendDialog by remember { mutableStateOf<AddFriendDialog?>(null) }
        var addEventDialogVisible by remember { mutableStateOf(false) }
        if (showDiscardDialog) {
            AestheticDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = translation["discard_changes_dialog_title"],
                text = translation["discard_changes_dialog_text"],
                icon = Icons.Default.Warning,
                confirmButtonText = translation["discard_button"],
                onConfirm = {
                    showDiscardDialog = false
                    routes.navController.popBackStack()
                },
                dismissButtonText = translation["button.cancel"],
                onDismiss = { showDiscardDialog = false }
            )
        }
        BackHandler(enabled = isDirty) {
            showDiscardDialog = true
        }
        if (showEventsEmptyDialog) {
            Dialog(onDismissRequest = { showEventsEmptyDialog = false }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = translation["cannot_save_rule_dialog_title"],
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = translation["cannot_save_rule_dialog_text"],
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { showEventsEmptyDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(translation["button.ok"])
                        }
                    }
                }
            }
        }
        if (showDuplicateNameDialog) {
            AlertDialog(
                onDismissRequest = { showDuplicateNameDialog = false },
                title = { Text(translation["duplicate_rule_name_dialog_title"]) },
                text = { Text(translation["duplicate_rule_name_dialog_text"]) },
                confirmButton = {
                    Button(onClick = { showDuplicateNameDialog = false }) {
                        Text(translation["button.ok"])
                    }
                }
            )
        }
        if (deleteConfirmation) {
            AlertDialog(
                onDismissRequest = { deleteConfirmation = false },
                title = { Text(translation["delete_rule_dialog_title"]) },
                text = { Text(translation["delete_rule_dialog_text"]) },
                confirmButton = {
                    Button(
                        onClick = {
                            if (currentRuleId != null) context.database.deleteTrackerRule(currentRuleId)
                            routes.navController.popBackStack()
                        }
                    ) { Text(translation["delete_button"]) }
                },
                dismissButton = {
                    Button(onClick = { deleteConfirmation = false }) { Text(translation["button.cancel"]) }
                }
            )
        }
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .navigationBarsPadding(),
            topBar = {
                TopAppBar(
                    title = { Text(translation[if (currentRuleId == null) "new_rule_title" else "edit_rule_title"]) },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isDirty) {
                                showDiscardDialog = true
                            } else {
                                routes.navController.popBackStack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = translation["back_button_description"])
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (events.isEmpty()) {
                                showEventsEmptyDialog = true
                                return@IconButton
                            }
                            if (currentRuleId == null && context.database.getTrackerRuleByName(ruleName.value.trim()) != null) {
                                showDuplicateNameDialog = true
                                return@IconButton
                            }
                            val ruleId = currentRuleId ?: context.database.newTrackerRule()
                            eventsToDelete.forEach { event -> context.database.deleteTrackerRuleEvent(event.id.takeIf { it > -1 } ?: return@forEach) }
                            events.forEach { event ->
                                context.database.addOrUpdateTrackerRuleEvent(
                                    event.id.takeIf { it > -1 },
                                    ruleId,
                                    event.eventType,
                                    event.params,
                                    event.actions
                                )
                            }
                            context.database.setTrackerRuleName(ruleId, ruleName.value.trim())
                            context.database.setTrackerRuleAuthor(ruleId, authorName.value.trim())
                            context.database.setRuleTrackerScopes(ruleId, currentScopeType, scopes)
                            routes.navController.popBackStack()
                        }) { Icon(Icons.Filled.Save, contentDescription = translation["save_button_description"]) }
                        if (currentRuleId != null) {
                            IconButton(onClick = { deleteConfirmation = true }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = translation["delete_button_description"])
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(translation["general_section_title"], style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = ruleName.value,
                            onValueChange = { ruleName.value = it },
                            label = { Text(translation["rule_name_label"]) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = authorName.value,
                            onValueChange = { authorName.value = it },
                            label = { Text(translation["author_name_label"]) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(translation["scope_section_title"], style = MaterialTheme.typography.titleMedium)
                        val friendDialogActions = remember {
                            AddFriendDialog.Actions(
                                onFriendState = { friend, state ->
                                    if (state) scopes.add(friend.userId) else scopes.remove(friend.userId)
                                },
                                onGroupState = { group, state ->
                                    if (state) scopes.add(group.conversationId) else scopes.remove(group.conversationId)
                                },
                                getFriendState = { friend -> friend.userId in scopes },
                                getGroupState = { group -> group.conversationId in scopes }
                            )
                        }
                        val scopeOptions = listOf(translation["scope_all"], translation["scope_whitelist"], translation["scope_blacklist"])
                        val selectedScopeIndex = when {
                            scopes.isEmpty() -> 0
                            currentScopeType == TrackerScopeType.WHITELIST -> 1
                            else -> 2
                        }
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp)
                        ) {
                            scopeOptions.forEachIndexed { index, label ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index, scopeOptions.size),
                                    onClick = {
                                        when (index) {
                                            0 -> scopes.clear()
                                            1 -> {
                                                currentScopeType = TrackerScopeType.WHITELIST
                                                if (scopes.isEmpty()) {
                                                    addFriendDialog = AddFriendDialog(context, friendDialogActions, pinnedIds = scopes)
                                                }
                                            }
                                            2 -> {
                                                currentScopeType = TrackerScopeType.BLACKLIST
                                                if (scopes.isEmpty()) {
                                                    addFriendDialog = AddFriendDialog(context, friendDialogActions, pinnedIds = scopes)
                                                }
                                            }
                                        }
                                    },
                                    selected = index == selectedScopeIndex
                                ) { Text(label) }
                            }
                        }
                        if (scopes.isNotEmpty()) {
                            Button(
                                onClick = {
                                    addFriendDialog = AddFriendDialog(context, friendDialogActions, pinnedIds = scopes)
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) { Text(translation.format("select_friends_groups_button", "count" to scopes.size.toString())) }
                        }
                        addFriendDialog?.Content { addFriendDialog = null }
                    }
                }
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(
                        Modifier
                            .padding(16.dp)
                            .animateContentSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(translation["events_section_title"], fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { addEventDialogVisible = true }) {
                                Icon(Icons.Default.Add, contentDescription = translation["add_event_button"], modifier = Modifier.size(28.dp))
                            }
                        }
                        if (addEventDialogVisible) {
                            AddEventDialog(
                                onDismissRequest = { addEventDialogVisible = false },
                                onEventAdd = { event ->
                                    events.add(0, event)
                                    addEventDialogVisible = false
                                }
                            )
                        }
                        if (events.isEmpty()) {
                            Text(
                                translation["no_events_text"],
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Light,
                                modifier = Modifier
                                    .padding(10.dp)
                                    .fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                        events.forEach { event ->
                            var expanded by remember { mutableStateOf(false) }
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize()
                                    .padding(vertical = 4.dp)
                                    .clickable { expanded = !expanded }
                            ) {
                                Column(Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f, fill = false),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                contentDescription = null
                                            )
                                            Column {
                                                Text(
                                                    context.translation["tracker_events.${event.eventType}"],
                                                    lineHeight = 20.sp,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = event.actions.joinToString(", ") { context.translation["tracker_actions.${it.key}"] },
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Light,
                                                    overflow = TextOverflow.Ellipsis,
                                                    maxLines = 1,
                                                    lineHeight = 14.sp
                                                )
                                            }
                                        }
                                        OutlinedIconButton(
                                            onClick = {
                                                if (event.id > -1) {
                                                    eventsToDelete.add(event)
                                                }
                                                events.remove(event)
                                            }
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = translation["delete_button_description"])
                                        }
                                    }
                                    if (expanded) {
                                        Column(modifier = Modifier.padding(top = 8.dp)) {
                                            ConditionCheckboxes(event.params)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(routes.bottomPadding))
            }
        }
    }
}
