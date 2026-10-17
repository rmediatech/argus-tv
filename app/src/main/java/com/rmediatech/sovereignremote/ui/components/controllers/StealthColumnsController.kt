package com.rmediatech.sovereignremote.ui.components.controllers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * 4. OBSIDIAN STEALTH: Ergonomic high-speed darkroom layout optimized for single-handed thumb reach.
 */
@Composable
fun StealthColumnsController(
    onSendKey: (RemoteKey) -> Unit,
    performHaptic: () -> Unit
) {
    val palette = LocalThemePalette.current
    val icons = LocalThemeIcons.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(20.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Navigation Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxHeight().weight(1f)
        ) {
            // UP
            IconButton(
                onClick = { performHaptic(); onSendKey(RemoteKey.UP) },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceElevated)
            ) {
                Icon(icons.up, contentDescription = "Up", tint = palette.primaryAccent)
            }

            // Mid Row: LEFT, ENTER, RIGHT
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { performHaptic(); onSendKey(RemoteKey.LEFT) },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surfaceElevated)
                ) {
                    Icon(icons.left, contentDescription = "Left", tint = palette.textPrimary)
                }

                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surfaceElevated)
                        .border(1.dp, palette.primaryAccent, RoundedCornerShape(8.dp))
                        .clickable { performHaptic(); onSendKey(RemoteKey.SELECT) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = icons.selectLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.primaryAccent,
                        fontSize = 11.sp
                    )
                }

                IconButton(
                    onClick = { performHaptic(); onSendKey(RemoteKey.RIGHT) },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surfaceElevated)
                ) {
                    Icon(icons.right, contentDescription = "Right", tint = palette.textPrimary)
                }
            }

            // DOWN
            IconButton(
                onClick = { performHaptic(); onSendKey(RemoteKey.DOWN) },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceElevated)
            ) {
                Icon(icons.down, contentDescription = "Down", tint = palette.primaryAccent)
            }
        }
    }
}
