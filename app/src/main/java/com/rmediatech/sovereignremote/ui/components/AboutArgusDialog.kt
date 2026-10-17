package com.rmediatech.sovereignremote.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rmediatech.sovereignremote.logging.SovereignLogger
import com.rmediatech.sovereignremote.ui.theme.LocalThemePalette

/**
 * Sovereign About Argus Dialog:
 * One Job: Displays tactical provenance, version, zero-cloud architecture, and developer credentials.
 */
@Composable
fun AboutArgusDialog(
    onDismiss: () -> Unit
) {
    val palette = LocalThemePalette.current
    var showLogsDialog by remember { mutableStateOf(false) }

    if (showLogsDialog) {
        DiagnosticLogsDialog(onDismiss = { showLogsDialog = false })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = palette.primaryAccent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "About Argus",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Argus Sovereign Remote v1.0.0",
                    fontWeight = FontWeight.Bold,
                    color = palette.primaryAccent,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The all-seeing, zero-cloud universal device & smart TV controller. Communicates directly with Samsung Tizen, LG webOS, Sony BRAVIA, Vizio SmartCast, Google TV, Android TV, Fire TV, Roku, and Wake-on-LAN hosts over your local network.",
                    fontSize = 13.sp,
                    color = palette.textSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = palette.border)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "FIRST-TIME TV PAIRING",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "For security, your TV operating system (Fire TV, Google TV, Samsung, or LG) displays a one-time permission popup on your TV screen when connecting for the first time. Select 'Always Allow' or 'Accept' using your TV's physical remote. Roku TVs connect instantly with zero prompts.",
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = palette.border)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "DEVELOPER & ARCHITECTURE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Crafted by RMediaTech LLC",
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    fontSize = 13.sp
                )
                Text(
                    text = "https://rmediatech.com",
                    color = palette.primaryAccent,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "100% Free • Zero Ads • Zero Cloud Relays • Pure Local Sovereign Engineering",
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = palette.border)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "DIAGNOSTIC AUDIT LOGS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = SovereignLogger.getLogFilePath() ?: "Persistent local logs: files/logs/argus.log",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = palette.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showLogsDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.primaryAccent),
                    border = BorderStroke(1.dp, palette.primaryAccent.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = palette.primaryAccent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "View Diagnostic Logs",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.primaryAccent
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = palette.primaryAccent, fontWeight = FontWeight.Bold)
            }
        }
    )
}
