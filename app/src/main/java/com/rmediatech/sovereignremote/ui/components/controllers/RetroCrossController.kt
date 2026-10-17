package com.rmediatech.sovereignremote.ui.components.controllers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.rmediatech.sovereignremote.model.RemoteKey
import com.rmediatech.sovereignremote.ui.theme.LocalThemeIcons
import com.rmediatech.sovereignremote.ui.theme.LocalThemePalette

/**
 * 3. RETRO CONSOLE: Authentic arcade cross (+) D-Pad with tactile directional arms & dual A/B arcade action buttons.
 */
@Composable
fun RetroCrossController(
    onSendKey: (RemoteKey) -> Unit,
    performHaptic: () -> Unit
) {
    val palette = LocalThemePalette.current
    val icons = LocalThemeIcons.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Retro Cross D-Pad
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            // Horizontal Cross Arm
            Box(
                modifier = Modifier
                    .width(190.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.surface)
                    .border(1.5.dp, palette.border, RoundedCornerShape(12.dp))
            )

            // Vertical Cross Arm
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(190.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.surface)
                    .border(1.5.dp, palette.border, RoundedCornerShape(12.dp))
            )

            // Central Pivot
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .background(palette.surfaceElevated)
            )

            // UP
            IconButton(
                onClick = { performHaptic(); onSendKey(RemoteKey.UP) },
                modifier = Modifier.align(Alignment.TopCenter).size(60.dp)
            ) {
                Icon(icons.up, contentDescription = "Up", tint = palette.secondaryAccent, modifier = Modifier.size(36.dp))
            }

            // DOWN
            IconButton(
                onClick = { performHaptic(); onSendKey(RemoteKey.DOWN) },
                modifier = Modifier.align(Alignment.BottomCenter).size(60.dp)
            ) {
                Icon(icons.down, contentDescription = "Down", tint = palette.secondaryAccent, modifier = Modifier.size(36.dp))
            }

            // LEFT
            IconButton(
                onClick = { performHaptic(); onSendKey(RemoteKey.LEFT) },
                modifier = Modifier.align(Alignment.CenterStart).size(60.dp)
            ) {
                Icon(icons.left, contentDescription = "Left", tint = palette.secondaryAccent, modifier = Modifier.size(32.dp))
            }

            // RIGHT
            IconButton(
                onClick = { performHaptic(); onSendKey(RemoteKey.RIGHT) },
                modifier = Modifier.align(Alignment.CenterEnd).size(60.dp)
            ) {
                Icon(icons.right, contentDescription = "Right", tint = palette.secondaryAccent, modifier = Modifier.size(32.dp))
            }
        }

        // Arcade Action Buttons (A and B) angled ergonomically
        Column(
            modifier = Modifier.padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Button B (Back / Secondary)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(palette.surfaceElevated)
                    .border(2.dp, palette.secondaryAccent, CircleShape)
                    .clickable { performHaptic(); onSendKey(RemoteKey.BACK) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "B",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = palette.secondaryAccent
                )
            }

            // Button A (Select / Primary Action)
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(palette.surfaceElevated)
                    .border(2.dp, palette.primaryAccent, CircleShape)
                    .clickable { performHaptic(); onSendKey(RemoteKey.SELECT) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "A",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = palette.primaryAccent
                )
            }
        }
    }
}
