package me.rhunk.snapenhance.ui.manager.pages.tracker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.common.data.ExportedTrackerData
import me.rhunk.snapenhance.common.data.ExportType
import me.rhunk.snapenhance.storage.getTrackerRule
import me.rhunk.snapenhance.storage.getTrackerRulesDesc
import me.rhunk.snapenhance.ui.manager.Routes
import me.rhunk.snapenhance.ui.util.saveFile
import org.json.JSONArray

class FriendTrackerConfigExportScreen : Routes.Route() {
    override val translation by lazy { context.translation.getCategory("manager.friend_tracker_export") }

    @OptIn(ExperimentalMaterial3Api::class)
    override val content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit = { navBackStackEntry ->
        val ruleId = navBackStackEntry.arguments?.getString("rule_id")?.toIntOrNull()
        val parser = remember { TrackerConfigParser(this) }
        var trackerData by remember { mutableStateOf<ExportedTrackerData?>(null) }
        var featuresByCategory by remember { mutableStateOf<Map<String, List<ImportedFeature>>>(emptyMap()) }

        LaunchedEffect(Unit) {
            launch(Dispatchers.IO) {
                val data = if (ruleId != null) {
                    context.trackerDataManager.getExportedTrackerData(ruleId)
                } else {
                    context.trackerDataManager.getExportedTrackerData()
                }
                trackerData = data
                featuresByCategory = data?.let { parser.parse(context.gson.toJson(it)) } ?: emptyMap()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(translation["title"]) },
                    navigationIcon = {
                        IconButton(onClick = { routes.navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = translation["back_button_description"])
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            routes.activityLauncher.saveFile("friend_tracker_config.json", "application/json") { uri ->
                                runCatching {
                                    context.androidContext.contentResolver.openOutputStream(android.net.Uri.parse(uri))?.use {
                                        trackerData?.let { data ->
                                            context.gson.toJson(data).byteInputStream().copyTo(it)
                                            context.shortToast(translation["exported_toast"])
                                        }
                                    }
                                }.onFailure {
                                    context.longToast(translation.format("export_failed_toast", "message" to (it.message ?: "Unknown")))
                                }
                            }
                        }) {
                            Text(translation["save_button"])
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + routes.bottomPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(featuresByCategory.toList()) { (category, features) ->
                    var isExpanded by remember { mutableStateOf(ruleId != null) }
                    val rotationState by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "rotationState")

                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = category,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { isExpanded = !isExpanded }) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = translation["expand_button_description"],
                                        modifier = Modifier.graphicsLayer(rotationZ = rotationState)
                                    )
                                }
                            }
                            AnimatedVisibility(visible = isExpanded) {
                                Column {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    features.forEach { feature ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .padding(start = (feature.indentation * 16).dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = feature.name,
                                                modifier = Modifier.weight(1f),
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(
                                                text = parser.parseValue(feature.key, feature.value).toString(),
                                                color = MaterialTheme.colorScheme.primary,
                                                textAlign = TextAlign.End,
                                            )
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
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
