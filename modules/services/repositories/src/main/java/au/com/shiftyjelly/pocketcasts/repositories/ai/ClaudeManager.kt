package au.com.shiftyjelly.pocketcasts.repositories.ai

import au.com.shiftyjelly.pocketcasts.servers.anthropic.AnthropicMessage

// Shared entry point for all Claude features (summaries, chat, ad detection). Callers must
// treat a failure here as user-visible: the key may be missing, the opt-in disabled, or the
// request refused.
interface ClaudeManager {
    fun isConfigured(): Boolean

    suspend fun complete(
        system: String?,
        messages: List<AnthropicMessage>,
        maxTokens: Int,
    ): String

    companion object {
        const val MODEL = "claude-sonnet-5"

        // Bounds the transcript sent to Anthropic so a very long episode stays at a sane cost.
        const val MAX_TRANSCRIPT_CHARS = 400_000
    }
}

fun String.truncateForClaude(maxChars: Int = ClaudeManager.MAX_TRANSCRIPT_CHARS): String = if (length <= maxChars) this else take(maxChars)
