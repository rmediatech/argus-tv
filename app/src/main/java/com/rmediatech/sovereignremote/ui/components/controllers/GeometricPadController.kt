package com.rmediatech.sovereignremote.ui.components.controllers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * 2. NORDIC MINIMALIST: Modern frosted rounded glass plate (Scandi Hi-Fi touch surface).
 */
@Composable
fun GeometricPadController(
    onSendKey: (RemoteKey) -> Unit,
    performHaptic: () -> Unit
) {
    val palette = LocalThemePalette.current
    val icons = LocalThemeIcons.current
    val shape = RoundedCornerShape(32.dp)

    Box(
        modifier = Modifier
            .size(240.dp)
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.border, shape),
        contentAlignment = Alignment.Center
    ) {
        // UP
        IconButton(
            onClick = { performHaptic(); onSendKey(RemoteKey.UP) },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(52.dp)
        ) {
            Icon(icons.up, contentDescription = "Up", tint = palette.primaryAccent, modifier = Modifier.size(24.dp))
        }

        // DOWN
        IconButton(
            onClick = { performHaptic(); onSendKey(RemoteKey.DOWN) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).size(52.dp)
        ) {
            Icon(icons.down, contentDescription = "Down", tint = palette.primaryAccent, modifier = Modifier.size(24.dp))
        }

        // LEFT
        IconButton(
            onClick = { performHaptic(); onSendKey(RemoteKey.LEFT) },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp).size(52.dp)
        ) {
            Icon(icons.left, contentDescription = "Left", tint = palette.primaryAccent, modifier = Modifier.size(24.dp))
        }

        // RIGHT
        IconButton(
            onClick = { performHaptic(); onSendKey(RemoteKey.RIGHT) },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).size(52.dp)
        ) {
            Icon(icons.right, contentDescription = "Right", tint = palette.primaryAccent, modifier = Modifier.size(24.dp))
        }

        // CENTER MINIMALIST DOT / GLASS TOUCH BUTTON
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(CircleShape)
                .background(palette.surfaceElevated)
                .border(1.dp, palette.border, CircleShape)
                .clickable { performHaptic(); onSendKey(RemoteKey.SELECT) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icons.selectLabel,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = palette.primaryAccent
            )
        }
    }
}
