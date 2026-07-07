package au.com.shiftyjelly.pocketcasts.repositories.chat

import au.com.shiftyjelly.pocketcasts.models.db.dao.EpisodeChatDao
import au.com.shiftyjelly.pocketcasts.models.entity.EpisodeChat
import au.com.shiftyjelly.pocketcasts.models.entity.EpisodeChatMessage
import au.com.shiftyjelly.pocketcasts.models.to.Transcript
import au.com.shiftyjelly.pocketcasts.models.to.TranscriptEntry
import au.com.shiftyjelly.pocketcasts.models.to.TranscriptType
import au.com.shiftyjelly.pocketcasts.repositories.ai.ClaudeManager
import au.com.shiftyjelly.pocketcasts.repositories.chat.ClaudeChatManager.Companion.normalizeForAnthropic
import au.com.shiftyjelly.pocketcasts.repositories.transcript.TranscriptManager
import au.com.shiftyjelly.pocketcasts.servers.anthropic.AnthropicMessage
import com.squareup.moshi.Moshi
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ClaudeChatManagerTest {
    private val episodeChatDao = TestEpisodeChatDao()
    private val transcriptManager = mock<TranscriptManager>()
    private val claudeManager = mock<ClaudeManager>()
    private val manager = ClaudeChatManager(
        episodeChatDao = episodeChatDao,
        transcriptManager = transcriptManager,
        claudeManager = claudeManager,
        moshi = Moshi.Builder().build(),
    )

    @Test
    fun `send message passes transcript as system context and stores both messages`() = runTest {
        whenever(transcriptManager.loadTranscript(EPISODE_UUID)).thenReturn(transcript())
        whenever(claudeManager.complete(anyOrNull(), any(), any())).thenReturn("Claude reply")

        manager.sendMessage(
            episodeUuid = EPISODE_UUID,
            message = ChatMessage.User(text = "What happened?", uuid = "user-uuid"),
            allMessages = listOf(
                ChatMessage.Assistant(text = "Welcome", uuid = "welcome-uuid"),
                ChatMessage.User(text = "Earlier question", uuid = "earlier-uuid"),
                ChatMessage.Assistant(text = "Earlier answer", uuid = "answer-uuid"),
            ),
        )

        val systemCaptor = argumentCaptor<String>()
        val messagesCaptor = argumentCaptor<List<AnthropicMessage>>()
        verify(claudeManager).complete(systemCaptor.capture(), messagesCaptor.capture(), any())
        assertTrue(systemCaptor.firstValue.contains("Welcome to the show."))
        assertEquals(
            listOf(
                AnthropicMessage(role = "user", content = "Earlier question"),
                AnthropicMessage(role = "assistant", content = "Earlier answer"),
                AnthropicMessage(role = "user", content = "What happened?"),
            ),
            messagesCaptor.firstValue,
        )
        assertEquals(listOf("What happened?", "Claude reply"), episodeChatDao.messages.map { it.text })
        assertEquals(listOf(ChatRole.User.value, ChatRole.Assistant.value), episodeChatDao.messages.map { it.role })
    }

    @Test
    fun `send message requires transcript`() = runTest {
        whenever(transcriptManager.loadTranscript(EPISODE_UUID)).thenReturn(null)

        val exception = runCatching {
            manager.sendMessage(
                episodeUuid = EPISODE_UUID,
                message = ChatMessage.User(text = "Question", uuid = "user-uuid"),
                allMessages = emptyList(),
            )
        }.exceptionOrNull()

        assertTrue(exception is IllegalStateException)
        assertEquals(0, episodeChatDao.messages.size)
    }

    @Test
    fun `send message does not store user message when claude fails`() = runTest {
        whenever(transcriptManager.loadTranscript(EPISODE_UUID)).thenReturn(transcript())
        whenever(claudeManager.complete(anyOrNull(), any(), any())).thenAnswer { throw IOException() }

        val exception = runCatching {
            manager.sendMessage(
                episodeUuid = EPISODE_UUID,
                message = ChatMessage.User(text = "Question", uuid = "user-uuid"),
                allMessages = emptyList(),
            )
        }.exceptionOrNull()

        assertTrue(exception is IOException)
        assertEquals(0, episodeChatDao.messages.size)
    }

    @Test
    fun `normalize drops leading assistant messages`() {
        val normalized = normalizeForAnthropic(
            listOf(
                AnthropicMessage(role = "assistant", content = "Welcome"),
                AnthropicMessage(role = "user", content = "Question"),
            ),
        )

        assertEquals(listOf(AnthropicMessage(role = "user", content = "Question")), normalized)
    }

    @Test
    fun `normalize merges consecutive same role messages`() {
        val normalized = normalizeForAnthropic(
            listOf(
                AnthropicMessage(role = "user", content = "First"),
                AnthropicMessage(role = "user", content = "Second"),
                AnthropicMessage(role = "assistant", content = "Reply"),
                AnthropicMessage(role = "assistant", content = "Quote"),
                AnthropicMessage(role = "user", content = "Third"),
            ),
        )

        assertEquals(
            listOf(
                AnthropicMessage(role = "user", content = "First\n\nSecond"),
                AnthropicMessage(role = "assistant", content = "Reply\n\nQuote"),
                AnthropicMessage(role = "user", content = "Third"),
            ),
            normalized,
        )
    }

    private class TestEpisodeChatDao : EpisodeChatDao() {
        val chats = mutableListOf<EpisodeChat>()
        val messages = mutableListOf<EpisodeChatMessage>()

        override suspend fun insertChat(chat: EpisodeChat) {
            chats.removeAll { it.episodeUuid == chat.episodeUuid }
            chats += chat
        }

        override suspend fun getChatByEpisode(episodeUuid: String): EpisodeChat? {
            return chats.firstOrNull { it.episodeUuid == episodeUuid }
        }

        override suspend fun deleteChat(episodeUuid: String) {
            chats.removeAll { it.episodeUuid == episodeUuid }
        }

        override suspend fun insertMessage(message: EpisodeChatMessage) {
            messages.removeAll { it.uuid == message.uuid }
            messages += message
        }

        override fun observeMessages(episodeUuid: String): Flow<List<EpisodeChatMessage>> {
            return MutableStateFlow(messages.filter { it.episodeUuid == episodeUuid }.sortedBy { it.createdAt })
        }

        override suspend fun getMessages(episodeUuid: String): List<EpisodeChatMessage> {
            return messages.filter { it.episodeUuid == episodeUuid }.sortedBy { it.createdAt }
        }

        override suspend fun deleteMessagesByEpisode(episodeUuid: String) {
            messages.removeAll { it.episodeUuid == episodeUuid }
        }
    }

    private companion object {
        const val EPISODE_UUID = "episode-uuid"

        fun transcript() = Transcript.Text(
            entries = listOf(TranscriptEntry.Text("Welcome to the show.")),
            type = TranscriptType.Vtt,
            url = "https://example.com/transcript.vtt",
            isGenerated = false,
            episodeUuid = EPISODE_UUID,
            podcastUuid = "podcast-uuid",
        )
    }
}
