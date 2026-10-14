package io.github.tellyteam.telly.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import io.github.tellyteam.telly.BuildConfig
import io.github.tellyteam.telly.ui.UpdateState
import io.github.tellyteam.telly.data.Provider
import io.github.tellyteam.telly.ui.components.PageInset
import io.github.tellyteam.telly.ui.home.PillButton
import io.github.tellyteam.telly.ui.theme.Telly

/** Downloader (AFTVnews) code that always fetches the latest release. */
private const val DOWNLOADER_CODE = "3327748"

@Composable
fun SettingsScreen(
    provider: Provider,
    updating: Boolean,
    onEditProvider: () -> Unit,
    onRemoveProvider: () -> Unit,
    onClearSavedData: () -> Unit,
    update: UpdateState,
    onCheckForUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    // A second press within a few seconds confirms; otherwise the button resets.
    LaunchedEffect(confirmClear) { if (confirmClear) { delay(4_000); confirmClear = false } }
    LaunchedEffect(confirmRemove) { if (confirmRemove) { delay(4_000); confirmRemove = false } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = PageInset, top = 22.dp, end = 40.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Telly.Text)

        Section("Your provider") {
            Text(provider.name, fontSize = 17.sp, color = Telly.Text)
            Text(
                when (provider) {
                    is Provider.Xtream -> "Xtream Codes · ${provider.server}"
                    is Provider.M3u -> "M3U playlist"
                },
                fontSize = 13.sp,
                color = Telly.Muted,
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Edit provider", primary = false, onClick = onEditProvider, modifier = Modifier.focusRequester(firstFocus))
                PillButton(
                    if (confirmRemove) "Press again to remove" else "Remove provider",
                    primary = false,
                    onClick = { if (confirmRemove) onRemoveProvider() else confirmRemove = true },
                )
            }
        }

        Section("Channels, films and series") {
            Text(
                "telly saves your lists so they open instantly, and refreshes them in the background each time it starts. Use Refresh in the menu to update them now.",
                fontSize = 13.sp,
                color = Telly.Muted,
                modifier = Modifier.width(560.dp),
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton(
                    if (confirmClear) "Press again to clear" else "Clear saved data",
                    primary = false,
                    onClick = {
                        if (confirmClear) {
                            confirmClear = false
                            onClearSavedData()
                        } else {
                            confirmClear = true
                        }
                    },
                )
                if (updating) Text("Updating…", fontSize = 13.sp, color = Telly.Accent)
            }
            Text(
                "Clearing removes saved lists and artwork, then downloads them again. Your My List, watch progress and history are kept.",
                fontSize = 12.sp,
                color = Telly.Faint,
                modifier = Modifier.padding(top = 8.dp).width(560.dp),
            )
        }

        Section("Updates") {
            Text("You have telly ${BuildConfig.VERSION_NAME}. telly checks for a new version once a week.", fontSize = 13.sp, color = Telly.Muted)
            val release = when (update) {
                is UpdateState.Available -> update.release
                is UpdateState.Downloading -> update.release
                is UpdateState.NeedsPermission -> update.release
                is UpdateState.Failed -> update.release
                else -> null
            }
            val status = when (update) {
                UpdateState.Idle -> null
                UpdateState.Checking -> "Checking…"
                UpdateState.UpToDate -> "You're up to date."
                is UpdateState.Available -> "telly ${update.release.version} is available."
                is UpdateState.Downloading -> "Downloading telly ${update.release.version}… ${(update.progress * 100).toInt()}%"
                is UpdateState.NeedsPermission -> "Allow telly to install apps (on the screen that opened), then come back and choose Install again."
                is UpdateState.Failed -> update.message
            }
            status?.let { Text(it, fontSize = 13.sp, color = Telly.Text, modifier = Modifier.padding(top = 4.dp)) }
            (update as? UpdateState.Available)?.release?.notes?.let {
                Text(it, fontSize = 12.sp, color = Telly.Faint, maxLines = 4, modifier = Modifier.padding(top = 4.dp).width(560.dp))
            }
            if (release != null) {
                Text(
                    "Android then asks you to confirm: choose Install, then Open. Everything you've set up is kept.",
                    fontSize = 13.sp,
                    color = Telly.Accent,
                    modifier = Modifier.padding(top = 4.dp).width(560.dp),
                )
            }
            // One button that stays put and only changes its label, so the remote's focus never
            // falls out to the menu while checking or downloading.
            Row(Modifier.padding(top = 12.dp)) {
                val (label, action) = when {
                    update is UpdateState.Checking -> "Checking…" to {}
                    update is UpdateState.Downloading -> "Downloading… ${(update.progress * 100).toInt()}%" to {}
                    release != null -> "Download and install ${release.version}" to onInstallUpdate
                    else -> "Check for updates" to onCheckForUpdates
                }
                PillButton(label, primary = release != null, onClick = action)
            }
        }

        Section("About") {
            Text("telly ${BuildConfig.VERSION_NAME}", fontSize = 15.sp, color = Telly.Text)
            Text("Plays your own IPTV subscription. Talks only to your provider.", fontSize = 13.sp, color = Telly.Muted)
            Text("To update: open Downloader and enter code $DOWNLOADER_CODE", fontSize = 13.sp, color = Telly.Muted)
        }
    }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title.uppercase(), fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = Telly.Accent, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}
