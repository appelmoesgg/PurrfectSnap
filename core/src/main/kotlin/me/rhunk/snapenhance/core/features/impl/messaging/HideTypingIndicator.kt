package me.rhunk.snapenhance.core.features.impl.messaging

import me.rhunk.snapenhance.common.data.MessagingRuleType
import me.rhunk.snapenhance.core.features.MessagingRuleFeature
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook

class HideTypingIndicator : MessagingRuleFeature("Hide Typing Indicator", MessagingRuleType.HIDE_TYPING_INDICATOR) {
    private val messaging: Messaging by lazy { context.feature(Messaging::class) }

    override fun init() {
        context.classCache.presenceSession.hook("processTypingActivity", HookStage.BEFORE, {
            messaging.openedConversationUUID?.toString()?.let { canUseRule(it) } ?: false
        }) {
            it.setResult(null)
        }
    }
}