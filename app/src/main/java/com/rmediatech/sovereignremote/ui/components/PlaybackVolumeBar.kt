package com.rmediatech.sovereignremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rmediatech.sovereignremote.model.NowPlayingState
import com.rmediatech.sovereignremote.model.PlaybackStatus
import com.rmediatech.sovereignremote.model.RemoteKey
import com.rmediatech.sovereignremote.ui.theme.LocalThemeIcons
import com.rmediatech.sovereignremote.ui.theme.LocalThemePalette
import kotlinx.coroutines.delay

/**
 * Media Transport Playback Row (Rewind, Play/Pause, Fast-Forward), Now-Playing Pill, and Volume/Mute cluster.
 */
@Composable
fun PlaybackVolumeBar(
    nowPlaying: NowPlayingState? = null,
    watchPartyCount: Int = 0,
    onOpenCinemaDialog: () -> Unit = {},
    onSendKey: (RemoteKey) -> Unit,
    performHaptic: () -> Unit
) {
    val palette = LocalThemePalette.current
    val icons = LocalThemeIcons.current
    var tickMs by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(nowPlaying) {
        while (nowPlaying?.status == PlaybackStatus.PLAYING) {
            tickMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- SOVEREIGN NOW-PLAYING & CINEMA BEAM PILL ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surfaceElevated)
                .border(1.dp, if (nowPlaying?.status == PlaybackStatus.PLAYING) palette.primaryAccent else palette.border, RoundedCornerShape(12.dp))
                .clickable { performHaptic(); onOpenCinemaDialog() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val pillTitle = if (nowPlaying != null) {
                "🎬 ${nowPlaying.title}"
            } else {
                "🎬 Beam Movie / Watch Party"
            }
            Text(
                text = pillTitle,
                color = palette.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            val badgeLabel = if (watchPartyCount > 0) {
                "🍿 PARTY (${watchPartyCount + 1} TVs)"
            } else {
                "🍿 WATCH PARTY"
            }
            val playheadLabel = if (nowPlaying != null && nowPlaying.positionMs > 0L) {
                " • ${nowPlaying.formattedPlayhead(tickMs)}"
            } else ""

            Row(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.primaryAccent.copy(alpha = 0.2f))
                    .border(1.dp, palette.primaryAccent.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$badgeLabel$playheadLabel",
                    color = palette.primaryAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        // --- PLAYBACK CONTROLS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { performHaptic(); onSendKey(RemoteKey.REWIND) }) {
                Icon(icons.rewind, contentDescription = "Rev", tint = palette.textSecondary, modifier = Modifier.size(28.dp))
            }
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(palette.surfaceElevated)
                    .border(1.dp, palette.border, CircleShape)
                    .clickable { performHaptic(); onSendKey(RemoteKey.PLAY_PAUSE) },
                contentAlignment = Alignment.Center
            ) {
                Icon(icons.playPause, contentDescription = "Play/Pause", tint = palette.primaryAccent, modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = { performHaptic(); onSendKey(RemoteKey.FAST_FORWARD) }) {
                Icon(icons.fastForward, contentDescription = "Fwd", tint = palette.textSecondary, modifier = Modifier.size(28.dp))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // --- BOTTOM VOLUME & MUTE ROW ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { performHaptic(); onSendKey(RemoteKey.VOLUME_DOWN) }) {
                Icon(icons.volDown, contentDescription = "Vol -", tint = palette.textPrimary)
            }
            IconButton(onClick = { performHaptic(); onSendKey(RemoteKey.MUTE) }) {
                Icon(icons.mute, contentDescription = "Mute", tint = palette.powerColor)
            }
            IconButton(onClick = { performHaptic(); onSendKey(RemoteKey.VOLUME_UP) }) {
                Icon(icons.volUp, contentDescription = "Vol +", tint = palette.textPrimary)
            }
        }
    }
}
