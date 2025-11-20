package com.example.miniwechat

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.miniwechat.core.model.ChatMessage
import com.example.miniwechat.core.model.MemberProfile
import com.example.miniwechat.core.model.MessageStatus
import com.example.miniwechat.core.model.DiagnosticsEvent
import com.example.miniwechat.ui.ChatBubble
import com.example.miniwechat.ui.DiagnosticsBubble
import com.example.miniwechat.ui.state.DiagnosticsBubbleState
import com.example.miniwechat.ui.theme.MiniwechatTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatUiInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun longPressChatBubbleShowsCancel() {
        var cancelTriggered = false
        val message = ChatMessage(
            conversationId = "conversation-test",
            senderId = "self",
            content = "Hello",
            status = MessageStatus.QUEUED
        )
        composeRule.setContent {
            MiniwechatTheme {
                ChatBubble(
                    message = message,
                    isOwn = true,
                    profile = MemberProfile(memberId = "self", localNickname = "Self"),
                    showAvatar = true,
                    onCancel = { cancelTriggered = true }
                )
            }
        }
        composeRule.onNodeWithText("Hello").performTouchInput { longClick() }
        composeRule.onNodeWithText("Cancel send").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel send").performClick()
        assertTrue(cancelTriggered)
    }

    @Test
    fun diagnosticsBubbleDisplaysAndDismisses() {
        var dismissed = false
        val state = DiagnosticsBubbleState(
            isEnabled = true,
            latestEvent = DiagnosticsEvent(code = "nearby", message = "Connection failed"),
            isVisible = true
        )
        composeRule.setContent {
            MiniwechatTheme {
                DiagnosticsBubble(
                    state = state,
                    onDismiss = { dismissed = true }
                )
            }
        }
        composeRule.onNodeWithText("nearby").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Dismiss diagnostics").performClick()
        assertTrue(dismissed)
    }
}
