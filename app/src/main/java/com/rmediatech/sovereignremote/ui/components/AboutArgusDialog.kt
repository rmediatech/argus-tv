package com.rmediatech.sovereignremote.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                    text = "The all-seeing, zero-cloud universal device & smart TV controller. Communicates directly with Google TV, Android TV, Fire TV, Roku, Smart TVs, and Wake-on-LAN hosts over your local network.",
                    fontSize = 13.sp,
                    color = palette.textSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = palette.border)
                Spacer(modifier = Modifier.height(14.dp))
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
                    text = com.rmediatech.sovereignremote.logging.SovereignLogger.getLogFilePath() ?: "Persistent local logs: files/logs/argus.log",
                    fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = palette.textSecondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = palette.primaryAccent, fontWeight = FontWeight.Bold)
            }
        }
    )
}
