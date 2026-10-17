package com.rmediatech.sovereignremote.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rmediatech.sovereignremote.model.DeviceLivenessState
import com.rmediatech.sovereignremote.model.RemoteKey
import com.rmediatech.sovereignremote.ui.theme.LocalThemeIcons
import com.rmediatech.sovereignremote.ui.theme.LocalThemePalette

/**
 * Top Tactical Action Bar: Power/Wake, Back, Home, and Options.
 */
@Composable
fun TacticalActionBar(
    livenessState: DeviceLivenessState,
    onSendKey: (RemoteKey) -> Unit,
    performHaptic: () -> Unit
) {
    val palette = LocalThemePalette.current
    val icons = LocalThemeIcons.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (powerLabel, powerColor) = when (livenessState) {
            DeviceLivenessState.STANDBY -> "WAKE" to Color(0xFFFFAB00)
            DeviceLivenessState.WAKING -> "WAKING" to Color(0xFF00E5FF)
            DeviceLivenessState.ONLINE -> "POWER" to palette.powerColor
            DeviceLivenessState.DISCONNECTED -> "POWER" to palette.powerColor
        }
        TacticalRoundButton(
            icon = icons.power,
            label = powerLabel,
            accentColor = powerColor,
            onClick = {
                performHaptic()
                onSendKey(RemoteKey.POWER)
            }
        )
        TacticalRoundButton(
            icon = icons.back,
            label = "BACK",
            accentColor = palette.textPrimary,
            onClick = {
                performHaptic()
                onSendKey(RemoteKey.BACK)
            }
        )
        TacticalRoundButton(
            icon = icons.home,
            label = "HOME",
            accentColor = palette.primaryAccent,
            onClick = {
                performHaptic()
                onSendKey(RemoteKey.HOME)
            }
        )
        TacticalRoundButton(
            icon = icons.options,
            label = "OPTIONS",
            accentColor = palette.secondaryAccent,
            onClick = {
                performHaptic()
                onSendKey(RemoteKey.INFO)
            }
        )
    }
}
