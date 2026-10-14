package io.github.tellyteam.telly.ui.live

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.github.tellyteam.telly.container
import io.github.tellyteam.telly.data.Channel
import io.github.tellyteam.telly.ui.player.PlayerActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** How long focus rests on a channel before it starts playing, so scrolling the list stays cheap. */
private const val SETTLE_MS = 1_000L

/** Give up (and keep the logo) if the channel hasn't shown a picture by then. */
private const val START_TIMEOUT_MS = 8_000L

/**
 * One small, muted player for Live TV's preview, reused for every channel and released when the
 * screen goes. It buffers only a few seconds and prefers low-resolution variants, to stay light
 * on a TV stick and on the provider.
 */
@OptIn(UnstableApi::class)
@Composable
fun rememberPreviewPlayer(): ExoPlayer {
    val context = LocalContext.current
    val player = remember { previewPlayer(context) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

@OptIn(UnstableApi::class)
private fun previewPlayer(context: Context): ExoPlayer {
    val trackSelector = DefaultTrackSelector(context).apply {
        setParameters(buildUponParameters().setMaxVideoSizeSd())
    }
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(2_000, 8_000, 1_000, 1_000)
        .build()
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(context.container.http)))
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        .build()
        .apply { volume = 0f }
}

/**
 * The focused channel, live, once focus has settled on it. [fallback] (the logo) shows until the
 * first picture arrives, and stays if the channel doesn't play.
 */
@OptIn(UnstableApi::class)
@Composable
fun LivePicture(channel: Channel?, player: ExoPlayer, modifier: Modifier, fallback: @Composable () -> Unit) {
    var showing by remember { mutableStateOf(false) }
    var firstFrames by remember { mutableIntStateOf(0) }
    var failures by remember { mutableIntStateOf(0) }
    // Bumped when telly comes back to the front, to start the preview again.
    var resumes by remember { mutableIntStateOf(0) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrames++
            }

            override fun onPlayerError(error: PlaybackException) {
                failures++
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Leaving telly (or opening the full player) frees the provider connection straight away.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        player.stop()
        showing = false
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { resumes++ }

    LaunchedEffect(channel?.id, resumes) {
        showing = false
        player.stop()
        player.clearMediaItems()
        if (channel == null) return@LaunchedEffect
        delay(SETTLE_MS)
        val frames = firstFrames
        val errors = failures
        player.setMediaItem(
            MediaItem.Builder().setUri(channel.streamUrl).setMimeType(PlayerActivity.mimeTypeFor(channel.streamUrl)).build(),
        )
        player.prepare()
        player.play()
        val started = withTimeoutOrNull(START_TIMEOUT_MS) {
            snapshotFlow { firstFrames > frames || failures > errors }.first { it }
            firstFrames > frames
        }
        if (started == true) showing = true else player.stop()
    }

    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (!showing) fallback()
    }
}
