package com.rmediatech.sovereignremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rmediatech.sovereignremote.ui.theme.LocalThemePalette
import com.rmediatech.sovereignremote.ui.theme.ThemePreset
import com.rmediatech.sovereignremote.ui.theme.ThemeRegistry

/**
 * One-Job-Principle: Dedicated modal dialog for selecting and previewing UI themes.
 */
@Composable
fun ThemeSelectionDialog(
    currentTheme: ThemePreset,
    onSelectTheme: (ThemePreset) -> Unit,
    onDismiss: () -> Unit
) {
    val currentPalette = LocalThemePalette.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = currentPalette.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Theme & Controller Layout",
                    style = MaterialTheme.typography.titleMedium,
                    color = currentPalette.textPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemePreset.entries.forEach { preset ->
                    val isSelected = preset == currentTheme
                    val palette = ThemeRegistry.getPalette(preset)
                    val layout = ThemeRegistry.getLayout(preset)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) currentPalette.surfaceElevated else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) currentPalette.primaryAccent else currentPalette.border,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onSelectTheme(preset)
                                onDismiss()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = preset.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) palette.primaryAccent else currentPalette.textPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // Swatch indicator pills
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(palette.background)
                                            .border(1.dp, palette.border, CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(palette.primaryAccent)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(palette.secondaryAccent)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = preset.tagLine,
                                style = MaterialTheme.typography.labelSmall,
                                color = currentPalette.textSecondary,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Layout: ${layout.name.replace("_", " ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.primaryAccent.copy(alpha = 0.8f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onSelectTheme(preset)
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = currentPalette.primaryAccent,
                                unselectedColor = currentPalette.border
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = currentPalette.textSecondary)
            }
        }
    )
}
