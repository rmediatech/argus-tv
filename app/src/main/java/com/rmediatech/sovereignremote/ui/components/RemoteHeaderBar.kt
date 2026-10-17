package com.rmediatech.sovereignremote.ui.components

import androidx.compose.animation.core.*
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rmediatech.sovereignremote.model.DeviceLivenessState
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.ui.theme.LocalThemeIcons
import com.rmediatech.sovereignremote.ui.theme.LocalThemePalette

/**
 * Top Header & Target Device Bar:
 * Displays sovereign branding, utility actions (Theme, Scan, About),
 * and the active target device card with real-time liveness, ACK latency,
 * and first-time pairing assistance chips.
 */
@Composable
fun RemoteHeaderBar(
    currentDevice: RemoteDevice?,
    isScanning: Boolean,
    livenessState: DeviceLivenessState,
    lastAckLatencyMs: Long?,
    isUnconfigured: Boolean = false,
    onOpenThemeDialog: () -> Unit,
    onScanRequested: () -> Unit,
    onOpenAboutDialog: () -> Unit,
    onOpenDeviceDialog: () -> Unit,
    onOpenSetupGuide: (() -> Unit)? = null,
    performHaptic: () -> Unit
) {
    val palette = LocalThemePalette.current
    val icons = LocalThemeIcons.current

    val infiniteTransition = rememberInfiniteTransition(label = "HeaderLivenessPulse")
    val wakingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wakingAlpha"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. BRANDING & UTILITY ACTION ICONS ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ARGUS",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    ),
                    color = palette.primaryAccent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.primaryAccent.copy(alpha = 0.15f))
                        .border(1.dp, palette.primaryAccent.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SOVEREIGN",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.primaryAccent
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { performHaptic(); onOpenThemeDialog() }) {
                    Icon(imageVector = icons.themeSwitcher, contentDescription = "Switch Theme", tint = palette.primaryAccent)
                }
                IconButton(onClick = { performHaptic(); onScanRequested() }, enabled = !isScanning) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = palette.primaryAccent, strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "Scan Wi-Fi", tint = palette.primaryAccent)
                    }
                }
                IconButton(onClick = { performHaptic(); onOpenAboutDialog() }) {
                    Icon(imageVector = Icons.Rounded.Info, contentDescription = "About Argus", tint = palette.primaryAccent)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- 2. TARGET DEVICE CARD ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                .clickable {
                    performHaptic()
                    onOpenDeviceDialog()
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dotColor = Color(livenessState.colorHex)
                val dotAlpha = if (livenessState == DeviceLivenessState.WAKING) wakingAlpha else 1.0f

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = dotAlpha))
                        .then(
                            if (livenessState == DeviceLivenessState.ONLINE) {
                                Modifier.border(2.dp, dotColor.copy(alpha = 0.35f), CircleShape)
                            } else Modifier
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentDevice?.name ?: "No Device Selected",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary,
                            fontSize = 14.sp
                        )
                        if (currentDevice != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            val badgeColor = Color(currentDevice.deviceType.badgeColorHex)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(badgeColor.copy(alpha = 0.15f))
                                    .border(1.dp, badgeColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = currentDevice.deviceType.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(5.dp))
                            val statusColor = Color(livenessState.colorHex)
                            val badgeAlpha = if (livenessState == DeviceLivenessState.WAKING) wakingAlpha else 1.0f
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(statusColor.copy(alpha = if (livenessState == DeviceLivenessState.WAKING) badgeAlpha * 0.2f else 0.15f))
                                    .border(1.dp, statusColor.copy(alpha = if (livenessState == DeviceLivenessState.WAKING) badgeAlpha else 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = livenessState.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    val ackText = when {
                        lastAckLatencyMs != null && lastAckLatencyMs >= 0 -> " • ACK < ${lastAckLatencyMs.coerceAtLeast(1)}ms"
                        lastAckLatencyMs == -1L -> " • TIMEOUT (TV Not Responding)"
                        else -> ""
                    }
                    Text(
                        text = currentDevice?.let { 
                            "${it.ipAddress} • " + (if (it.isEthernet) "Ethernet" else "Wi-Fi") + (it.modelName?.let { m -> " ($m)" } ?: "") + ackText
                        } ?: (if (isScanning) "Searching Wi-Fi for TVs..." else "Tap to scan or select TV"),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (lastAckLatencyMs == -1L) Color(0xFFFF1744) else palette.textSecondary
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Select",
                tint = palette.textSecondary
            )
        }

        // --- 3. FIRST-TIME PAIRING ASSISTANCE CHIP ---
        if (currentDevice != null && (isUnconfigured || lastAckLatencyMs == -1L)) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF00E5FF).copy(alpha = 0.10f))
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                    .clickable {
                        performHaptic()
                        onOpenSetupGuide?.invoke()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "Pairing Guide",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isUnconfigured) {
                            "Setup needed: Enable debugging or pairing mode on TV"
                        } else {
                            "First time connecting? Check TV screen to select Allow"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF00E5FF)
                    )
                }
                Text(
                    text = "HELP ›",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF)
                )
            }
        }
    }
}
