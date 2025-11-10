package me.rhunk.snapenhance.ui.setup.screens.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import me.rhunk.snapenhance.common.bridge.wrapper.LocaleWrapper
import me.rhunk.snapenhance.ui.setup.screens.SetupScreen
import me.rhunk.snapenhance.ui.util.ObservableMutableState
import me.rhunk.snapenhance.ui.util.Motion
import me.rhunk.snapenhance.ui.util.scaleOnPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import java.util.Locale

class PickLanguageScreen : SetupScreen() {
    private val availableLocales by lazy {
        LocaleWrapper.fetchAvailableLocales(context.androidContext)
    }
    private lateinit var selectedLocale: ObservableMutableState<String>
    private fun getLocaleDisplayName(locale: String): String {
        val displayLocale = when (locale) {
            "zh-Hans" -> Locale.forLanguageTag("zh-Hans")
            "zh_TW" -> Locale.TRADITIONAL_CHINESE
            else -> try {
                Locale.forLanguageTag(locale.replace('_', '-'))
            } catch (e: Exception) {
                Locale.getDefault()
            }
        }
        return displayLocale.getDisplayName(Locale.getDefault())
    }
    private fun reloadTranslation(selectedLocale: String) {
        context.translation.reload(selectedLocale, isSetup = true)
    }
    private fun setLocale(locale: String) {
        with(context) {
            config.locale = locale
            config.writeConfig()
            reloadTranslation(locale)
        }
    }
    override fun onLeave() {
        context.config.locale = selectedLocale.value
        context.config.writeConfig()
    }
    override fun init() {
        val deviceLocale = Locale.getDefault().toString()
        selectedLocale =
            ObservableMutableState(
                defaultValue = availableLocales.firstOrNull { locale -> locale == deviceLocale }
                    ?: LocaleWrapper.DEFAULT_LOCALE
            ) { _, newValue ->
                setLocale(newValue)
            }.also { reloadTranslation(it.value) }
    }
    @Composable
    override fun Content() {
        allowNext(true)
        DialogText(text = context.translation["setup.dialogs.select_language"])
        var isDialog by remember { mutableStateOf(false) }
        if (isDialog) {
            var visible by remember { mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(Unit) { visible = true }
            Dialog(onDismissRequest = { isDialog = false }) {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = Motion.tweenFloatSpec(180)) + scaleIn(animationSpec = Motion.tweenFloatSpec(200)),
                    exit = fadeOut(animationSpec = Motion.tweenFloatSpec(150)) + scaleOut(animationSpec = Motion.tweenFloatSpec(180))
                ) {
                    Surface(
                        modifier = Modifier
                            .padding(10.dp)
                            .fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        LazyColumn(
                            modifier = Modifier.scrollable(rememberScrollState(), orientation = Orientation.Vertical)
                        ) {
                            items(availableLocales) { locale ->
                                Box(
                                    modifier = Modifier
                                        .height(70.dp)
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedLocale.value = locale
                                            isDialog = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = remember(locale) { getLocaleDisplayName(locale) },
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Light,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .padding(top = 40.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val btnSrc = remember { MutableInteractionSource() }
            Button(
                onClick = { isDialog = true },
                interactionSource = btnSrc,
                modifier = Modifier.scaleOnPress(btnSrc)
            ) {
                Text(
                    text = remember(selectedLocale.value) { getLocaleDisplayName(selectedLocale.value) },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}
