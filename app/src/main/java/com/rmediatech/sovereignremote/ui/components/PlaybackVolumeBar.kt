package com.rmediatech.sovereignremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.rmediatech.sovereignremote.model.RemoteKey
import com.rmediatech.sovereignremote.ui.theme.LocalThemeIcons
import com.rmediatech.sovereignremote.ui.theme.LocalThemePalette

/**
 * Media Transport Playback Row (Rewind, Play/Pause, Fast-Forward) and Volume/Mute tactile cluster.
 */
@Composable
fun PlaybackVolumeBar(
    onSendKey: (RemoteKey) -> Unit,
    performHaptic: () -> Unit
) {
    val palette = LocalThemePalette.current
    val icons = LocalThemeIcons.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
