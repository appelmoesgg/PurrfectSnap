package me.rhunk.snapenhance.ui.manager.pages.home

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.navigation.NavBackStackEntry
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.common.action.EnumAction
import me.rhunk.snapenhance.common.bridge.InternalFileHandleType
import me.rhunk.snapenhance.common.ui.ThemeChooserDialog
import me.rhunk.snapenhance.common.ui.ThemeMode
import me.rhunk.snapenhance.common.ui.ThemePreferences
import me.rhunk.snapenhance.common.ui.rememberAsyncMutableState
import me.rhunk.snapenhance.storage.getAllScopeNotes
import me.rhunk.snapenhance.storage.setAllScopeNotes
import me.rhunk.snapenhance.task.UpdateCheckWorker
import me.rhunk.snapenhance.ui.manager.Routes
import me.rhunk.snapenhance.ui.setup.Requirements
import me.rhunk.snapenhance.ui.util.ActivityLauncherHelper
import me.rhunk.snapenhance.ui.util.AlertDialogs
import me.rhunk.snapenhance.ui.util.openFile
import me.rhunk.snapenhance.ui.util.saveFile
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class HomeSettings : Routes.Route() {
    override val translation by lazy { context.translation.getCategory("manager.sections.home_settings") }
    private lateinit var activityLauncherHelper: ActivityLauncherHelper
    private val dialogs by lazy { AlertDialogs(context.translation) }

    private fun scheduleUpdateCheck() {
        val workManager = WorkManager.getInstance(context.androidContext)
        if (context.config.root.global.updateSettings.autoUpdateCheck.get()) {
            val frequency = context.config.root.global.updateSettings.updateCheckFrequency.get()
            val repeatInterval = when (frequency) {
                "daily" -> 1L
                "weekly" -> 7L
                "monthly" -> 30L
                else -> 1L
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = Data.Builder()
                .putString("channel_name", translation["update_notification_channel_name"])
                .putString("channel_description", translation["update_notification_channel_description"])
                .putString("notification_title", translation["update_notification_title"])
                .putString("notification_text", translation["update_notification_text"])
                .build()

            val workRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(repeatInterval, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "snapenhance_update_check",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        } else {
            workManager.cancelUniqueWork("snapenhance_update_check")
        }
    }
    override val init: () -> Unit = {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
    }
    @Composable
    private fun RowTitle(title: String) {
        Text(text = title, modifier = Modifier.padding(16.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
    @Composable
    private fun PreferenceToggle(sharedPreferences: SharedPreferences, key: String, text: String) {
        val realKey = "debug_$key"
        var value by remember { mutableStateOf(sharedPreferences.getBoolean(realKey, false)) }
        val hapticFeedback = LocalHapticFeedback.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 55.dp)
                .clickable {
                    if (context.config.root.global.uiSettings.hapticFeedback.get()) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    value = !value
                    sharedPreferences
                        .edit() {
                            putBoolean(realKey, value)
                        }
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = text, modifier = Modifier.padding(end = 16.dp), fontSize = 14.sp)
            Switch(checked = value, onCheckedChange = {
                if (context.config.root.global.uiSettings.hapticFeedback.get()) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                value = it
                sharedPreferences.edit().putBoolean(realKey, it).apply()
            }, modifier = Modifier.padding(end = 26.dp))
        }
    }
    @Composable
    private fun RowAction(key: String, requireConfirmation: Boolean = false, action: () -> Unit) {
        var confirmationDialog by remember {
            mutableStateOf(false)
        }
        fun takeAction() {
            if (requireConfirmation) {
                confirmationDialog = true
            } else {
                action()
            }
        }
        if (requireConfirmation && confirmationDialog) {
            Dialog(onDismissRequest = { confirmationDialog = false }) {
                dialogs.ConfirmDialog(title = context.translation["manager.dialogs.action_confirm.title"], onConfirm = {
                    action()
                    confirmationDialog = false
                }, onDismiss = {
                    confirmationDialog = false
                })
            }
        }
        ShiftedRow(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 55.dp)
                .clickable {
                    takeAction()
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(text = context.translation["actions.$key.name"], fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
                context.translation.getOrNull("actions.$key.description")?.let { Text(text = it, fontSize = 12.sp, fontWeight = FontWeight.Light, lineHeight = 15.sp) }
            }
            IconButton(onClick = { takeAction() },
                modifier = Modifier.padding(end = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = context.translation.getOrNull("actions.$key.name"),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
    @Composable
    private fun ShiftedRow(
        modifier: Modifier = Modifier,
        horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
        verticalAlignment: Alignment.Vertical = Alignment.Top,
        content: @Composable RowScope.() -> Unit
    ) {
        Row(
            modifier = modifier.padding(start = 26.dp),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment
        ) { content(this) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override val content: @Composable (NavBackStackEntry) -> Unit = {
        val contextC = LocalContext.current
        val scope = rememberCoroutineScope()
        val themeMode by ThemePreferences.getThemeModeFlow(contextC).collectAsState(initial = ThemeMode.SYSTEM)
        var showThemeDialog by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // APP THEME (Popup)
            Spacer(Modifier.height(20.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { showThemeDialog = true },
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Brightness4,
                        contentDescription = translation["theme_icon_description"],
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(translation["app_theme_title"], fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text(when (themeMode) {
                            ThemeMode.SYSTEM -> translation["theme_mode_system"]
                            ThemeMode.LIGHT -> translation["theme_mode_light"]
                            ThemeMode.DARK -> translation["theme_mode_dark"]
                            ThemeMode.AMOLED -> translation["theme_mode_dark"] + " (AMOLED)"
                        }, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                    }
                }
            }
            if (showThemeDialog) {
                ThemeChooserDialog(
                    selected = themeMode,
                    onSelect = { mode ->
                        scope.launch {
                            ThemePreferences.setThemeMode(contextC, mode)
                        }
                    },
                    onDismiss = { showThemeDialog = false }
                )
            }
            Spacer(Modifier.height(20.dp))

            RowTitle(title = translation["actions_title"])
            EnumAction.entries.forEach { enumAction ->
                RowAction(key = enumAction.key) {
                    context.launchActionIntent(enumAction)
                }
            }
            RowAction(key = "regen_mappings") {
                context.checkForRequirements(Requirements.MAPPINGS)
            }
            RowAction(key = "change_language") {
                context.checkForRequirements(Requirements.LANGUAGE)
            }

            RowTitle(title = translation["ui_settings_title"])
            ShiftedRow {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 55.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = translation["haptic_feedback_label"])
                    var hapticFeedbackEnabled by remember { mutableStateOf(context.config.root.global.uiSettings.hapticFeedback.getNullable() ?: true) }
                    val hapticFeedback = LocalHapticFeedback.current
                    Switch(
                        checked = hapticFeedbackEnabled,
                        onCheckedChange = {
                            if (it) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            hapticFeedbackEnabled = it
                            context.config.root.global.uiSettings.hapticFeedback.set(it)
                            context.config.writeConfig()
                        },
                        modifier = Modifier.padding(end = 26.dp)
                    )
                }
            }

            RowTitle(title = translation["updates_title"])
            ShiftedRow {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    var autoUpdateCheck by remember { mutableStateOf(context.config.root.global.updateSettings.autoUpdateCheck.getNullable() ?: true) }
                    var selectedFrequency by remember { mutableStateOf(context.config.root.global.updateSettings.updateCheckFrequency.getNullable() ?: "weekly") }
                    var frequencyMenuExpanded by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 55.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = translation["auto_update_check"])
                            if (autoUpdateCheck) {
                                Text(
                                    text = translation["update_check_frequency_" + selectedFrequency],
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Light
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                IconButton(
                                    onClick = { frequencyMenuExpanded = true },
                                    enabled = autoUpdateCheck,
                                    modifier = Modifier.alpha(if (autoUpdateCheck) 1f else 0f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = translation["update_check_frequency"]
                                    )
                                }
                                if (autoUpdateCheck) {
                                    DropdownMenu(
                                        expanded = frequencyMenuExpanded,
                                        onDismissRequest = { frequencyMenuExpanded = false }
                                    ) {
                                        val frequencies = remember { listOf("daily", "weekly", "monthly") }
                                        frequencies.forEach { frequency ->
                                            DropdownMenuItem(
                                                text = { Text(text = translation["update_check_frequency_" + frequency]) },
                                                onClick = {
                                                    selectedFrequency = frequency
                                                    context.config.root.global.updateSettings.updateCheckFrequency.set(frequency)
                                                    context.config.writeConfig()
                                                    scheduleUpdateCheck()
                                                    frequencyMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            val hapticFeedback = LocalHapticFeedback.current
                            Switch(
                                checked = autoUpdateCheck,
                                onCheckedChange = {
                                    if (context.config.root.global.uiSettings.hapticFeedback.get()) {
                                         hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    autoUpdateCheck = it
                                    context.config.root.global.updateSettings.autoUpdateCheck.set(it)
                                    if (it && context.config.root.global.updateSettings.updateCheckFrequency.getNullable() == null) {
                                        context.config.root.global.updateSettings.updateCheckFrequency.set("weekly")
                                    }
                                    context.config.writeConfig()
                                    scheduleUpdateCheck()
                                },
                                modifier = Modifier.padding(end = 26.dp)
                            )
                        }
                    }
                }
            }

            RowTitle(title = translation["message_logger_title"])
            ShiftedRow {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    var storedMessagesCount by rememberAsyncMutableState(defaultValue = 0) {
                        context.messageLogger.getStoredMessageCount()
                    }
                    var storedStoriesCount by rememberAsyncMutableState(defaultValue = 0) {
                        context.messageLogger.getStoredStoriesCount()
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(5.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                translation.format("message_logger_summary",
                                "messageCount" to storedMessagesCount.toString(),
                                "storyCount" to storedStoriesCount.toString()
                            ), maxLines = 2)
                        }
                        Button(onClick = {
                            runCatching {
                                activityLauncherHelper.saveFile("message_logger.db", "application/octet-stream") { uri ->
                                    context.androidContext.contentResolver.openOutputStream(uri.toUri())?.use { outputStream ->
                                        context.messageLogger.databaseFile.inputStream().use { inputStream ->
                                            inputStream.copyTo(outputStream)
                                        }
                                    }
                                }
                            }.onFailure {
                                context.log.error("Failed to export database", it)
                                context.longToast(translation.format("export_database_failed_toast", "message" to (it.localizedMessage ?: "")))
                            }
                        }) {
                            Text(text = translation["export_button"])
                        }
                        Button(onClick = {
                            runCatching {
                                activityLauncherHelper.openFile("application/octet-stream") { uri ->
                                    val tempFile = File(context.androidContext.cacheDir, "view_message_logger.db")
                                    context.androidContext.contentResolver.openInputStream(uri.toUri())?.use { inputStream ->
                                        FileOutputStream(tempFile).use { outputStream ->
                                            inputStream.copyTo(outputStream)
                                        }
                                    }
                                    routes.viewLoggerHistory.navigate {
                                        put("uri", URLEncoder.encode(tempFile.toUri().toString(), "UTF-8"))
                                    }
                                }
                            }.onFailure {
                                context.log.error("Failed to open file", it)
                                context.longToast("Failed to open file! ${it.localizedMessage}")
                            }
                        }) {
                            Text(text = translation["view_button"])
                        }
                        Button(onClick = {
                            runCatching {
                                context.messageLogger.purgeAll()
                                storedMessagesCount = 0
                                storedStoriesCount = 0
                            }.onFailure {
                                context.log.error("Failed to clear messages", it)
                                context.longToast(translation.format("clear_messages_failed_toast", "message" to (it.localizedMessage ?: "")))
                            }.onSuccess {
                                context.shortToast(translation["success_toast"])
                            }
                        }) {
                            Text(text = translation["clear_button"])
                        }
                    }
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(5.dp),
                        onClick = {
                            routes.loggerHistory.navigate()
                        }
                    ) {
                        Text(translation["view_logger_history_button"])
                    }
                }
            }

            RowTitle(title = translation["friend_notes_title"])
            ShiftedRow {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(5.dp)
                    ) {
                        Text(
                            text = translation["friend_notes_description"],
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                runCatching {
                                    val notes = context.database.getAllScopeNotes()
                                    if (notes.isEmpty()) {
                                        context.shortToast(translation["friend_notes_no_notes_to_backup"])
                                        return@runCatching
                                    }
                                    val json = context.gson.toJson(notes)
                                    activityLauncherHelper.saveFile("friend_notes_backup.json", "application/json") { uri ->
                                        context.androidContext.contentResolver.openOutputStream(uri.toUri())?.use {
                                            it.write(json.toByteArray())
                                        }
                                        context.shortToast(translation["friend_notes_backup_success"])
                                    }
                                }.onFailure {
                                    context.log.error("Failed to backup notes", it)
                                    context.longToast(translation.format("friend_notes_backup_failure", "error" to (it.localizedMessage ?: "")))
                                }
                            }) {
                                Text(text = translation["backup_button"])
                            }
                            Button(onClick = {
                                runCatching {
                                    activityLauncherHelper.openFile("application/json") { uri ->
                                        context.androidContext.contentResolver.openInputStream(uri.toUri())?.use {
                                            val json = it.reader().readText()
                                            val notes = context.gson.fromJson<Map<String, String>>(json, object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type)
                                            context.database.setAllScopeNotes(notes)
                                            context.shortToast(translation["friend_notes_restore_success"])
                                        }
                                    }
                                }.onFailure {
                                    context.log.error("Failed to restore notes", it)
                                    context.longToast(translation.format("friend_notes_restore_failure", "error" to (it.localizedMessage ?: "")))
                                }
                            }) {
                                Text(text = translation["restore_button"])
                            }
                        }
                    }
                }
            }

            RowTitle(title = translation["debug_title"])
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var selectedFileType by remember { mutableStateOf(InternalFileHandleType.entries.first()) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 26.dp)
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.fillMaxWidth(0.7f)
                    ) {
                        TextField(
                            value = translation.getOrNull("debug_file_${selectedFileType.name.lowercase()}") ?: selectedFileType.fileName,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            InternalFileHandleType.entries.forEach { fileType ->
                                DropdownMenuItem(onClick = {
                                    expanded = false
                                    selectedFileType = fileType
                                }, text = {
                                    Text(text = translation.getOrNull("debug_file_${fileType.name.lowercase()}") ?: fileType.fileName)
                                })
                            }
                        }
                    }
                }
                Button(onClick = {
                    runCatching {
                        context.coroutineScope.launch {
                            selectedFileType.resolve(context.androidContext).delete()
                        }
                    }.onFailure {
                        context.log.error("Failed to clear file", it)
                        context.longToast(translation.format("clear_file_failed_toast", "message" to (it.localizedMessage ?: "")))
                    }.onSuccess {
                        context.shortToast(translation["success_toast"])
                    }
                }) {
                    Text(translation["clear_button"])
                }
            }
            ShiftedRow {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PreferenceToggle(context.sharedPreferences, key = "test_mode", text = translation["test_mode_label"])
                    PreferenceToggle(context.sharedPreferences, key = "disable_feature_loading", text = translation["disable_feature_loading_label"])
                    PreferenceToggle(context.sharedPreferences, key = "disable_mapper", text = translation["disable_auto_mapper_label"])
                    PreferenceToggle(context.sharedPreferences, key = "disable_bypass_indicator", text = translation["disable_bypass_indicator_label"])
                }
            }
            Spacer(modifier = Modifier.height(routes.bottomPadding))
        }
    }
}
