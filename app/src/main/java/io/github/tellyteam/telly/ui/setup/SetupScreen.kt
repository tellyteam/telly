package io.github.tellyteam.telly.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tellyteam.telly.data.DEFAULT_PLAYLIST_NAME
import io.github.tellyteam.telly.data.DEFAULT_PROVIDER_NAME
import io.github.tellyteam.telly.data.Provider
import io.github.tellyteam.telly.ui.theme.TellyWordmark
import io.github.tellyteam.telly.ui.theme.TvTextField
import io.github.tellyteam.telly.ui.theme.focusRing

/**
 * Getting providers from another device on the sign-in screen (a hidden feature, like sync: seven
 * presses on the wordmark show it), or, for a device still linked with no provider, a way out.
 */
class JoinOptions(
    val shown: Boolean,
    val linked: Boolean,
    val onUnlock: () -> Unit,
    /** Returns an error message, or null. */
    val onJoin: suspend (code: String) -> String?,
    val onStopSync: () -> Unit,
)

@Composable
fun SetupScreen(
    existing: Provider?,
    onConnect: (Provider) -> Unit,
    onForget: (() -> Unit)?,
    /** Set when opened from Settings: Back returns without changing anything. */
    onCancel: (() -> Unit)? = null,
    join: JoinOptions? = null,
) {
    BackHandler(enabled = onCancel != null) { onCancel?.invoke() }
    val xtream = existing as? Provider.Xtream
    val m3u = existing as? Provider.M3u
    var useXtream by rememberSaveable { mutableStateOf(m3u == null) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var server by rememberSaveable { mutableStateOf(xtream?.server ?: "") }
    var username by rememberSaveable { mutableStateOf(xtream?.username ?: "") }
    var password by rememberSaveable { mutableStateOf(xtream?.password ?: "") }
    var url by rememberSaveable { mutableStateOf(m3u?.url ?: "") }
    var allowCleartext by rememberSaveable { mutableStateOf(existing?.allowCleartext ?: false) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var wordmarkPresses by remember { mutableStateOf(0) }
    LaunchedEffect(wordmarkPresses) { if (wordmarkPresses > 0) { delay(3_000); wordmarkPresses = 0 } }

    val canConnect = if (useXtream) server.isNotBlank() && username.isNotBlank() && password.isNotBlank() else url.isNotBlank()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TellyWordmark(
                fontSize = 48.sp,
                modifier = if (join == null) Modifier else Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (++wordmarkPresses >= 7) { wordmarkPresses = 0; join.onUnlock() }
                },
            )
            Text(if (existing == null) "Add your IPTV provider" else "Your IPTV provider", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Details are stored encrypted on this device and sent only to your provider.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = useXtream, onClick = { useXtream = true }, label = { Text("Xtream Codes") }, modifier = Modifier.focusRing())
                FilterChip(selected = !useXtream, onClick = { useXtream = false }, label = { Text("M3U playlist") }, modifier = Modifier.focusRing())
            }

            TvTextField("Name (optional)", name, modifier = Modifier.fillMaxWidth()) { name = it }
            if (useXtream) {
                TvTextField("Server, e.g. https://example.com:8080", server, KeyboardType.Uri, modifier = Modifier.fillMaxWidth()) { server = it }
                TvTextField("Username", username, modifier = Modifier.fillMaxWidth()) { username = it }
                TvTextField("Password", password, KeyboardType.Password, isPassword = !showPassword, modifier = Modifier.fillMaxWidth()) { password = it }
                FilterChip(
                    selected = showPassword,
                    onClick = { showPassword = !showPassword },
                    label = { Text(if (showPassword) "Hide password" else "Show password") },
                    modifier = Modifier.focusRing(),
                )
            } else {
                TvTextField("Playlist URL", url, KeyboardType.Uri, modifier = Modifier.fillMaxWidth()) { url = it }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Switch(checked = allowCleartext, onCheckedChange = { allowCleartext = it }, modifier = Modifier.focusRing())
                Column {
                    Text("Allow unencrypted HTTP")
                    Text(
                        "Only turn on if your provider has no HTTPS. Your login is then sent in plain text.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    enabled = canConnect,
                    modifier = Modifier.focusRing(),
                    onClick = {
                        val label = name.trim()
                        onConnect(
                            if (useXtream) {
                                Provider.Xtream(label.ifEmpty { DEFAULT_PROVIDER_NAME }, server.trim(), username.trim(), password, allowCleartext)
                            } else {
                                Provider.M3u(label.ifEmpty { DEFAULT_PLAYLIST_NAME }, url.trim(), allowCleartext)
                            },
                        )
                    },
                ) { Text("Connect") }
                if (onForget != null) {
                    OutlinedButton(onClick = onForget, modifier = Modifier.focusRing()) { Text("Remove provider") }
                }
            }
            if (join != null && existing == null && onCancel == null) JoinSection(join)
        }
    }
}

@Composable
private fun JoinSection(join: JoinOptions) {
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var joining by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    if (join.linked) {
        Text("This device is still linked for sync", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
        Text(
            "Providers your other devices share arrive here on their own, if you share logins.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = join.onStopSync, modifier = Modifier.focusRing()) { Text("Stop syncing on this device") }
        return
    }
    if (!join.shown) return
    Text("Or get your providers from another device", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
    Text(
        "On a device that has them, turn on Settings → Sync → Share provider logins, then enter its sync code here.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TvTextField("Sync code, e.g. ABCD-EFGH-JKMN-PQRS", code, KeyboardType.Ascii, modifier = Modifier.fillMaxWidth()) { code = it }
    Button(
        enabled = code.isNotBlank() && !joining,
        modifier = Modifier.focusRing(),
        onClick = {
            joining = true
            error = null
            scope.launch {
                error = join.onJoin(code)
                joining = false
            }
        },
    ) { Text(if (joining) "Linking…" else "Get providers") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}
