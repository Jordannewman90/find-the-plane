package com.airwall.radar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.airwall.radar.data.AppConfig
import com.airwall.radar.data.EnrichedFlight
import com.airwall.radar.data.OpenSkyApi
import com.airwall.radar.data.SpotterStage

@Composable
fun FlightOverviewPanel(
    flight: EnrichedFlight?,
    isSimulationMode: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(AirwallColors.PanelDark, RoundedCornerShape(14.dp))
            .border(1.5.dp, AirwallColors.CardBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AnimatedContent(
            targetState = flight,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "KidSpotterTransition"
        ) { currentFlight ->
            if (currentFlight != null) {
                KidSpotterContent(currentFlight, isSimulationMode)
            } else {
                IdleScanningContent()
            }
        }
    }
}

@Composable
private fun KidSpotterContent(
    flight: EnrichedFlight,
    isSimulationMode: Boolean
) {
    val meta = flight.metadata
    val guide = meta.kidGuide

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AsyncImage(
                    model = "file:///android_asset/app_logo.jpg",
                    contentDescription = "${AppConfig.APP_TITLE} Logo",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, AirwallColors.CyanGlow, CircleShape),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = "${AppConfig.APP_TITLE} • ${AppConfig.APP_SUBTITLE}",
                    color = AirwallColors.CyanGlow,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.1.sp
                )
            }

            if (isSimulationMode) {
                Text(
                    text = "[TEST FLIGHT]",
                    color = AirwallColors.YellowTag,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            } else {
                Text(
                    text = "LIVE OVERHEAD",
                    color = AirwallColors.GreenStatus,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        val isClosePass = OpenSkyApi.willCrossWithin2Miles(flight.state)
        val stage = guide.stage

        val cardBgGradient = when (stage) {
            SpotterStage.DIRECT_FLYOVER -> Brush.verticalGradient(listOf(Color(0xFF4A0E17), Color(0xFF26050A)))
            SpotterStage.RUN_OUTSIDE_NOW -> Brush.verticalGradient(listOf(Color(0xFF0F2D1F), Color(0xFF06180F)))
            SpotterStage.GET_READY -> Brush.verticalGradient(listOf(Color(0xFF2E2405), Color(0xFF171201)))
            SpotterStage.MONITORING_INBOUND -> Brush.verticalGradient(listOf(Color(0xFF0D1B2A), Color(0xFF080F18)))
            SpotterStage.FADING_AWAY -> Brush.verticalGradient(listOf(Color(0xFF16191D), Color(0xFF0C0E10)))
        }

        val cardBorderColor = when (stage) {
            SpotterStage.DIRECT_FLYOVER -> Color(0xFFFF1744)
            SpotterStage.RUN_OUTSIDE_NOW -> AirwallColors.GreenStatus
            SpotterStage.GET_READY -> Color(0xFFFFB300)
            SpotterStage.MONITORING_INBOUND -> Color(0xFF1E3A5F)
            SpotterStage.FADING_AWAY -> Color(0xFF37474F)
        }

        val badgeBg = when (stage) {
            SpotterStage.DIRECT_FLYOVER -> Color(0xFFFF1744)
            SpotterStage.RUN_OUTSIDE_NOW -> if (guide.doorToRunTo.contains("FRONT")) AirwallColors.YellowTag else AirwallColors.GreenStatus
            SpotterStage.GET_READY -> Color(0xFFFFB300)
            SpotterStage.MONITORING_INBOUND -> Color(0xFF1E3A5F)
            SpotterStage.FADING_AWAY -> Color(0xFF263238)
        }

        val badgeTextColor = when (stage) {
            SpotterStage.DIRECT_FLYOVER -> Color.White
            SpotterStage.RUN_OUTSIDE_NOW -> Color.Black
            SpotterStage.GET_READY -> Color.Black
            SpotterStage.MONITORING_INBOUND -> Color(0xFF80D8FF)
            SpotterStage.FADING_AWAY -> Color(0xFFCFD8DC)
        }

        val distBg = when (stage) {
            SpotterStage.DIRECT_FLYOVER -> Color(0x55FF1744)
            SpotterStage.RUN_OUTSIDE_NOW -> Color(0x3300E676)
            SpotterStage.GET_READY -> Color(0x44FFB300)
            SpotterStage.MONITORING_INBOUND -> Color(0x3300E5FF)
            SpotterStage.FADING_AWAY -> Color(0x3390A4AE)
        }

        val distTextColor = when (stage) {
            SpotterStage.DIRECT_FLYOVER -> Color(0xFFFF8A80)
            SpotterStage.RUN_OUTSIDE_NOW -> AirwallColors.GreenStatus
            SpotterStage.GET_READY -> Color(0xFFFFE082)
            SpotterStage.MONITORING_INBOUND -> AirwallColors.CyanGlow
            SpotterStage.FADING_AWAY -> Color(0xFFB0BEC5)
        }

        val lookLabel = when (stage) {
            SpotterStage.DIRECT_FLYOVER -> "🚨 FLYOVER:"
            SpotterStage.RUN_OUTSIDE_NOW -> "👀 LOOK:"
            SpotterStage.GET_READY -> "⏱️ INBOUND:"
            SpotterStage.MONITORING_INBOUND -> "📺 STATUS:"
            SpotterStage.FADING_AWAY -> "👋 DEPARTING:"
        }

        val lookTextColor = when (stage) {
            SpotterStage.DIRECT_FLYOVER -> Color(0xFFFF8A80)
            SpotterStage.RUN_OUTSIDE_NOW -> Color(0xFFFFFF8D)
            SpotterStage.GET_READY -> Color(0xFFFFE082)
            SpotterStage.MONITORING_INBOUND -> Color(0xFF80D8FF)
            SpotterStage.FADING_AWAY -> Color(0xFFB0BEC5)
        }

        val tipTextColor = when (stage) {
            SpotterStage.DIRECT_FLYOVER -> Color(0xFFFFCDD2)
            SpotterStage.RUN_OUTSIDE_NOW -> AirwallColors.TextWhite
            SpotterStage.GET_READY -> Color(0xFFFFECB3)
            SpotterStage.MONITORING_INBOUND -> Color(0xFF90A4AE)
            SpotterStage.FADING_AWAY -> Color(0xFF78909C)
        }

        val formattedTip = if (isClosePass && stage != SpotterStage.DIRECT_FLYOVER) {
            "🚨 FLYOVER ALERT: Path crosses within 2 miles! ${guide.spotterTip}"
        } else {
            guide.spotterTip
        }

        // 1. GIANT KID ACTION CARD (STAGE-AWARE: WATCH TV / GET READY / RUN OUTSIDE / DIRECT FLYOVER)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBgGradient, RoundedCornerShape(12.dp))
                .border(2.dp, cardBorderColor, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // Door Badge & Distance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(badgeBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = guide.doorToRunTo.uppercase(),
                        color = badgeTextColor,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Box(
                    modifier = Modifier
                        .background(distBg, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = guide.distanceText,
                        color = distTextColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Direction to look
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = lookLabel,
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = guide.directionToLook,
                    color = lookTextColor,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Spotter Tip
            Text(
                text = formattedTip,
                color = tipTextColor,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // 2. AIRCRAFT PHOTO & CALLSIGN CARD
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.95f)
                .background(AirwallColors.CardBackground, RoundedCornerShape(10.dp))
                .border(1.dp, AirwallColors.CardBorderHighlight, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
        ) {
            val imageModel = if (meta.assetImagePath.startsWith("http")) {
                meta.assetImagePath
            } else {
                "file:///android_asset/${meta.assetImagePath}"
            }

            AsyncImage(
                model = imageModel,
                contentDescription = meta.aircraftModelName,
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.CenterEnd),
                contentScale = ContentScale.Crop
            )

            // Gradient shadow on left for crystal clear text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                AirwallColors.PureBlack.copy(alpha = 0.95f),
                                AirwallColors.PureBlack.copy(alpha = 0.75f),
                                Color.Transparent
                            ),
                            startX = 0f,
                            endX = 650f
                        )
                    )
            )

            // Flight details overlay
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = "AIRCRAFT",
                    color = AirwallColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = meta.flightNumber,
                    color = AirwallColors.CyanGlow,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${meta.airlineName} • ${meta.aircraftModelName}",
                    color = AirwallColors.TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                if (meta.registration.isNotBlank()) {
                    Text(
                        text = "TAIL NUMBER: ${meta.registration}",
                        color = AirwallColors.TextDim,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // 3. FLIGHT DESTINATION & SPEED / ALTITUDE (KID FRIENDLY)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.05f)
                .background(AirwallColors.CardBackground, RoundedCornerShape(12.dp))
                .border(1.dp, AirwallColors.CardBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Destination info
            Column(
                modifier = Modifier.weight(1.3f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "WHERE IS IT FLYING?",
                    color = AirwallColors.CyanMuted,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                val destText = if (meta.destinationCity.isNotBlank()) {
                    if (meta.destinationAirportCode.isNotBlank()) "${meta.destinationCity} (${meta.destinationAirportCode})"
                    else meta.destinationCity
                } else if (meta.destinationAirportName.isNotBlank()) {
                    meta.destinationAirportName
                } else {
                    "Local Airspace / Michigan"
                }

                val titlePrefix = if (destText.startsWith("Local") || destText.contains("Michigan")) "Flying in " else "To "

                Text(
                    text = "$titlePrefix$destText",
                    color = AirwallColors.TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )

                if (meta.originCity.isNotBlank() && meta.originCity != meta.destinationCity) {
                    Text(
                        text = "From ${meta.originCity} (${meta.originAirportCode})",
                        color = AirwallColors.TextMuted,
                        fontSize = 11.5.sp,
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = "Cruising near Bishop Airport (FNT)",
                        color = AirwallColors.TextMuted,
                        fontSize = 11.5.sp,
                        maxLines = 1
                    )
                }
            }

            // Speed and Altitude cards
            Column(
                modifier = Modifier.weight(1.0f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HEIGHT:",
                        color = AirwallColors.CyanMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = guide.altitudeText,
                        color = AirwallColors.TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SPEED:",
                        color = AirwallColors.CyanMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = guide.speedMphText,
                        color = AirwallColors.CyanGlow,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleScanningContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = "file:///android_asset/app_logo.jpg",
            contentDescription = "Find The Plane Logo",
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .border(2.5.dp, AirwallColors.CyanGlow, CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "${AppConfig.APP_TITLE} • ${AppConfig.APP_SUBTITLE}",
            color = AirwallColors.CyanGlow,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "NO PLANES OVERHEAD RIGHT NOW",
            color = AirwallColors.TextWhite,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "RADAR LISTENING FOR PLANES NEAR ${AppConfig.LOCATION_LABEL}",
            color = AirwallColors.TextDim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .background(AirwallColors.CardBackground, RoundedCornerShape(8.dp))
                .border(1.dp, AirwallColors.CardBorderHighlight, RoundedCornerShape(8.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "PRESS [OK / SELECT] ON TV REMOTE",
                    color = AirwallColors.YellowTag,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "To test what it looks like when a plane flies over!",
                    color = AirwallColors.TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}
