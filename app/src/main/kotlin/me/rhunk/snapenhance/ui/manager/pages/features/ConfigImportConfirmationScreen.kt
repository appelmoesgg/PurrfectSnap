package me.rhunk.snapenhance.ui.manager.pages.features

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.ui.manager.Routes
import org.json.JSONArray
import org.json.JSONObject

class ConfigImportConfirmationScreen : Routes.Route() {
    override val translation by lazy { context.translation.getCategory("manager.features.config_import") }

    private data class ImportedFeature(
        val category: String,
        val name: String,
        val key: String,
        val value: Any,
        val indentation: Int
    )

    private inner class ConfigParser {
        fun parse(configJson: String): Map<String, List<ImportedFeature>> {
            val featureList = mutableListOf<ImportedFeature>()
            val json = JSONObject(configJson)
            fun parseProperties(
                categoryKey: String,
                niceCategoryName: String,
                properties: JSONObject,
                prefix: String,
                indent: Int
            ) {
                for (key in properties.keys()) {
                    val value = properties.get(key)
                    val currentPrefix = if (prefix.isEmpty()) key else "$prefix.$key"
                    if (value is JSONObject && value.has("state") && value.has("properties")) {
                        val featureNameKey =
                            "features.properties.$categoryKey.properties.${currentPrefix.split('.')
                                .joinToString(".properties.")}.name"
                        val featureName = context.translation[featureNameKey] ?: key
                        featureList.add(
                            ImportedFeature(
                                niceCategoryName,
                                featureName,
                                key,
                                value.getBoolean("state"),
                                indent
                            )
                        )
                        parseProperties(
                            categoryKey,
                            niceCategoryName,
                            value.getJSONObject("properties"),
                            currentPrefix,
                            indent + 1
                        )
                    } else if (value is JSONObject && value.has("properties")) {
                        parseProperties(
                            categoryKey,
                            niceCategoryName,
                            value.getJSONObject("properties"),
                            currentPrefix,
                            indent
                        )
                    } else {
                        val featureNameKey =
                            "features.properties.$categoryKey.properties.${currentPrefix.split('.')
                                .joinToString(".properties.")}.name"
                        val featureName = context.translation[featureNameKey] ?: key
                        featureList.add(
                            ImportedFeature(
                                niceCategoryName,
                                featureName,
                                key,
                                value,
                                indent
                            )
                        )
                    }
                }
            }
            for (categoryKey in json.keys()) {
                val value = json.get(categoryKey)
                if (value is JSONObject) {
                    val niceCategoryName =
                        context.translation["features.properties.$categoryKey.name"]
                            ?: categoryKey.replaceFirstChar { it.uppercase() }
                    if (value.has("state") && !value.has("properties")) {
                        featureList.add(
                            ImportedFeature(
                                niceCategoryName,
                                translation["enable_feature"],
                                categoryKey,
                                value.getBoolean("state"),
                                0
                            )
                        )
                    } else if (value.has("properties")) {
                        parseProperties(
                            categoryKey,
                            niceCategoryName,
                            value.getJSONObject("properties"),
                            "",
                            0
                        )
                    }
                }
            }
            return featureList.groupBy { it.category }
        }

        fun parseValue(featureKey: String, value: Any): Any {
            fun innerParse(v: Any): String {
                if (v is String) {
                    val translationKey = "features.options.$featureKey.$v"
                    val translated = context.translation[translationKey]
                    return translated!!
                }
                return v.toString()
            }
            return when (value) {
                is Boolean -> if (value) translation["enabled"] else translation["disabled"]
                is JSONArray -> {
                    val list = mutableListOf<String>()
                    for (i in 0 until value.length()) {
                        list.add(innerParse(value.get(i)))
                    }
                    list
                }
                else -> innerParse(value)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override val content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit = {
        val parser = remember { ConfigParser() }
        val featuresByCategory = remember {
            routes.configJsonForImport?.let { parser.parse(it) } ?: emptyMap()
        }
        val expandedState = remember { mutableStateMapOf<String, Boolean>() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(translation["title"]) },
                    navigationIcon = {
                        IconButton(onClick = { routes.navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = translation["back_button_description"]
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            routes.configJsonForImport?.let {
                                runCatching {
                                    context.config.loadFromString(it)
                                }.onFailure { err ->
                                    context.longToast(
                                        context.translation.format(
                                            "config_import_failure_toast",
                                            "error" to (err.message ?: "Unknown error")
                                        )
                                    )
                                    // Return is not needed since last statement in lambda
                                }
                                context.shortToast(translation["config_imported_toast"])
                                context.coroutineScope.launch(Dispatchers.Main) {
                                    routes.features.navigateReload()
                                }
                            }
                        }) {
                            Text(translation["confirm_button"])
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp + routes.bottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(featuresByCategory.toList()) { (category, features) ->
                    val isExpanded = expandedState[category] ?: false
                    val rotationState by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedState[category] = !isExpanded },
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
                                IconButton(onClick = { expandedState[category] = !isExpanded }) {
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
                                    features.forEachIndexed { index, feature ->
                                        when (val parsedValue = parser.parseValue(feature.key, feature.value)) {
                                            is List<*> -> {
                                                Column(
                                                    modifier = Modifier
                                                        .padding(
                                                            start = (feature.indentation * 16).dp,
                                                            top = 4.dp,
                                                            bottom = 4.dp
                                                        )
                                                ) {
                                                    Text(
                                                        text = feature.name,
                                                        fontWeight = FontWeight.SemiBold,
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Column(modifier = Modifier.padding(start = 16.dp)) {
                                                        parsedValue.forEach { item ->
                                                            Row {
                                                                Text(
                                                                    text = "•",
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.padding(end = 8.dp)
                                                                )
                                                                Text(
                                                                    text = item.toString(),
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            is String -> {
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
                                                        text = parsedValue,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        textAlign = TextAlign.End,
                                                    )
                                                }
                                            }
                                        }
                                        // Always show divider only when there are more features to follow
                                        if (index < features.size - 1) {
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
}
