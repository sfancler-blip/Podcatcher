package au.com.shiftyjelly.pocketcasts.repositories.ai

import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.preferences.UserSetting
import au.com.shiftyjelly.pocketcasts.servers.anthropic.AnthropicContentBlock
import au.com.shiftyjelly.pocketcasts.servers.anthropic.AnthropicMessage
import au.com.shiftyjelly.pocketcasts.servers.anthropic.AnthropicMessagesRequest
import au.com.shiftyjelly.pocketcasts.servers.anthropic.AnthropicMessagesResponse
import au.com.shiftyjelly.pocketcasts.servers.anthropic.AnthropicService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ClaudeManagerImplTest {
    private val anthropicService = mock<AnthropicService>()

    private fun settings(apiKey: String = "sk-ant-test", optIn: Boolean = true) = mock<Settings> {
        on { anthropicApiKey } doReturn UserSetting.Mock(apiKey, mock())
        on { sendTranscriptsToAnthropic } doReturn UserSetting.Mock(optIn, mock())
    }

    @Test
    fun `is configured requires key and opt in`() {
        assertTrue(ClaudeManagerImpl(anthropicService, settings()).isConfigured())
        assertFalse(ClaudeManagerImpl(anthropicService, settings(apiKey = "")).isConfigured())
        assertFalse(ClaudeManagerImpl(anthropicService, settings(optIn = false)).isConfigured())
    }

    @Test
    fun `complete sends request with locked in model and returns text`() = runTest {
        whenever(anthropicService.messages(any(), any())).thenReturn(
            AnthropicMessagesResponse(content = listOf(AnthropicContentBlock(type = "text", text = "A reply"))),
        )
        val manager = ClaudeManagerImpl(anthropicService, settings())

        val reply = manager.complete(
            system = "Be helpful.",
            messages = listOf(AnthropicMessage(role = AnthropicMessage.ROLE_USER, content = "Hi")),
            maxTokens = 128,
        )

        assertEquals("A reply", reply)
        val keyCaptor = argumentCaptor<String>()
        val requestCaptor = argumentCaptor<AnthropicMessagesRequest>()
        verify(anthropicService).messages(keyCaptor.capture(), requestCaptor.capture())
        assertEquals("sk-ant-test", keyCaptor.firstValue)
        assertEquals(ClaudeManager.MODEL, requestCaptor.firstValue.model)
        assertEquals(128, requestCaptor.firstValue.maxTokens)
        assertEquals("Be helpful.", requestCaptor.firstValue.system)
    }

    @Test
    fun `complete fails without opt in`() = runTest {
        val manager = ClaudeManagerImpl(anthropicService, settings(optIn = false))

        val exception = runCatching {
            manager.complete(system = null, messages = emptyList(), maxTokens = 64)
        }.exceptionOrNull()

        assertTrue(exception is IllegalStateException)
    }

    @Test
    fun `complete fails without api key`() = runTest {
        val manager = ClaudeManagerImpl(anthropicService, settings(apiKey = " "))

        val exception = runCatching {
            manager.complete(system = null, messages = emptyList(), maxTokens = 64)
        }.exceptionOrNull()

        assertTrue(exception is IllegalStateException)
    }

    @Test
    fun `complete fails on refusal with no text`() = runTest {
        whenever(anthropicService.messages(any(), any())).thenReturn(
            AnthropicMessagesResponse(content = emptyList(), stopReason = "refusal"),
        )
        val manager = ClaudeManagerImpl(anthropicService, settings())

        val exception = runCatching {
            manager.complete(system = null, messages = emptyList(), maxTokens = 64)
        }.exceptionOrNull()

        assertTrue(exception is IllegalStateException)
        assertTrue(exception!!.message!!.contains("refusal"))
    }

    @Test
    fun `truncate for claude bounds long transcripts`() {
        val long = "a".repeat(ClaudeManager.MAX_TRANSCRIPT_CHARS + 10)
        assertEquals(ClaudeManager.MAX_TRANSCRIPT_CHARS, long.truncateForClaude().length)
        assertEquals("short", "short".truncateForClaude())
    }
}
