package me.rhunk.snapenhance.ui.manager.pages.scripting

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import me.rhunk.snapenhance.common.scripting.type.ModuleInfo
import me.rhunk.snapenhance.common.scripting.ui.EnumScriptInterface
import me.rhunk.snapenhance.common.scripting.ui.InterfaceManager
import me.rhunk.snapenhance.common.scripting.ui.ScriptInterface
import me.rhunk.snapenhance.common.ui.AsyncUpdateDispatcher
import me.rhunk.snapenhance.common.ui.TopBarActionButton
import me.rhunk.snapenhance.common.ui.rememberAsyncMutableState
import me.rhunk.snapenhance.common.ui.rememberAsyncUpdateDispatcher
import me.rhunk.snapenhance.common.util.ktx.getUrlFromClipboard
import me.rhunk.snapenhance.common.util.ktx.openLink
import me.rhunk.snapenhance.storage.isScriptEnabled
import me.rhunk.snapenhance.storage.setScriptEnabled
import me.rhunk.snapenhance.ui.manager.Routes
import me.rhunk.snapenhance.ui.util.ActivityLauncherHelper
import me.rhunk.snapenhance.ui.util.Dialog
import me.rhunk.snapenhance.ui.util.chooseFolder
import me.rhunk.snapenhance.ui.util.pullrefresh.PullRefreshIndicator
import me.rhunk.snapenhance.ui.util.pullrefresh.pullRefresh
import me.rhunk.snapenhance.ui.util.pullrefresh.rememberPullRefreshState
import java.io.File

class ScriptingRootSection : Routes.Route() {
    override val translation by lazy { context.translation.getCategory("manager.scripting") }
    private lateinit var activityLauncherHelper: ActivityLauncherHelper
    val reloadDispatcher = AsyncUpdateDispatcher(updateOnFirstComposition = false)
    private var selectedTab by mutableStateOf(0)

    override val init: () -> Unit = {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
    }

