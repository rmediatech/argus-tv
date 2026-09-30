package com.rmediatech.sovereignremote.ui.components

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
        modifier = Modifier.fillMaxWidth().height(240.dp),
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
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(palette.surfaceElevated)
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
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(palette.surfaceElevated)
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
