package com.rmediatech.sovereignremote.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rmediatech.sovereignremote.logging.SovereignLogger
import com.rmediatech.sovereignremote.ui.theme.LocalThemePalette

/**
 * Sovereign In-App Diagnostic Logs Dialog:
 * One Job: Displays and exports local rolling diagnostic logs directly on-device without needing ADB.
 */
@Composable
fun DiagnosticLogsDialog(
    onDismiss: () -> Unit
) {
    val palette = LocalThemePalette.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var logText by remember { mutableStateOf(SovereignLogger.getFullLogContent()) }
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    // Scroll to bottom whenever text updates
    LaunchedEffect(logText) {
        if (logText.isNotEmpty()) {
            verticalScroll.scrollTo(verticalScroll.maxValue)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = null,
                        tint = palette.primaryAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Diagnostic Logs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                }

                IconButton(
                    onClick = {
                        SovereignLogger.clearLogs()
                        logText = SovereignLogger.getFullLogContent()
                        Toast.makeText(context, "Diagnostic logs cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Clear Logs",
                        tint = palette.textSecondary
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = SovereignLogger.getLogFilePath() ?: "Memory Ring-Buffer",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = palette.textSecondary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .background(palette.background, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    if (logText.isBlank()) {
                        Text(
                            text = "No log records recorded yet.",
                            color = palette.textSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        Text(
                            text = logText,
                            color = palette.textPrimary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp,
                            modifier = Modifier
                                .verticalScroll(verticalScroll)
                                .horizontalScroll(horizontalScroll)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val fullLogs = SovereignLogger.getFullLogContent()
                    if (fullLogs.isNotBlank()) {
                        clipboardManager.setText(AnnotatedString(fullLogs))
                        Toast.makeText(context, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Log is empty", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = palette.primaryAccent)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = palette.background
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy All", color = palette.background, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = palette.textSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
