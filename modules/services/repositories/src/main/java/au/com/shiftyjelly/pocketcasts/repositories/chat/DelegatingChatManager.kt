package au.com.shiftyjelly.pocketcasts.repositories.chat

import au.com.shiftyjelly.pocketcasts.repositories.ai.ClaudeManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

// Podcatcher fork: picks the chat backend per call so a key added or removed in settings takes
// effect immediately — Claude when configured, otherwise the upstream Pocket Casts backend.
@Singleton
class DelegatingChatManager @Inject constructor(
    private val upstream: ChatManagerImpl,
    private val claude: ClaudeChatManager,
    private val claudeManager: ClaudeManager,
) : ChatManager {
    private val active: ChatManager
        get() = if (claudeManager.isConfigured()) claude else upstream

    override fun observeMessages(episodeUuid: String): Flow<List<ChatMessage>> = active.observeMessages(episodeUuid)

    override suspend fun getMessages(episodeUuid: String): List<ChatMessage> = active.getMessages(episodeUuid)

    override suspend fun createChat(episodeUuid: String, podcastUuid: String, welcomeMessage: ChatMessage) {
        active.createChat(episodeUuid, podcastUuid, welcomeMessage)
    }

    override suspend fun sendMessage(episodeUuid: String, message: ChatMessage.User, allMessages: List<ChatMessage>) {
        active.sendMessage(episodeUuid, message, allMessages)
    }

    override suspend fun clearMessages(episodeUuid: String, welcomeMessage: ChatMessage) {
        active.clearMessages(episodeUuid, welcomeMessage)
    }
}
