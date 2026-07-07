package au.com.shiftyjelly.pocketcasts.repositories.chat

import au.com.shiftyjelly.pocketcasts.models.db.dao.EpisodeChatDao
import au.com.shiftyjelly.pocketcasts.models.entity.EpisodeChat
import au.com.shiftyjelly.pocketcasts.models.to.Transcript
import au.com.shiftyjelly.pocketcasts.repositories.ai.ClaudeManager
import au.com.shiftyjelly.pocketcasts.repositories.ai.truncateForClaude
import au.com.shiftyjelly.pocketcasts.repositories.transcript.TranscriptManager
import au.com.shiftyjelly.pocketcasts.servers.anthropic.AnthropicMessage
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Podcatcher fork: episode chat backed by the user's own Anthropic key instead of the
// Pocket Casts backend. Persists to the same tables as ChatManagerImpl, so conversations
// stay intact if the backend selection changes.
@Singleton
class ClaudeChatManager @Inject constructor(
    private val episodeChatDao: EpisodeChatDao,
    private val transcriptManager: TranscriptManager,
    private val claudeManager: ClaudeManager,
    moshi: Moshi,
) : ChatManager {
    private val quoteMetadataAdapter = moshi.adapter(QuoteMetadata::class.java)

    override fun observeMessages(episodeUuid: String): Flow<List<ChatMessage>> {
        return episodeChatDao.observeMessages(episodeUuid).map { it.toChatMessages(quoteMetadataAdapter) }
    }

    override suspend fun getMessages(episodeUuid: String): List<ChatMessage> {
        return episodeChatDao.getMessages(episodeUuid).toChatMessages(quoteMetadataAdapter)
    }

    override suspend fun createChat(episodeUuid: String, podcastUuid: String, welcomeMessage: ChatMessage) {
        episodeChatDao.insertChat(EpisodeChat(episodeUuid = episodeUuid, podcastUuid = podcastUuid))
        episodeChatDao.insertMessage(welcomeMessage.toEntity(episodeUuid, quoteMetadataAdapter))
    }

    override suspend fun sendMessage(
        episodeUuid: String,
        message: ChatMessage.User,
        allMessages: List<ChatMessage>,
    ) {
        val transcriptText = (transcriptManager.loadTranscript(episodeUuid) as? Transcript.Text)
            ?.buildString()
            ?.takeIf(String::isNotBlank)
        checkNotNull(transcriptText) { "A transcript is required to chat about this episode" }

        val history = allMessages.mapNotNull { msg ->
            val content = msg.textOrNull() ?: return@mapNotNull null
            AnthropicMessage(role = msg.role.apiRole, content = content)
        }
        val messages = normalizeForAnthropic(
            history + AnthropicMessage(role = AnthropicMessage.ROLE_USER, content = message.text),
        )

        val reply = claudeManager.complete(
            system = SYSTEM_PROMPT_PREFIX + transcriptText.truncateForClaude(),
            messages = messages,
            maxTokens = MAX_TOKENS,
        )

        episodeChatDao.insertMessage(message.toEntity(episodeUuid, quoteMetadataAdapter))
        episodeChatDao.insertMessage(ChatMessage.Assistant(text = reply).toEntity(episodeUuid, quoteMetadataAdapter))
    }

    override suspend fun clearMessages(episodeUuid: String, welcomeMessage: ChatMessage) {
        episodeChatDao.deleteMessagesByEpisode(episodeUuid)
        episodeChatDao.insertMessage(welcomeMessage.toEntity(episodeUuid, quoteMetadataAdapter))
    }

    companion object {
        const val MAX_TOKENS = 1024

        val SYSTEM_PROMPT_PREFIX = """
            You answer questions about a podcast episode using its transcript. Be concise and
            conversational. Only use information from the transcript; say so when the transcript
            doesn't contain the answer. The transcript follows:


        """.trimIndent()

        // The Anthropic API requires the first message to be from the user and roles to strictly
        // alternate, while stored chats begin with an assistant welcome message and may contain
        // consecutive same-role entries. Drops leading assistant messages and merges neighbours.
        internal fun normalizeForAnthropic(messages: List<AnthropicMessage>): List<AnthropicMessage> {
            val trimmed = messages.dropWhile { it.role != AnthropicMessage.ROLE_USER }
            val result = mutableListOf<AnthropicMessage>()
            for (message in trimmed) {
                val last = result.lastOrNull()
                if (last != null && last.role == message.role) {
                    result[result.lastIndex] = last.copy(content = "${last.content}\n\n${message.content}")
                } else {
                    result += message
                }
            }
            return result
        }
    }
}
