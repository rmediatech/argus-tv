package com.rmediatech.sovereignremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.ui.theme.LocalThemePalette

/**
 * Sovereign Tactile Device Card:
 * One Job: Presents a single discovered Smart TV with online/standby liveness,
 * brand color badge, quick action toggles (hide/forget), and setup helper indicators.
 */
@Composable
fun DeviceCard(
    device: RemoteDevice,
    isSelected: Boolean,
    isOnline: Boolean,
    isHidden: Boolean = false,
    needsSetup: Boolean = false,
    networkSubtitle: String? = null,
    onSelect: () -> Unit,
    onToggleHide: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onShowSetupGuide: ((RemoteDevice) -> Unit)? = null
) {
    val palette = LocalThemePalette.current
    val badgeColor = Color(device.deviceType.badgeColorHex)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) palette.surfaceElevated else Color.Transparent)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) palette.primaryAccent.copy(alpha = 0.6f) else palette.border.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable {
                if (needsSetup && onShowSetupGuide != null) {
                    onShowSetupGuide(device)
                } else {
                    onSelect()
                }
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor.copy(alpha = if (isHidden) 0.08f else 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Tv,
                    contentDescription = null,
                    tint = if (isHidden) badgeColor.copy(alpha = 0.45f) else badgeColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.name,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isHidden) palette.textSecondary else palette.textPrimary,
                        fontSize = 13.sp
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "Active TV",
                            tint = palette.primaryAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (needsSetup) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFFB300).copy(alpha = 0.18f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "Setup Needed",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB300)
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    val dotColor = when {
                        needsSetup -> Color(0xFFFFB300)
                        isOnline -> Color(0xFF00E676)
                        else -> Color(0xFF9E9E9E)
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    val sub = when {
                        needsSetup -> "Tap for 1-step guide • Port 5555 disabled (${device.ipAddress})"
                        networkSubtitle != null -> networkSubtitle
                        else -> "${if (isOnline) "Online" else "Standby"} • ${device.deviceType.displayName} • ${device.ipAddress}"
                    }
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (needsSetup) Color(0xFFFFB300).copy(alpha = 0.9f) else if (isOnline) palette.textPrimary.copy(alpha = 0.85f) else palette.textSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (needsSetup && onShowSetupGuide != null) {
                IconButton(onClick = { onShowSetupGuide(device) }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.HelpOutline,
                        contentDescription = "Setup Guide",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (onToggleHide != null) {
                IconButton(onClick = onToggleHide, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = if (isHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = if (isHidden) "Unhide TV" else "Hide TV",
                        tint = if (isHidden) palette.primaryAccent else palette.textSecondary.copy(alpha = 0.65f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Forget TV",
                    tint = Color(0xFFFF5252).copy(alpha = 0.85f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}