    suspend fun isScriptInstalledByUrl(scriptUrl: String): Boolean {
        return try {
            val installedScripts = context.scriptManager.getSyncedModules()
            installedScripts.any { module ->
                module.updateUrl?.equals(scriptUrl, ignoreCase = true) == true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun downloadScript(scriptUrl: String, onComplete: () -> Unit) {
        context.coroutineScope.launch {
            if (isScriptInstalledByUrl(scriptUrl)) {
                context.shortToast(translation["script_already_installed"])
                return@launch
            }

            runCatching {
                context.shortToast(translation["downloading_script"])
                val moduleInfo = context.scriptManager.importFromUrl(scriptUrl)
                context.shortToast(translation.format("script_downloaded", "name" to moduleInfo.name))
                reloadDispatcher.dispatch()
                onComplete()
            }.onFailure {
                context.log.error("Failed to download script", it)
                context.shortToast(translation["download_script_failed"])
            }
        }
    }

    @Composable
    private fun ImportRemoteScript(
        dismiss: () -> Unit
    ) {
        Dialog(onDismissRequest = dismiss) {
            var url by remember { mutableStateOf("") }
            val focusRequester = remember { FocusRequester() }
            var isLoading by remember { mutableStateOf(false) }
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = translation["import_script_from_url_title"],
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(8.dp),
                    )
                    Text(
                        text = translation["import_script_warning"],
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Light,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(8.dp),
                        textAlign = TextAlign.Center,
                    )
                    TextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(text = translation["enter_url_label"]) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onGloballyPositioned { focusRequester.requestFocus() }
                    )
                    LaunchedEffect(Unit) {
                        context.androidContext.getUrlFromClipboard()?.let { url = it }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        enabled = url.isNotBlank(),
                        onClick = {
                            isLoading = true
                            context.coroutineScope.launch {
                                runCatching {
                                    if (isScriptInstalledByUrl(url)) {
                                        context.shortToast(translation["script_already_installed"])
                                        withContext(Dispatchers.Main) {
                                            dismiss()
                                        }
                                        return@launch
                                    }

                                    val moduleInfo = context.scriptManager.importFromUrl(url)
                                    context.shortToast(translation.format("script_imported", "name" to moduleInfo.name))
                                    reloadDispatcher.dispatch()
                                    withContext(Dispatchers.Main) {
                                        dismiss()
                                    }
                                    return@launch
                                }.onFailure {
                                    context.log.error("Failed to import script", it)
                                    context.shortToast(translation.format("import_failed", "message" to (it.message ?: "Unknown")))
                                }
                                isLoading = false
                            }
                        },
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(30.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(text = translation["import_button"])
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ModuleActions(
        script: ModuleInfo,
        canUpdate: Boolean,
        dismiss: () -> Unit
    ) {
        Dialog(onDismissRequest = dismiss) {
            ElevatedCard(modifier = Modifier.fillMaxWidth().padding(2.dp)) {
                val actions = remember {
                    mutableMapOf<Pair<String, ImageVector>, suspend () -> Unit>().apply {
                        if (canUpdate) {
                            put(translation["update_module_button"] to Icons.Default.Download) {
                                dismiss()
                                context.shortToast(translation.format("updating_script", "name" to script.name))
                                runCatching {
                                    val modulePath = context.scriptManager.getModulePath(script.name) ?: throw Exception(translation["module_not_found"])
                                    context.scriptManager.unloadScript(modulePath)
                                    val moduleInfo = context.scriptManager.importFromUrl(script.updateUrl!!, filepath = modulePath)
                                    context.shortToast(translation.format("updated_script", "name" to script.name, "version" to moduleInfo.version))
                                    context.database.setScriptEnabled(script.name, false)
                                    withContext(context.database.executor.asCoroutineDispatcher()) {
                                        reloadDispatcher.dispatch()
                                    }
                                }.onFailure {
                                    context.log.error("Failed to update module", it)
                                    context.shortToast(translation["update_module_failed"])
                                }
                            }
                        }
                        put(translation["edit_module_button"] to Icons.Default.Edit) {
                            runCatching {
                                val modulePath = context.scriptManager.getModulePath(script.name)!!
                                context.androidContext.startActivity(
                                    Intent(Intent.ACTION_VIEW).apply {
                                        data = context.scriptManager.getScriptsFolder()!!.findFile(modulePath)!!.uri
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    }
                                )
                                dismiss()
                            }.onFailure {
                                context.log.error("Failed to open module file", it)
                                context.shortToast(translation["open_module_failed"])
                            }
                        }
                        put(translation["clear_module_data_button"] to Icons.Default.Save) {
                            runCatching {
                                context.scriptManager.getModuleDataFolder(script.name).deleteRecursively()
                                context.shortToast(translation["module_data_cleared"])
                                dismiss()
                            }.onFailure {
                                context.log.error("Failed to clear module data", it)
                                context.shortToast(translation["clear_module_data_failed"])
                            }
                        }
                        put(translation["delete_module_button"] to Icons.Default.DeleteOutline) {
                            context.scriptManager.apply {
                                runCatching {
                                    val modulePath = getModulePath(script.name)!!
                                    unloadScript(modulePath)
                                    getScriptsFolder()?.findFile(modulePath)?.delete()
                                    reloadDispatcher.dispatch()
                                    context.shortToast(translation.format("deleted_script", "name" to script.name))
                                    dismiss()
                                }.onFailure {
                                    context.log.error("Failed to delete module", it)
                                    context.shortToast(translation["delete_module_failed"])
                                }
                            }
                        }
                    }.toMap()
                }
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Text(
                            text = translation["actions_title"],
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                    items(actions.size) { index ->
                        val action = actions.entries.elementAt(index)
                        ListItem(
                            modifier = Modifier
                                .clickable { context.coroutineScope.launch { action.value(); dismiss() } }
                                .fillMaxWidth(),
                            leadingContent = {
                                Icon(action.key.second, action.key.first)
                            },
                            headlineContent = { Text(action.key.first) }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ModuleItem(script: ModuleInfo) {
        var enabled by rememberAsyncMutableState(defaultValue = false, keys = arrayOf(script)) {
            context.database.isScriptEnabled(script.name)
        }
        var openSettings by remember(script) { mutableStateOf(false) }
        var openActions by remember { mutableStateOf(false) }

        val dispatcher = rememberAsyncUpdateDispatcher()
        val reloadCallback = remember { suspend { dispatcher.dispatch() } }
        val latestUpdate by rememberAsyncMutableState(defaultValue = null, updateDispatcher = dispatcher, keys = arrayOf(script)) {
            context.scriptManager.checkForUpdate(script)
        }

        LaunchedEffect(Unit) {
            reloadDispatcher.addCallback(reloadCallback)
        }
        DisposableEffect(Unit) {
            onDispose { reloadDispatcher.removeCallback(reloadCallback) }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            elevation = CardDefaults.cardElevation()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (enabled) openSettings = !openSettings }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (enabled) {
                    Icon(
                        imageVector = if (openSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp).size(32.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Text(text = script.displayName ?: script.name, fontSize = 20.sp)
                    Text(text = script.description ?: translation["no_description"], fontSize = 14.sp)
                    latestUpdate?.let {
                        Text(
                            text = translation.format("update_available", "version" to it.version),
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { openActions = !openActions }) {
                    Icon(Icons.Default.Build, translation["actions_button"])
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { isChecked ->
                        openSettings = false
                        context.coroutineScope.launch(Dispatchers.IO) {
                            runCatching {
                                val modulePath = context.scriptManager.getModulePath(script.name)!!
                                context.scriptManager.unloadScript(modulePath)
                                if (isChecked) {
                                    context.scriptManager.loadScript(modulePath)
                                    context.scriptManager.runtime.getModuleByName(script.name)
                                        ?.callFunction("module.onSnapEnhanceLoad")
                                    context.shortToast(translation.format("loaded_script", "name" to script.name))
                                } else {
                                    context.shortToast(translation.format("unloaded_script", "name" to script.name))
                                }
                                context.database.setScriptEnabled(script.name, isChecked)
                                withContext(Dispatchers.Main) { enabled = isChecked }
                            }.onFailure { throwable ->
                                withContext(Dispatchers.Main) { enabled = !isChecked }
                                context.log.error("Failed to ${if (isChecked) "enable" else "disable"} script", throwable)
                                context.shortToast(translation.format(if (isChecked) "enable_script_failed" else "disable_script_failed"))
                            }
                        }
                    }
                )
            }
            if (openSettings) {
                ScriptSettings(script)
            }
        }
        if (openActions) {
            ModuleActions(script = script, canUpdate = latestUpdate != null) { openActions = false }
        }
    }

    override val floatingActionButton: @Composable () -> Unit = {
        val tab = selectedTab
        var showImportDialog by remember { mutableStateOf(false) }
        var showToast by remember { mutableStateOf(false) }
        val scriptingFolder = context.scriptManager.getScriptsFolder()

        if (showImportDialog) {
            ImportRemoteScript { showImportDialog = false }
        }
        if (showToast) {
            LaunchedEffect(Unit) {
                context.shortToast(translation["select_scripts_folder_toast"])
                showToast = false
            }
        }

        if (tab == 1) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                ExtendedFloatingActionButton(
                    onClick = { routes.manageScriptRepos.navigate() },
                    icon = { Icon(Icons.Default.Public, contentDescription = null) },
                    text = { Text(translation["manage_repos_button"]) }
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (scriptingFolder == null) {
                            showToast = true
                        } else {
                            showImportDialog = true
                        }
                    },
                    icon = { Icon(imageVector = Icons.Default.Link, contentDescription = translation["import_from_url_button"]) },
                    text = { Text(text = translation["import_from_url_button"]) }
                )
                ExtendedFloatingActionButton(
                    onClick = {
                        if (scriptingFolder == null) {
                            showToast = true
                        } else {
                            scriptingFolder.let {
                                context.androidContext.openLink(it.uri.toString())
                            }
                        }
                    },
                    icon = { Icon(imageVector = Icons.Default.FolderOpen, contentDescription = translation["open_scripts_folder_button"]) },
                    text = { Text(text = translation["open_scripts_folder_button"]) }
                )
            }
        }
    }

    @Composable
    fun ScriptSettings(script: ModuleInfo) {
        val settingsInterface = remember {
            val module =
                context.scriptManager.runtime.getModuleByName(script.name) ?: return@remember null
            (module.getBinding(InterfaceManager::class))?.buildInterface(EnumScriptInterface.SETTINGS)
        }
        if (settingsInterface == null) {
            Text(
                text = translation["no_settings_for_module"],
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            ScriptInterface(interfaceBuilder = settingsInterface)
        }
    }

    override val content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit = {
        val scriptingFolder by rememberAsyncMutableState(
            defaultValue = null,
            updateDispatcher = reloadDispatcher
        ) { context.scriptManager.getScriptsFolder() }
        val tab = selectedTab
        val tabTitles = listOf(translation["installed_scripts_tab"], translation["catalog_tab"])

        Column(Modifier.fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                tabTitles.forEachIndexed { i, text ->
                    val shape = when (i) {
                        0 -> RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                        tabTitles.lastIndex -> RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
                        else -> RoundedCornerShape(0.dp)
                    }
                    SegmentedButton(
                        selected = tab == i,
                        onClick = {
                            if (i == 1 && scriptingFolder == null) {
                                context.shortToast(translation["select_scripts_folder_toast"])
                            } else {
                                selectedTab = i
                            }
                        },
                        shape = shape,
                        modifier = Modifier.weight(1f),
                        icon = {},
                        label = { Text(text) }
                    )
                }
            }
            when (tab) {
                0 -> {
                    val scriptModules by rememberAsyncMutableState(
                        defaultValue = emptyList(),
                        updateDispatcher = reloadDispatcher
                    ) { context.scriptManager.sync(); context.scriptManager.getSyncedModules() }
                    val coroutineScope = rememberCoroutineScope()
                    var refreshing by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        refreshing = true
                        withContext(Dispatchers.IO) {
                            reloadDispatcher.dispatch()
                            refreshing = false
                        }
                    }
                    val pullRefreshState = rememberPullRefreshState(refreshing, onRefresh = {
                        refreshing = true
                        coroutineScope.launch(Dispatchers.IO) {
                            reloadDispatcher.dispatch()
                            refreshing = false
                        }
                    })

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState),
                            contentPadding = PaddingValues(bottom = routes.bottomPadding),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item {
                                if (scriptingFolder == null && !refreshing) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(320.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = translation["no_scripts_folder_selected_title"],
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(bottom = 16.dp)
                                            )
                                            Button(
                                                onClick = {
                                                    activityLauncherHelper.chooseFolder {
                                                        context.config.root.scripting.moduleFolder.set(it)
                                                        context.config.writeConfig()
                                                        coroutineScope.launch { reloadDispatcher.dispatch() }
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp)
                                            ) {
                                                Text(
                                                    text = translation["select_folder_button"],
                                                    fontSize = 18.sp
                                                )
                                            }
                                        }
                                    }
                                } else if (scriptModules.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(320.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = translation["no_scripts_found_title"],
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = translation["use_catalog_to_add_scripts"],
                                                style = MaterialTheme.typography.bodyLarge,
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            items(scriptModules.size, key = { scriptModules[it].hashCode() }) { index ->
                                ModuleItem(scriptModules[index])
                            }
                        }
                        PullRefreshIndicator(
                            refreshing = refreshing,
                            state = pullRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                    var scriptingWarning by remember {
                        mutableStateOf(context.sharedPreferences.run {
                            getBoolean("scripting_warning", true).also {
                                edit().putBoolean("scripting_warning", false).apply()
                            }
                        })
                    }
                    if (scriptingWarning) {
                        var timeout by remember { mutableIntStateOf(10) }
                        LaunchedEffect(Unit) {
                            while (timeout > 0) {
                                delay(1000)
                                timeout--
                            }
                        }
                        AlertDialog(onDismissRequest = {
                            if (timeout == 0) scriptingWarning = false
                        }, title = {
                            Text(text = context.translation["manager.dialogs.scripting_warning.title"])
                        }, text = {
                            Text(text = context.translation["manager.dialogs.scripting_warning.content"])
                        }, confirmButton = {
                            TextButton(
                                onClick = { scriptingWarning = false },
                                enabled = timeout == 0
                            ) {
                                Text(text = translation.format("ok_button_timeout", "timeout" to timeout.toString()))
                            }
                        })
                    }
                }
                1 -> {
                    ScriptCatalog(this@ScriptingRootSection)
                }
            }
        }
    }

    override val topBarActions: @Composable() (RowScope.() -> Unit) = {
        TopBarActionButton(
            onClick = {
                context.androidContext.openLink("https://github.com/SnapEnhance/scripting-docs")
            },
            icon = Icons.Default.CollectionsBookmark,
            text = translation["documentation_button"],
        )
    }
}
