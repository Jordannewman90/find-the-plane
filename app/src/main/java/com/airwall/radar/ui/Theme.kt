package com.airwall.radar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AirwallColors {
    val PureBlack = Color(0xFF000000)
    val PanelDark = Color(0xFF070C12)
    val CardBackground = Color(0xFF0A111A)
    val CardBorder = Color(0xFF142436)
    val CardBorderHighlight = Color(0xFF1E3A56)

    val CyanGlow = Color(0xFF00E5FF)
    val CyanMuted = Color(0xFF00B0FF)
    val CyanDim = Color(0xFF004D66)

    val MagentaRing = Color(0xFFD500F9)
    val MagentaText = Color(0xFFFF4081)

    val YellowTag = Color(0xFFFFD600)
    val YellowText = Color(0xFFFFFF00)

    val GreenStatus = Color(0xFF00E676)
    val OrangeStatus = Color(0xFFFF9100)

    val TextWhite = Color(0xFFFFFFFF)
    val TextMuted = Color(0xFF7E9EB8)
    val TextDim = Color(0xFF4A6882)

    val RadarGrid = Color(0xFF0D253A)
    val RadarGridDim = Color(0xFF081826)
}

@Composable
fun AirwallTheme(content: @Composable () -> Unit) {
    val darkColors = darkColorScheme(
        primary = AirwallColors.CyanGlow,
        background = AirwallColors.PureBlack,
        surface = AirwallColors.PanelDark,
        onPrimary = AirwallColors.PureBlack,
        onBackground = AirwallColors.TextWhite,
        onSurface = AirwallColors.TextWhite
    )

    MaterialTheme(
        colorScheme = darkColors,
        content = content
    )
}
