package com.rmediatech.sovereignremote.ui.components.controllers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rmediatech.sovereignremote.model.RemoteKey
import com.rmediatech.sovereignremote.ui.theme.LocalThemeIcons
import com.rmediatech.sovereignremote.ui.theme.LocalThemePalette

/**
 * 1. CYBER TACTICAL: Precision circular radar D-Pad with glowing Neon Cyan center and concentric ring.
 */
@Composable
fun RadarCircularController(
    onSendKey: (RemoteKey) -> Unit,
    performHaptic: () -> Unit
) {
    val palette = LocalThemePalette.current
    val icons = LocalThemeIcons.current

    Box(
        modifier = Modifier
            .size(240.dp)
            .clip(CircleShape)
            .background(palette.surface)
            .border(1.dp, palette.border, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Concentric ambient radar ring
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .border(1.dp, palette.primaryAccent.copy(alpha = 0.15f), CircleShape)
        )

        // UP
        IconButton(
            onClick = { performHaptic(); onSendKey(RemoteKey.UP) },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(54.dp)
        ) {
            Icon(icons.up, contentDescription = "Up", tint = palette.textPrimary, modifier = Modifier.size(34.dp))
        }

        // DOWN
        IconButton(
            onClick = { performHaptic(); onSendKey(RemoteKey.DOWN) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).size(54.dp)
        ) {
            Icon(icons.down, contentDescription = "Down", tint = palette.textPrimary, modifier = Modifier.size(34.dp))
        }

        // LEFT
        IconButton(
            onClick = { performHaptic(); onSendKey(RemoteKey.LEFT) },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(54.dp)
        ) {
            Icon(icons.left, contentDescription = "Left", tint = palette.textPrimary, modifier = Modifier.size(34.dp))
        }

        // RIGHT
        IconButton(
            onClick = { performHaptic(); onSendKey(RemoteKey.RIGHT) },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(54.dp)
        ) {
            Icon(icons.right, contentDescription = "Right", tint = palette.textPrimary, modifier = Modifier.size(34.dp))
        }

        // CENTER OK
        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(CircleShape)
                .background(palette.surfaceElevated)
                .border(1.5.dp, palette.primaryAccent, CircleShape)
                .clickable { performHaptic(); onSendKey(RemoteKey.SELECT) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icons.selectLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.primaryAccent
            )
        }
    }
}
