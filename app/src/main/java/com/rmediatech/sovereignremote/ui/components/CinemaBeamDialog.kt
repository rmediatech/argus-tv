package com.rmediatech.sovereignremote.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.rmediatech.sovereignremote.model.NowPlayingState
import com.rmediatech.sovereignremote.model.PlaybackStatus
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.ui.theme.LocalThemePalette
import kotlinx.coroutines.delay

/**
 * Sovereign Cinema Beam & Multi-TV Watch Party Dialog:
 * One Job: Displays live hardware-clock Now Playing telemetry, lets the user beam any movie search
 * or URL from their phone keyboard without D-pad pecking, and toggles Multi-Room Watch Party sync.
 */
@Composable
fun CinemaBeamDialog(
    currentDevice: RemoteDevice?,
    otherDevices: List<RemoteDevice>,
    nowPlaying: NowPlayingState?,
    watchPartyIps: Set<String>,
    followerTelemetry: Map<String, NowPlayingState> = emptyMap(),
    onToggleWatchPartyDevice: (String) -> Unit,
    onBeamMedia: (String) -> Unit,
    onScanRequested: () -> Unit = {},
    onAddManualIp: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val palette = LocalThemePalette.current
    val view = LocalView.current
    val scrollState = rememberScrollState()
    var queryText by remember { mutableStateOf("") }
    var showGuide by remember { mutableStateOf(false) }
    var tickMs by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(nowPlaying) {
        while (true) {
            tickMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(20.dp))
                .padding(20.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SOVEREIGN CINEMA & WATCH PARTY", color = palette.primaryAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (showGuide) "Hide Steps" else "Quick Steps ℹ️",
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { showGuide = !showGuide }.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            if (showGuide) {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(palette.surfaceElevated).border(1.dp, palette.primaryAccent.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("1. Beam: Type title or paste URL below to launch on TV instantly.", color = palette.textSecondary, fontSize = 11.sp)
                    Text("2. Multi-TV Sync: Tap other TVs in your home to lockstep Play/Pause.", color = palette.textSecondary, fontSize = 11.sp)
                    Text("3. Subtitles & CLI: Use 'argus cinema --srt movie.srt' on PC for live sync.", color = palette.textSecondary, fontSize = 11.sp)
                }
            }

            // 1. LIVE NOW-PLAYING TELEMETRY CARD
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.surfaceElevated).border(1.dp, palette.border, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val statusLabel = nowPlaying?.status?.label ?: "IDLE"
                val appLabel = nowPlaying?.appLabel ?: (currentDevice?.name ?: "No Active Stream")
                Text("$appLabel • $statusLabel", color = if (nowPlaying?.status == PlaybackStatus.PLAYING) palette.primaryAccent else palette.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(nowPlaying?.title ?: "Ready to beam or track playback", color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (nowPlaying != null && nowPlaying.positionMs > 0L) {
                    Text("Hardware Playhead: ${nowPlaying.formattedPlayhead(tickMs)}", color = palette.primaryAccent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            // 2. DIRECT MOVIE SEARCH / URL BEAM (NO D-PAD PECKING)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("BEAM MOVIE SEARCH OR YOUTUBE URL", color = palette.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    placeholder = { Text("e.g. Dreamcatcher, Matrix, or URL", fontSize = 12.sp, color = palette.textSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        focusedBorderColor = palette.primaryAccent,
                        unfocusedBorderColor = palette.border
                    )
                )
                Button(
                    onClick = {
                        if (queryText.isNotBlank()) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onBeamMedia(queryText)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primaryAccent)
                ) {
                    Text("Beam Straight to TV", color = palette.background, fontWeight = FontWeight.Bold)
                }
            }

            // 3. MULTI-ROOM WATCH PARTY SYNC
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MULTI-ROOM WATCH PARTY SYNC",
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Scan Wi-Fi 🔄",
                        color = palette.primaryAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onScanRequested() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (otherDevices.isNotEmpty()) {
                    for (dev in otherDevices.take(4)) {
                        val isSynced = watchPartyIps.contains(dev.ipAddress)
                        val fState = followerTelemetry[dev.ipAddress]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.surfaceElevated)
                                .border(
                                    1.dp,
                                    if (isSynced) palette.primaryAccent else palette.border,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onToggleWatchPartyDevice(dev.ipAddress)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dev.name, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                val subLabel = if (isSynced && fState != null) {
                                    "${dev.ipAddress} • ${fState.status.label} (${fState.formattedPlayhead(tickMs)})"
                                } else {
                                    dev.ipAddress
                                }
                                Text(subLabel, color = palette.textSecondary, fontSize = 11.sp)
                            }
                            Text(
                                text = if (isSynced) "SYNCED 🍿" else "TAP TO SYNC",
                                color = if (isSynced) palette.primaryAccent else palette.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // Empty state card with inline IP add
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceElevated)
                            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Only 1 TV detected on Wi-Fi (${currentDevice?.name ?: "Current"}). Enter your second TV IP below to link them in lockstep:",
                            color = palette.textSecondary,
                            fontSize = 11.sp
                        )
                        var manualIpInput by remember { mutableStateOf("") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = manualIpInput,
                                onValueChange = { manualIpInput = it },
                                placeholder = { Text("e.g. 192.168.1.186", fontSize = 11.sp, color = palette.textSecondary) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = palette.textPrimary),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = palette.textPrimary,
                                    unfocusedTextColor = palette.textPrimary,
                                    focusedBorderColor = palette.primaryAccent,
                                    unfocusedBorderColor = palette.border
                                )
                            )
                            Button(
                                onClick = {
                                    if (manualIpInput.isNotBlank()) {
                                        onAddManualIp(manualIpInput.trim())
                                        manualIpInput = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.primaryAccent)
                            ) {
                                Text("Add TV", color = palette.background, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = palette.textSecondary)
                }
            }
        }
    }
}
