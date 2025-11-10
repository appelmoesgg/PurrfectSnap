package me.rhunk.snapenhance.ui.manager.pages.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import me.rhunk.snapenhance.common.bridge.wrapper.LocaleWrapper

@Composable
fun QuickActionsDialog(
    quickActions: Map<Pair<String, ImageVector>, Any>,
    selectedQuickActions: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
    translation: LocaleWrapper
) {
    val selected = remember { mutableStateListOf(*selectedQuickActions.toTypedArray()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = translation["manager.dialogs.quick_actions_dialog.title"],
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = translation["manager.dialogs.quick_actions_dialog.subtitle"],
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .fillMaxWidth()
                )
                quickActions.keys.forEach { (name, icon) ->
                    val isSelected = selected.contains(name)
                    ListItem(
                        modifier = Modifier
                            .clickable {
                                if (isSelected) selected.remove(name) else selected.add(name)
                            }
                            .fillMaxWidth(),
                        headlineContent = {
                            Text(
                                name,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = icon,
                                contentDescription = name,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) selected.add(name) else selected.remove(name)
                                }
                            )
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selected.toList()) },
                shape = MaterialTheme.shapes.medium
            ) {
                Text(translation["button.save"])
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(translation["button.cancel"])
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
