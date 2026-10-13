package io.github.tellyteam.telly.ui.shell

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tellyteam.telly.R
import io.github.tellyteam.telly.ui.theme.Telly
import io.github.tellyteam.telly.ui.theme.TellyWordmark

/** The rail's pages, in the agreed order. Refresh and Settings sit apart at the bottom; Refresh is an action, not a page. */
enum class Destination(val label: String, @DrawableRes val icon: Int) {
    HOME("Home", R.drawable.ic_nav_home),
    SEARCH("Search", R.drawable.ic_nav_search),
    LIVE("Live TV", R.drawable.ic_nav_live),
    SERIES("Series", R.drawable.ic_nav_series),
    FILMS("Films", R.drawable.ic_nav_films),
    MY_LIST("My List", R.drawable.ic_nav_mylist),
    REFRESH("Refresh", R.drawable.ic_nav_refresh),
    SETTINGS("Settings", R.drawable.ic_nav_settings),
}

/**
 * Netflix-style left rail: slim icons that widen to show labels while the rail has focus.
 * It overlays the page (which is laid out to its collapsed width), so expanding never
 * re-lays out the screen behind it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NavRail(
    destinations: List<Destination>,
    selected: Destination,
    onSelect: (Destination) -> Unit,
    selectedFocus: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Shows "Refreshing…" on the Refresh item while lists download. */
    refreshing: Boolean = false,
    /** A dot on Settings when a newer telly is available. */
    updateAvailable: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) 210.dp else Telly.RailWidth, tween(160), label = "railWidth")
    val scrim = if (expanded) {
        Brush.horizontalGradient(0f to Telly.BackgroundLow.copy(alpha = 0.97f), 0.75f to Telly.BackgroundLow.copy(alpha = 0.9f), 1f to Color.Transparent)
    } else {
        Brush.horizontalGradient(0f to Telly.BackgroundLow.copy(alpha = 0.9f), 1f to Color.Transparent)
    }

    Column(
        modifier
            .width(width)
            .fillMaxHeight()
            .background(scrim)
            .onFocusChanged {
                expanded = it.hasFocus
                onFocusChanged(it.hasFocus)
            }
            // Entering the rail always lands on the current page's item, not whichever icon
            // happens to be level with the focused card.
            .focusProperties { enter = { runCatching { selectedFocus }.getOrDefault(FocusRequester.Default) } }
            .focusGroup()
            .padding(horizontal = 10.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TellyWordmark(
            fontSize = if (expanded) 28.sp else 18.sp,
            modifier = Modifier.padding(start = if (expanded) 8.dp else 2.dp, bottom = 18.dp),
        )
        val bottom = listOf(Destination.REFRESH, Destination.SETTINGS)
        destinations.filter { it !in bottom }.forEach { d ->
            RailItem(d, d == selected, expanded, if (d == selected) selectedFocus else null) { onSelect(d) }
        }
        Spacer(Modifier.weight(1f))
        if (Destination.REFRESH in destinations) {
            RailItem(
                Destination.REFRESH, selected = false, expanded = expanded, focus = null,
                label = if (refreshing) "Refreshing…" else Destination.REFRESH.label,
                highlight = refreshing,
            ) { onSelect(Destination.REFRESH) }
        }
        if (Destination.SETTINGS in destinations) {
            RailItem(
                Destination.SETTINGS, Destination.SETTINGS == selected, expanded,
                if (Destination.SETTINGS == selected) selectedFocus else null,
                label = if (updateAvailable) "Settings · Update" else Destination.SETTINGS.label,
                dot = updateAvailable,
            ) { onSelect(Destination.SETTINGS) }
        }
    }
}

@Composable
private fun RailItem(
    d: Destination,
    selected: Boolean,
    expanded: Boolean,
    focus: FocusRequester?,
    label: String = d.label,
    highlight: Boolean = false,
    dot: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(Telly.ControlShape)
            .background(
                when {
                    focused -> Telly.AccentSoft
                    selected -> Color.White.copy(alpha = 0.06f)
                    else -> Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box {
            Icon(
                painterResource(d.icon),
                contentDescription = label,
                tint = when {
                    highlight -> Telly.Accent
                    focused || selected -> Color.White
                    else -> Telly.Muted
                },
                modifier = Modifier.size(20.dp),
            )
            if (dot) Box(Modifier.align(Alignment.TopEnd).size(7.dp).clip(Telly.ControlShape).background(Telly.Accent))
        }
        if (expanded) {
            Text(
                label,
                color = if (focused || selected) Color.White else Telly.Muted,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
