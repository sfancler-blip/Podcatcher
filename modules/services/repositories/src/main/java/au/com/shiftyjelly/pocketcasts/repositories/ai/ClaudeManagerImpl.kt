package au.com.shiftyjelly.pocketcasts.repositories.ai

import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.servers.anthropic.AnthropicMessage
import au.com.shiftyjelly.pocketcasts.servers.anthropic.AnthropicMessagesRequest
import au.com.shiftyjelly.pocketcasts.servers.anthropic.AnthropicService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClaudeManagerImpl @Inject constructor(
    private val anthropicService: AnthropicService,
    private val settings: Settings,
) : ClaudeManager {
    override fun isConfigured(): Boolean {
        return settings.sendTranscriptsToAnthropic.value && settings.anthropicApiKey.value.isNotBlank()
    }

    override suspend fun complete(
        system: String?,
        messages: List<AnthropicMessage>,
        maxTokens: Int,
    ): String {
        check(settings.sendTranscriptsToAnthropic.value) {
            "Sending transcripts to Anthropic is disabled. Enable it in Settings → Claude AI."
        }
        val apiKey = settings.anthropicApiKey.value
        check(apiKey.isNotBlank()) {
            "No Anthropic API key is set. Add one in Settings → Claude AI."
        }
        val response = anthropicService.messages(
            apiKey = apiKey,
            request = AnthropicMessagesRequest(
                model = ClaudeManager.MODEL,
                maxTokens = maxTokens,
                system = system,
                messages = messages,
            ),
        )
        return checkNotNull(response.textOrNull()) {
            "Claude returned no text (stop reason: ${response.stopReason})"
        }
    }
}
