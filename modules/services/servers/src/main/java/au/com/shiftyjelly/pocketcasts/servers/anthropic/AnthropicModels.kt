package au.com.shiftyjelly.pocketcasts.servers.anthropic

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AnthropicMessagesRequest(
    @Json(name = "model") val model: String,
    @Json(name = "max_tokens") val maxTokens: Int,
    @Json(name = "system") val system: String? = null,
    @Json(name = "messages") val messages: List<AnthropicMessage>,
)

@JsonClass(generateAdapter = true)
data class AnthropicMessage(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String,
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

@JsonClass(generateAdapter = true)
data class AnthropicMessagesResponse(
    @Json(name = "id") val id: String? = null,
    @Json(name = "model") val model: String? = null,
    @Json(name = "stop_reason") val stopReason: String? = null,
    @Json(name = "content") val content: List<AnthropicContentBlock> = emptyList(),
    @Json(name = "usage") val usage: AnthropicUsage? = null,
) {
    fun textOrNull(): String? = content
        .filter { it.type == "text" }
        .mapNotNull { it.text }
        .joinToString(separator = "")
        .ifBlank { null }

    companion object {
        const val STOP_REASON_REFUSAL = "refusal"
        const val STOP_REASON_MAX_TOKENS = "max_tokens"
    }
}

// The API returns polymorphic blocks (text, thinking, ...); unknown fields on non-text
// blocks are skipped by Moshi, so a single tolerant class covers all of them.
@JsonClass(generateAdapter = true)
data class AnthropicContentBlock(
    @Json(name = "type") val type: String,
    @Json(name = "text") val text: String? = null,
)

@JsonClass(generateAdapter = true)
data class AnthropicUsage(
    @Json(name = "input_tokens") val inputTokens: Long? = null,
    @Json(name = "output_tokens") val outputTokens: Long? = null,
)
