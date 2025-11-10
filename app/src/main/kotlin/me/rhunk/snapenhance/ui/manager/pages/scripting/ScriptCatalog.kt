@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package me.rhunk.snapenhance.ui.manager.pages.scripting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.*
import me.rhunk.snapenhance.common.util.ktx.openLink
import me.rhunk.snapenhance.storage.getRepositories
import okhttp3.OkHttpClient
import okhttp3.Request

data class ScriptRepoManifest(
    val scripts: List<ScriptRepoEntry>
)
data class ScriptRepoEntry(
    val name: String,
    val author: String? = null,
    val description: String? = null,
    val version: String? = null,
    val filepath: String
)

@Composable
fun ScriptCatalog(root: ScriptingRootSection) {
    val context = root.context
    val translation = remember { context.translation.getCategory("manager.scripting.catalog") }
    val coroutineScope = rememberCoroutineScope()
    val okHttpClient = remember { OkHttpClient() }
    val gson = remember { context.gson }

    var repositories by remember { mutableStateOf<List<String>>(emptyList()) }
    var repoIndexes by remember { mutableStateOf<Map<String, ScriptRepoManifest>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }

    fun refreshIndexes() {
        coroutineScope.launch(Dispatchers.IO) {
            isLoading = true
            val repos = context.database.getRepositories("script")
            withContext(Dispatchers.Main) {
                repositories = repos
            }
            
            if (repos.isNotEmpty()) {
                val newIndexes = mutableMapOf<String, ScriptRepoManifest>()
                repos.forEach { repoRoot ->
                    val indexUrl = if (repoRoot.endsWith("/")) "${repoRoot}index.json" else "$repoRoot/index.json"
                    try {
                        val req = Request.Builder().url(indexUrl).build()
                        okHttpClient.newCall(req).execute().use { response ->
                            if (response.isSuccessful) {
                                response.body?.charStream()?.let { reader ->
                                    val parsed = gson.fromJson(reader, ScriptRepoManifest::class.java)
                                    if (!parsed.scripts.isNullOrEmpty()) {
                                        newIndexes[repoRoot] = parsed
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                withContext(Dispatchers.Main) {
                    repoIndexes = newIndexes
                    isLoading = false
                }
            } else {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) { refreshIndexes() }

    val allScripts = repoIndexes.entries.flatMap { (repoUrl, manifest) ->
        manifest.scripts.map { repoUrl to it }
    }

    suspend fun isScriptInstalled(scriptName: String): Boolean {
        return try {
            val installedScripts = context.scriptManager.getSyncedModules()
            installedScripts.any { it.name.equals(scriptName, ignoreCase = true) }
        } catch (e: Exception) {
            false
        }
    }

    fun downloadScript(repoUrl: String, entry: ScriptRepoEntry) {
        coroutineScope.launch(Dispatchers.IO) {
            if (isScriptInstalled(entry.name)) {
                withContext(Dispatchers.Main) {
                    context.shortToast(translation["script_already_installed"])
                }
                return@launch
            }

            val rawUrl = if (repoUrl.endsWith("/")) repoUrl + entry.filepath else repoUrl + "/" + entry.filepath

            if (root.isScriptInstalledByUrl(rawUrl)) {
                withContext(Dispatchers.Main) {
                    context.shortToast(translation["script_already_installed"])
                }
                return@launch
            }

            try {
                val req = Request.Builder().url(rawUrl).build()
                okHttpClient.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) { context.shortToast(translation.format("download_failed", "code" to response.code.toString())) }
                        return@use
                    }
                    val content = response.body?.bytes()
                    if (content != null) {
                        val folder = context.scriptManager.getScriptsFolder()
                        if (folder != null) {
                            val file = folder.createFile("application/javascript", "${entry.name}.js")
                            if (file != null) {
                                context.androidContext.contentResolver.openOutputStream(file.uri)?.use { output ->
                                    output.write(content)
                                }
                                withContext(Dispatchers.Main) {
                                    context.shortToast(translation["script_downloaded"])
                                    root.reloadDispatcher.dispatch()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    context.shortToast(translation["could_not_create_file"])
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                context.shortToast(translation["no_scripts_folder_selected"])
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    context.shortToast(translation.format("error", "message" to (e.localizedMessage ?: "Unknown")))
                }
            }
        }
    }

    if (repositories.isEmpty() && !isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = translation["no_repos_added"],
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = translation["repo_list_info"],
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = translation["link_text"],
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            context.androidContext.openLink(
                                "https://github.com/particle-box/PurrfectSnap/blob/dev/app/src/main/kotlin/me/rhunk/snapenhance/ui/manager/pages/scripting/ScriptRepos.md"
                            )
                        }
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp + root.routes.bottomPadding)
        ) {
            item {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (allScripts.isEmpty() && repositories.isNotEmpty()) {
                    Text(
                        text = translation["no_scripts_available"],
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(allScripts) { (repoUrl, entry) ->
                var isDownloading by remember { mutableStateOf(false) }
                var isAlreadyInstalled by remember { mutableStateOf(false) }

                LaunchedEffect(entry) {
                    isAlreadyInstalled = isScriptInstalled(entry.name)
                }

                ElevatedCard(Modifier.padding(bottom = 8.dp).animateContentSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Code, null, Modifier.padding(end = 12.dp)
                        )
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = entry.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Bold
                                )
                                entry.author?.let {
                                    Text(
                                        text = translation.format("by_author", "author" to it),
                                        maxLines = 1,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Light,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            entry.description?.let {
                                Text(
                                    text = it,
                                    fontSize = 12.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = translation.format("version", "version" to (entry.version ?: "N/A")),
                                fontWeight = FontWeight.Light,
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            enabled = !isDownloading && !isAlreadyInstalled,
                            onClick = {
                                isDownloading = true
                                downloadScript(repoUrl, entry)
                                coroutineScope.launch {
                                    delay(1000)
                                    isDownloading = false
                                    isAlreadyInstalled = isScriptInstalled(entry.name)
                                }
                            }
                        ) {
                            when {
                                isAlreadyInstalled -> Text(translation["installed_button"])
                                isDownloading -> CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                else -> Text(translation["download_button"])
                            }
                        }
                    }
                }
            }
        }
    }
}
