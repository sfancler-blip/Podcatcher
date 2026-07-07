package au.com.shiftyjelly.pocketcasts.servers.anthropic

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

// Anthropic Messages API. The key is passed per call because it is entered by the user in
// app settings at runtime (never BuildConfig), so an interceptor bound at graph-creation
// time would not see key changes.
interface AnthropicService {
    @POST("v1/messages")
    @Headers("anthropic-version: 2023-06-01")
    suspend fun messages(
        @Header("x-api-key") apiKey: String,
        @Body request: AnthropicMessagesRequest,
    ): AnthropicMessagesResponse

    companion object {
        const val BASE_URL = "https://api.anthropic.com/"
    }
}
