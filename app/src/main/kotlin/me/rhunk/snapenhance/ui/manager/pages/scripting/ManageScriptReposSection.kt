package me.rhunk.snapenhance.ui.manager.pages.scripting

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.Error
import androidx.core.net.toUri
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.common.util.ktx.getUrlFromClipboard
import me.rhunk.snapenhance.storage.addRepo
import me.rhunk.snapenhance.storage.getRepositories
import me.rhunk.snapenhance.storage.removeRepo
import me.rhunk.snapenhance.ui.manager.Routes
import me.rhunk.snapenhance.ui.manager.components.AestheticDialog
import okhttp3.OkHttpClient

class ManageScriptReposSection : Routes.Route() {
    override val translation by lazy { context.translation.getCategory("manager.scripting.repos") }
    private val refreshTrigger = mutableStateOf(0)
    private val okHttpClient by lazy { OkHttpClient() }

    private fun extractRepoInfo(url: String): Pair<String, String> {
        if (url.contains("raw.githubusercontent.com")) {
            val parts = url.removePrefix("https://raw.githubusercontent.com/").split("/")
            if (parts.size >= 2) {
                return parts[1] to parts[0]
            }
        }
        return url.substringAfterLast("/").substringBeforeLast(".") to url.substringAfter("://").substringBefore("/")
    }

    override val floatingActionButton: @Composable () -> Unit = {
        var showAddDialog by remember { mutableStateOf(false) }
        var showErrorDialog by remember { mutableStateOf(false) }
        var errorDialogMessage by remember { mutableStateOf("") }

        if (showErrorDialog) {
            AestheticDialog(
                onDismissRequest = { showErrorDialog = false },
                title = translation["invalid_repo_title"],
                text = errorDialogMessage,
                icon = Icons.Default.Error,
                confirmButtonText = translation["button.ok"],
                onConfirm = { showErrorDialog = false }
            )
        }

        ExtendedFloatingActionButton(onClick = { showAddDialog = true }) {
            Text(translation["add_repo_button"])
        }

        if (showAddDialog) {
            val coroutineScope = rememberCoroutineScope { Dispatchers.IO }

            var url by remember { mutableStateOf("") }
            var loading by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text(translation["add_repo_dialog_title"]) },
                text = {
                    val focusRequester = remember { FocusRequester() }
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onGloballyPositioned { focusRequester.requestFocus() },
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(translation["repo_url_label"]) }
                    )
                    LaunchedEffect(Unit) {
                        context.androidContext.getUrlFromClipboard()?.let { url = it }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !loading && url.isNotBlank(),
                        onClick = {
                            loading = true
                            coroutineScope.launch {
                                runCatching {
                                    var modifiedUrl = url
                                    if (url.startsWith("https://github.com/")) {
                                        val splitUrl = modifiedUrl.removePrefix("https://github.com/").split("/")
                                        val repoName = splitUrl[0] + "/" + splitUrl[1]
                                        okHttpClient.newCall(
                                            okhttp3.Request.Builder().url("https://api.github.com/repos/$repoName").build()
                                        ).execute().use { response ->
                                            if (!response.isSuccessful) {
                                                throw Exception("Failed to fetch default branch: ${response.code}")
                                            }
                                            val json = response.body?.string() ?: throw Exception("Empty response")
                                            val defaultBranch = Regex("\"default_branch\":\"([^\"]+)\"").find(json)?.groupValues?.get(1)
                                                ?: throw Exception("No default_branch field")
                                            modifiedUrl = "https://raw.githubusercontent.com/$repoName/$defaultBranch/"
                                        }
                                    }

                                    val indexUrl = modifiedUrl.toUri().buildUpon().appendPath("index.json").build().toString()
                                    val request = okhttp3.Request.Builder().url(indexUrl).build()
                                    val isValid = okHttpClient.newCall(request).execute().use { response ->
                                        if (!response.isSuccessful) throw Exception("Failed to fetch index.json: ${response.code}")
                                        val indexJson = response.body?.string() ?: throw Exception("Empty index.json")
                                        JsonParser.parseString(indexJson).asJsonObject.has("scripts")
                                    }

                                    if (isValid) {
                                        context.database.addRepo("script", modifiedUrl)
                                        context.shortToast(translation["repo_added_toast"])
                                        showAddDialog = false
                                        refreshTrigger.value++
                                    } else {
                                        errorDialogMessage = translation["invalid_repo_error"]
                                        showErrorDialog = true
                                    }
                                }.onFailure {
                                    context.log.error("Failed to add repository", it)
                                    context.shortToast(translation.format("add_repo_failed_toast", "message" to (it.message ?: "Unknown")))
                                }
                                loading = false
                            }
                        }
                    ) {
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Text(translation["add_button"])
                        }
                    }
                }
            )
        }
    }

    override val content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit = {
        val repositories by remember(refreshTrigger.value) {
            mutableStateOf<List<String>>(runBlocking { context.database.getRepositories("script") })
        }

        if (repositories.isEmpty()) {
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
                contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp + routes.bottomPadding),
            ) {
                items(repositories) { url ->
                    val (repoName, author) = remember(url) { extractRepoInfo(url) }
                    
                    ElevatedCard(
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Public, 
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = repoName,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = author,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            var showRemoveDialog by remember { mutableStateOf(false) }

                            Button(
                                onClick = { showRemoveDialog = true }
                            ) {
                                Text(translation["remove_button"])
                            }

                            AnimatedVisibility(visible = showRemoveDialog) {
                                AlertDialog(
                                    onDismissRequest = { showRemoveDialog = false },
                                    title = { Text(translation["remove_repo_dialog_title"]) },
                                    text = { Text(translation["remove_repo_dialog_text"]) },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                context.database.removeRepo("script", url)
                                                showRemoveDialog = false
                                                refreshTrigger.value++
                                            }
                                        ) {
                                            Text(translation["remove_button"])
                                        }
                                    },
                                    dismissButton = {
                                        Button(onClick = { showRemoveDialog = false }) {
                                            Text(translation["button.cancel"])
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
