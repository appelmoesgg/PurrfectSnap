@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package me.rhunk.snapenhance.ui.manager.pages.tracker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.storage.getRepositories
import me.rhunk.snapenhance.storage.getTrackerRuleByName
import me.rhunk.snapenhance.ui.manager.Routes
import okhttp3.OkHttpClient
import okhttp3.Request

data class FriendTrackerRepoManifest(
    val rules: List<FriendTrackerRepoEntry>
)

data class FriendTrackerRepoEntry(
    val name: String,
    val author: String? = null,
    val description: String? = null,
    val path: String
)

@OptIn(ExperimentalMaterial3Api::class)
class FriendTrackerCatalog : Routes.Route() {
    override val translation by lazy { context.translation.getCategory("manager.friend_tracker_catalog") }

    @Composable
    private fun AvailableRulesTab() {
        val coroutineScope = rememberCoroutineScope()
        val okHttpClient = remember { OkHttpClient() }
        val gson = remember { context.gson }

        var repositories by remember { mutableStateOf<List<String>>(emptyList()) }
        var repoIndexes by remember { mutableStateOf<Map<String, FriendTrackerRepoManifest>>(emptyMap()) }
        var isLoading by remember { mutableStateOf(false) }

        // Ticks whenever a rule import happens so isImported values recompute
        var importTick by remember { mutableStateOf(0) }

        fun refreshIndexes() {
            coroutineScope.launch(Dispatchers.IO) {
                isLoading = true
                val repos = context.database.getRepositories("friend_tracker")
                withContext(Dispatchers.Main) {
                    repositories = repos
                }
                if (repos.isNotEmpty()) {
                    val newIndexes = mutableMapOf<String, FriendTrackerRepoManifest>()
                    repos.forEach { repoRoot ->
                        val indexUrl = if (repoRoot.endsWith("/")) "${repoRoot}index.json" else "$repoRoot/index.json"
                        try {
                            val req = Request.Builder().url(indexUrl).build() // ktlint-disable indent_wrapped_argument
                            okHttpClient.newCall(req).execute().use { response ->
                                if (response.isSuccessful) {
                                    response.body?.charStream()?.let { reader ->
                                        val parsed = gson.fromJson(reader, FriendTrackerRepoManifest::class.java)
                                        if (parsed.rules.isNotEmpty()) {
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

        LaunchedEffect(Unit) {
            refreshIndexes()
            routes.onRuleImported = {
                importTick++
            }
        }

        val allRules = repoIndexes.entries.flatMap { (repoUrl, manifest) ->
            manifest.rules.map { repoUrl to it }
        }

        fun importRule(repoUrl: String, entry: FriendTrackerRepoEntry) {
            coroutineScope.launch(Dispatchers.IO) {
                val rawUrl = if (repoUrl.endsWith("/")) repoUrl + entry.path else repoUrl + "/" + entry.path
                try {
                    val req = Request.Builder().url(rawUrl).build() // ktlint-disable indent_wrapped_argument
                    okHttpClient.newCall(req).execute().use { response ->
                        if (!response.isSuccessful) {
                            withContext(Dispatchers.Main) { context.shortToast(translation.format("download_failed", "code" to response.code.toString())) }
                            return@use
                        }
                        val content = response.body?.string()
                        if (content != null) { // ktlint-disable no-multi-spaces
                            withContext(Dispatchers.Main) {
                                routes.friendTrackerConfigJsonForImport = content
                                routes.friendTrackerConfigImport.navigate()
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
                Text(
                    text = translation["no_repos_added"],
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp + routes.bottomPadding)
            ) {
                item {
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (allRules.isEmpty() && repositories.isNotEmpty()) {
                        Text(
                            text = translation["no_rules_available"],
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
                items(allRules) { (repoUrl, entry) ->
                    // Compute isImported using produceState so the suspend db call runs in a coroutine
                    val isImported by produceState(initialValue = false, key1 = entry.name, key2 = importTick) {
                        val exists = withContext(Dispatchers.IO) {
                            context.database.getTrackerRuleByName(entry.name) != null
                        }
                        value = exists
                    }

                    ElevatedCard(Modifier.padding(bottom = 8.dp).animateContentSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Rule, null, Modifier.padding(end = 12.dp)
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
                            }
                            Button(
                                onClick = {
                                    importRule(repoUrl, entry)
                                },
                                enabled = !isImported
                            ) {
                                Text(if (isImported) translation["imported_button"] else translation["import_button"])
                            }
                        }
                    }
                }
            }
        }
    }

    override val title: @Composable () -> Unit = { Text(translation["title"]) }
    override val topBarActions: @Composable RowScope.() -> Unit = {
        IconButton(onClick = { routes.manageFriendTrackerRepos.navigate() }) {
            Icon(Icons.Default.Public, contentDescription = translation["manage_repos_description"])
        }
    }
    override val content: @Composable (NavBackStackEntry) -> Unit = {
        AvailableRulesTab()
    }
}

