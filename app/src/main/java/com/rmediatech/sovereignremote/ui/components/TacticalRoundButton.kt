package com.rmediatech.sovereignremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rmediatech.sovereignremote.ui.theme.LocalThemePalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reusable Tactical Round Button with responsive visual press-feedback and haptic support.
 */
@Composable
fun TacticalRoundButton(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    val palette = LocalThemePalette.current
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(if (isPressed) accentColor.copy(alpha = 0.25f) else palette.surface)
                .border(
                    width = if (isPressed) 2.dp else 1.dp,
                    color = if (isPressed) accentColor else accentColor.copy(alpha = 0.6f),
                    shape = CircleShape
                )
                .clickable {
                    scope.launch {
                        isPressed = true
                        delay(150)
                        isPressed = false
                    }
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isPressed) Color.White else accentColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = palette.textSecondary
        )
    }
}
