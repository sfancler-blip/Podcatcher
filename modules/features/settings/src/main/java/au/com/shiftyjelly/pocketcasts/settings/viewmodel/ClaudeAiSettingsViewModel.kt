package au.com.shiftyjelly.pocketcasts.settings.viewmodel

import androidx.lifecycle.ViewModel
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Podcatcher fork: settings for the Claude AI features (summaries, chat, ad skipping).
@HiltViewModel
class ClaudeAiSettingsViewModel @Inject constructor(
    private val settings: Settings,
) : ViewModel() {
    data class State(
        val apiKey: String,
        val sendTranscripts: Boolean,
        val adSkipEnabled: Boolean,
    )

    private val _state = MutableStateFlow(
        State(
            apiKey = settings.anthropicApiKey.value,
            sendTranscripts = settings.sendTranscriptsToAnthropic.value,
            adSkipEnabled = settings.adSkipEnabled.value,
        ),
    )
    val state = _state.asStateFlow()

    fun onApiKeyChange(value: String) {
        settings.anthropicApiKey.set(value.trim(), updateModifiedAt = false)
        _state.update { it.copy(apiKey = value) }
    }

    fun onSendTranscriptsChange(enabled: Boolean) {
        settings.sendTranscriptsToAnthropic.set(enabled, updateModifiedAt = false)
        _state.update { it.copy(sendTranscripts = enabled) }
    }

    fun onAdSkipChange(enabled: Boolean) {
        settings.adSkipEnabled.set(enabled, updateModifiedAt = false)
        _state.update { it.copy(adSkipEnabled = enabled) }
    }
}
