package au.com.shiftyjelly.pocketcasts.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import au.com.shiftyjelly.pocketcasts.compose.bars.ThemedTopAppBar
import au.com.shiftyjelly.pocketcasts.compose.components.FormField
import au.com.shiftyjelly.pocketcasts.compose.components.SettingRow
import au.com.shiftyjelly.pocketcasts.compose.components.SettingRowToggle
import au.com.shiftyjelly.pocketcasts.compose.components.SettingSection
import au.com.shiftyjelly.pocketcasts.compose.components.SettingsSection
import au.com.shiftyjelly.pocketcasts.compose.components.TextP50
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.settings.viewmodel.ClaudeAiSettingsViewModel
import au.com.shiftyjelly.pocketcasts.localization.R as LR

@Composable
fun ClaudeAiSettingsPage(
    viewModel: ClaudeAiSettingsViewModel,
    onBackPress: () -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val state: ClaudeAiSettingsViewModel.State by viewModel.state.collectAsState()

    Column(
        modifier = modifier,
    ) {
        ThemedTopAppBar(
            title = stringResource(LR.string.settings_claude_ai),
            bottomShadow = true,
            onNavigationClick = { onBackPress() },
        )

        LazyColumn(
            contentPadding = PaddingValues(bottom = bottomInset),
            modifier = Modifier
                .background(MaterialTheme.theme.colors.primaryUi02)
                .fillMaxHeight(),
        ) {
            item {
                TextP50(
                    text = stringResource(LR.string.settings_claude_ai_summary),
                    color = MaterialTheme.theme.colors.primaryText02,
                    modifier = Modifier.padding(SettingsSection.horizontalPadding),
                )
            }
            item {
                SettingSection(
                    heading = stringResource(LR.string.settings_claude_api_key),
                    indent = false,
                ) {
                    FormField(
                        value = state.apiKey,
                        placeholder = stringResource(LR.string.settings_claude_api_key_hint),
                        onValueChange = { viewModel.onApiKeyChange(it) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    TextP50(
                        text = stringResource(LR.string.settings_claude_api_key_summary),
                        color = MaterialTheme.theme.colors.primaryText02,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            item {
                SettingSection(
                    heading = stringResource(LR.string.settings_claude_ai),
                    indent = false,
                ) {
                    SettingRow(
                        primaryText = stringResource(LR.string.settings_claude_privacy_opt_in),
                        secondaryText = stringResource(LR.string.settings_claude_privacy_opt_in_summary),
                        toggle = SettingRowToggle.Switch(state.sendTranscripts),
                        indent = false,
                        modifier = Modifier.toggleable(
                            value = state.sendTranscripts,
                            role = Role.Switch,
                            onValueChange = { viewModel.onSendTranscriptsChange(it) },
                        ),
                    )
                    SettingRow(
                        primaryText = stringResource(LR.string.settings_claude_ad_skip),
                        secondaryText = stringResource(LR.string.settings_claude_ad_skip_summary),
                        toggle = SettingRowToggle.Switch(state.adSkipEnabled),
                        indent = false,
                        modifier = Modifier.toggleable(
                            value = state.adSkipEnabled,
                            role = Role.Switch,
                            onValueChange = { viewModel.onAdSkipChange(it) },
                        ),
                    )
                }
            }
        }
    }
}
