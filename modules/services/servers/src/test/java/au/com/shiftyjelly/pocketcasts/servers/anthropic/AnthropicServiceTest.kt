package au.com.shiftyjelly.pocketcasts.servers.anthropic

import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create

class AnthropicServiceTest {
    @get:Rule
    val server = MockWebServer()

    private val service: AnthropicService = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
        .build()
        .create()

    @Test
    fun `sends api key and version headers to the messages endpoint`() = runTest {
        server.enqueue(MockResponse().setBody(RESPONSE_JSON))

        service.messages(
            apiKey = "sk-ant-test",
            request = AnthropicMessagesRequest(
                model = "claude-sonnet-5",
                maxTokens = 256,
                system = "You are helpful.",
                messages = listOf(AnthropicMessage(role = AnthropicMessage.ROLE_USER, content = "Hi")),
            ),
        )

        val request = server.takeRequest()
        assertEquals("/v1/messages", request.path)
        assertEquals("sk-ant-test", request.getHeader("x-api-key"))
        assertEquals("2023-06-01", request.getHeader("anthropic-version"))
        val body = request.body.readUtf8()
        assertEquals(true, body.contains("\"model\":\"claude-sonnet-5\""))
        assertEquals(true, body.contains("\"max_tokens\":256"))
    }

    @Test
    fun `joins text blocks and ignores thinking blocks`() = runTest {
        server.enqueue(MockResponse().setBody(RESPONSE_JSON))

        val response = service.messages(apiKey = "key", request = minimalRequest())

        assertEquals("Hello there", response.textOrNull())
        assertEquals("end_turn", response.stopReason)
        assertEquals(12L, response.usage?.inputTokens)
    }

    @Test
    fun `returns null text for a refusal with empty content`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":"msg_2","content":[],"stop_reason":"refusal"}"""))

        val response = service.messages(apiKey = "key", request = minimalRequest())

        assertNull(response.textOrNull())
        assertEquals(AnthropicMessagesResponse.STOP_REASON_REFUSAL, response.stopReason)
    }

    private fun minimalRequest() = AnthropicMessagesRequest(
        model = "claude-sonnet-5",
        maxTokens = 64,
        messages = listOf(AnthropicMessage(role = AnthropicMessage.ROLE_USER, content = "Hi")),
    )

    private companion object {
        val RESPONSE_JSON = """
            {
              "id": "msg_1",
              "type": "message",
              "role": "assistant",
              "model": "claude-sonnet-5",
              "content": [
                {"type": "thinking", "thinking": "reasoning happens here"},
                {"type": "text", "text": "Hello "},
                {"type": "text", "text": "there"}
              ],
              "stop_reason": "end_turn",
              "usage": {"input_tokens": 12, "output_tokens": 5}
            }
        """.trimIndent()
    }
}
