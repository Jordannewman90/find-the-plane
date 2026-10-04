package com.airwall.radar.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airwall.radar.data.AircraftState
import com.airwall.radar.data.AppConfig
import com.airwall.radar.data.EnrichedFlight
import com.airwall.radar.data.OpenSkyApi
import kotlin.math.*

@Composable
fun RadarMapView(
    selectedFlight: EnrichedFlight?,
    allNearbyFlights: List<AircraftState>,
    radiusMiles: Double = 10.0,
    modifier: Modifier = Modifier
) {
    // Continuous rotation for radar sweep when scanning
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepAngle"
    )

    // Pulse animation for home and perimeter
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    // Flash animation for 2-mile flyover hazard
    val alertTransition = rememberInfiniteTransition(label = "FlyoverAlert")
    val alertFlashAlpha by alertTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlertFlashAlpha"
    )

    val isClosePass = selectedFlight != null && OpenSkyApi.willCrossWithin2Miles(selectedFlight.state)

    // Keep GPS breadcrumbs for tracked flights (actual flight path history)
    val breadcrumbMap = remember { mutableStateMapOf<String, MutableList<Pair<Double, Double>>>() }

    LaunchedEffect(selectedFlight?.state?.callsign, selectedFlight?.state?.latitude, selectedFlight?.state?.longitude) {
        selectedFlight?.let { flight ->
            val key = flight.state.callsign.ifBlank { "ACTIVE" }
            val list = breadcrumbMap.getOrPut(key) { mutableListOf() }
            val last = list.lastOrNull()
            if (last == null || abs(last.first - flight.state.latitude) > 0.0001 || abs(last.second - flight.state.longitude) > 0.0001) {
                list.add(Pair(flight.state.latitude, flight.state.longitude))
                if (list.size > 50) list.removeAt(0)
            }
        }
    }

    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(AirwallColors.PanelDark, RoundedCornerShape(12.dp))
            .border(
                1.5.dp,
                if (isClosePass) Color(0xFFFF1744).copy(alpha = alertFlashAlpha) else AirwallColors.CardBorder,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "AREA MAP (${radiusMiles.toInt()}-MILE RADIUS)",
                color = AirwallColors.CyanMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            if (isClosePass) {
                Text(
                    text = "🚨 FLYOVER ALERT (< 2 MILES)",
                    color = Color(0xFFFF1744).copy(alpha = alertFlashAlpha),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                val count = allNearbyFlights.size
                Text(
                    text = if (count > 0) "$count IN RANGE" else "SCANNING AIRSPACE",
                    color = if (count > 0) AirwallColors.GreenStatus else AirwallColors.TextDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Radar Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(AirwallColors.PureBlack, RoundedCornerShape(8.dp))
                .border(
                    1.dp,
                    if (isClosePass) Color(0xFFFF1744).copy(alpha = 0.5f * alertFlashAlpha) else AirwallColors.CardBorder,
                    RoundedCornerShape(8.dp)
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadiusPx = min(size.width, size.height) * 0.45f
                val pxPerMile = maxRadiusPx / radiusMiles.toFloat()

                // 1. Draw subtle background coordinate grid
                drawRadarGrid(center, size, maxRadiusPx)

                // 2. Draw geographical features (Roads, Flint Airport, Lakes)
                drawLocalGeography(center, pxPerMile, textMeasurer)

                // 3. Draw Concentric Range Rings (25%, 50%, 75%, 100% of radiusMiles)
                val ringMiles = listOf(
                    radiusMiles * 0.25,
                    radiusMiles * 0.50,
                    radiusMiles * 0.75,
                    radiusMiles
                )
                for (rm in ringMiles) {
                    val rPx = (rm * pxPerMile).toFloat()
                    val isOuter = (rm == radiusMiles)
                    val ringColor = if (isOuter) AirwallColors.MagentaRing.copy(alpha = 0.85f * pulseAlpha) else AirwallColors.RadarGrid
                    val strokeWidth = if (isOuter) 2.5.dp.toPx() else 1.dp.toPx()

                    drawCircle(
                        color = ringColor,
                        radius = rPx,
                        center = center,
                        style = Stroke(
                            width = strokeWidth,
                            pathEffect = if (!isOuter) PathEffect.dashPathEffect(floatArrayOf(6f, 8f)) else null
                        )
                    )

                    // Small mile marker label on the north vertical axis for inner rings
                    if (!isOuter) {
                        val ringLabelStr = if (rm % 1.0 == 0.0) "${rm.toInt()} mi" else String.format("%.1f mi", rm)
                        val ringLabel = textMeasurer.measure(
                            text = AnnotatedString(ringLabelStr),
                            style = TextStyle(
                                color = AirwallColors.RadarGrid.copy(alpha = 0.8f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        drawText(
                            textLayoutResult = ringLabel,
                            topLeft = Offset(center.x + 4f, center.y - rPx - ringLabel.size.height - 2f)
                        )
                    }
                }

                // 3b. Flashing Red 2-mile flyover zone around our house
                if (isClosePass) {
                    val r2MiPx = (2.0 * pxPerMile).toFloat()
                    drawCircle(
                        color = Color(0xFFFF1744).copy(alpha = 0.08f * alertFlashAlpha),
                        radius = r2MiPx,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFFFF1744).copy(alpha = alertFlashAlpha),
                        radius = r2MiPx,
                        center = center,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                        )
                    )
                }

                // 4. Draw Magenta 10-mile radius label and indicator arrow
                drawRadiusLabel(center, maxRadiusPx, radiusMiles, textMeasurer)

                // 5. Draw Radar Sweep Animation if idle or as subtle background sweep
                drawRadarSweep(center, maxRadiusPx, sweepAngle, selectedFlight == null)

                // 6. Draw Home Location (AppConfig.HOME_LABEL)
                drawHomeMarker(center, textMeasurer)

                // 7. Draw Other Secondary Nearby Flights (if any)
                for (aircraft in allNearbyFlights) {
                    if (aircraft.callsign != selectedFlight?.state?.callsign) {
                        drawSecondaryAircraft(aircraft, center, pxPerMile, textMeasurer)
                    }
                }

                // 8. Draw Selected/Primary Active Flight (Flashing Red Trajectory if within 2 miles)
                selectedFlight?.let { flight ->
                    val crumbs = breadcrumbMap[flight.state.callsign.ifBlank { "ACTIVE" }] ?: emptyList()
                    drawActiveFlight(flight, crumbs, center, pxPerMile, maxRadiusPx, isClosePass, alertFlashAlpha, textMeasurer)
                }

                // 9. Draw Scale Bar in bottom-left
                drawScaleBar(size, pxPerMile, textMeasurer)
            }
        }
    }
}

private fun DrawScope.drawRadarGrid(center: Offset, size: androidx.compose.ui.geometry.Size, maxRadiusPx: Float) {
    // Crosshairs through center
    drawLine(
        color = AirwallColors.RadarGridDim,
        start = Offset(center.x, 0f),
        end = Offset(center.x, size.height),
        strokeWidth = 1f
    )
    drawLine(
        color = AirwallColors.RadarGridDim,
        start = Offset(0f, center.y),
        end = Offset(size.width, center.y),
        strokeWidth = 1f
    )

    // Diagonal angle lines (45 deg)
    val diagLength = maxRadiusPx * 1.05f
    for (deg in listOf(45.0, 135.0, 225.0, 315.0)) {
        val rad = Math.toRadians(deg)
        val end = Offset(
            center.x + (diagLength * sin(rad)).toFloat(),
            center.y - (diagLength * cos(rad)).toFloat()
        )
        drawLine(
            color = AirwallColors.RadarGridDim.copy(alpha = 0.5f),
            start = center,
            end = end,
            strokeWidth = 0.8f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 10f))
        )
    }
}

private fun DrawScope.drawLocalGeography(center: Offset, pxPerMile: Float, textMeasurer: TextMeasurer) {
    if (AppConfig.LOCAL_AIRPORT_CODE.isBlank() || AppConfig.LOCAL_AIRPORT_LAT == 0.0) return

    val lat1 = AppConfig.HOME_LAT
    val lon1 = AppConfig.HOME_LON
    val lat2 = AppConfig.LOCAL_AIRPORT_LAT
    val lon2 = AppConfig.LOCAL_AIRPORT_LON

    val latDegPerMile = 1.0 / 69.0
    val lonDegPerMile = 1.0 / (69.0 * cos(Math.toRadians(lat1)))

    val aptEastMiles = (lon2 - lon1) / lonDegPerMile
    val aptNorthMiles = (lat2 - lat1) / latDegPerMile

    val aptCenter = Offset(
        center.x + (aptEastMiles.toFloat() * pxPerMile),
        center.y - (aptNorthMiles.toFloat() * pxPerMile)
    )

    // Draw Airport runway line
    val runwayLength = 1.4f * pxPerMile
    val runwayAngleRad = Math.toRadians(AppConfig.LOCAL_AIRPORT_RUNWAY_ANGLE_DEG)
    val rStart = Offset(
        aptCenter.x - (runwayLength / 2f * cos(runwayAngleRad)).toFloat(),
        aptCenter.y - (runwayLength / 2f * sin(runwayAngleRad)).toFloat()
    )
    val rEnd = Offset(
        aptCenter.x + (runwayLength / 2f * cos(runwayAngleRad)).toFloat(),
        aptCenter.y + (runwayLength / 2f * sin(runwayAngleRad)).toFloat()
    )
    drawLine(
        color = Color(0xFF385572),
        start = rStart,
        end = rEnd,
        strokeWidth = 3f
    )

    // Airport label box
    val aptText = textMeasurer.measure(
        text = AnnotatedString(AppConfig.LOCAL_AIRPORT_CODE),
        style = TextStyle(color = Color(0xFF7E9EB8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    )
    val tagWidth = aptText.size.width + 12f
    val tagHeight = aptText.size.height + 6f
    drawRoundRect(
        color = Color(0xFF0F1E2E),
        topLeft = Offset(aptCenter.x - tagWidth / 2f, aptCenter.y - tagHeight - 8f),
        size = androidx.compose.ui.geometry.Size(tagWidth, tagHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
    )
    drawRoundRect(
        color = Color(0xFF234567),
        topLeft = Offset(aptCenter.x - tagWidth / 2f, aptCenter.y - tagHeight - 8f),
        size = androidx.compose.ui.geometry.Size(tagWidth, tagHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
        style = Stroke(width = 1f)
    )
    drawText(
        textLayoutResult = aptText,
        topLeft = Offset(aptCenter.x - aptText.size.width / 2f, aptCenter.y - tagHeight - 5f)
    )
}

private fun DrawScope.drawRadiusLabel(
    center: Offset,
    maxRadiusPx: Float,
    radiusMiles: Double,
    textMeasurer: TextMeasurer
) {
    // Draw diagonal arrow pointing from center to perimeter
    val angleDeg = 38.0
    val angleRad = Math.toRadians(angleDeg)
    val endX = center.x + (maxRadiusPx * cos(angleRad)).toFloat()
    val endY = center.y - (maxRadiusPx * sin(angleRad)).toFloat()

    // Line from near center to ring
    drawLine(
        color = AirwallColors.MagentaRing.copy(alpha = 0.65f),
        start = Offset(center.x + 35f * cos(angleRad).toFloat(), center.y - 35f * sin(angleRad).toFloat()),
        end = Offset(endX, endY),
        strokeWidth = 1.5f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
    )

    // Arrowhead at ring
    val arrowLen = 14f
    val arrowAngle1 = angleRad + Math.PI - 0.35
    val arrowAngle2 = angleRad + Math.PI + 0.35
    drawLine(
        color = AirwallColors.MagentaRing,
        start = Offset(endX, endY),
        end = Offset(endX + (arrowLen * cos(arrowAngle1)).toFloat(), endY - (arrowLen * sin(arrowAngle1)).toFloat()),
        strokeWidth = 2f
    )
    drawLine(
        color = AirwallColors.MagentaRing,
        start = Offset(endX, endY),
        end = Offset(endX + (arrowLen * cos(arrowAngle2)).toFloat(), endY - (arrowLen * sin(arrowAngle2)).toFloat()),
        strokeWidth = 2f
    )

    // Text along the radius line
    val labelText = textMeasurer.measure(
        text = AnnotatedString("${radiusMiles.toInt()}-MILE RADIUS • ${AppConfig.HOME_LABEL}"),
        style = TextStyle(
            color = AirwallColors.MagentaRing,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    )

    val midX = center.x + (maxRadiusPx * 0.55f * cos(angleRad)).toFloat()
    val midY = center.y - (maxRadiusPx * 0.55f * sin(angleRad)).toFloat()

    rotate(degrees = -angleDeg.toFloat(), pivot = Offset(midX, midY)) {
        drawText(
            textLayoutResult = labelText,
            topLeft = Offset(midX - labelText.size.width / 2f, midY - labelText.size.height - 4f)
        )
    }
}

private fun DrawScope.drawRadarSweep(
    center: Offset,
    maxRadiusPx: Float,
    sweepAngle: Float,
    isIdle: Boolean
) {
    val alphaMultiplier = if (isIdle) 0.35f else 0.15f
    // Draw sweep sector with brush gradient
    val sweepBrush = Brush.sweepGradient(
        colors = listOf(
            Color.Transparent,
            Color.Transparent,
            AirwallColors.CyanGlow.copy(alpha = 0.05f * alphaMultiplier),
            AirwallColors.CyanGlow.copy(alpha = 0.35f * alphaMultiplier),
            AirwallColors.CyanGlow.copy(alpha = 0.7f * alphaMultiplier)
        ),
        center = center
    )

    rotate(degrees = sweepAngle - 90f, pivot = center) {
        drawCircle(
            brush = sweepBrush,
            radius = maxRadiusPx,
            center = center
        )

        // Bright leading sweep line
        drawLine(
            color = AirwallColors.CyanGlow.copy(alpha = if (isIdle) 0.85f else 0.4f),
            start = center,
            end = Offset(center.x + maxRadiusPx, center.y),
            strokeWidth = 1.8f
        )
    }
}

private fun DrawScope.drawHomeMarker(center: Offset, textMeasurer: TextMeasurer) {
    // 1. Backyard guide line
    val backAngleRad = Math.toRadians(AppConfig.BACK_DOOR_HEADING)
    val backGuideLen = 56f
    val backEnd = Offset(
        center.x + (backGuideLen * sin(backAngleRad)).toFloat(),
        center.y - (backGuideLen * cos(backAngleRad)).toFloat()
    )
    drawLine(
        color = AirwallColors.GreenStatus.copy(alpha = 0.6f),
        start = center,
        end = backEnd,
        strokeWidth = 1.5f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
    )
    val backText = textMeasurer.measure(
        text = AnnotatedString(AppConfig.BACKYARD_LABEL),
        style = TextStyle(color = AirwallColors.GreenStatus, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    )
    val backOffsetX = if (sin(backAngleRad) >= 0) backEnd.x + 6f else backEnd.x - backText.size.width - 6f
    drawText(
        textLayoutResult = backText,
        topLeft = Offset(backOffsetX, backEnd.y - backText.size.height / 2f)
    )

    // 2. Front yard guide line
    val frontAngleRad = Math.toRadians(AppConfig.FRONT_DOOR_HEADING)
    val frontGuideLen = 56f
    val frontEnd = Offset(
        center.x + (frontGuideLen * sin(frontAngleRad)).toFloat(),
        center.y - (frontGuideLen * cos(frontAngleRad)).toFloat()
    )
    drawLine(
        color = AirwallColors.YellowTag.copy(alpha = 0.6f),
        start = center,
        end = frontEnd,
        strokeWidth = 1.5f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
    )
    val frontText = textMeasurer.measure(
        text = AnnotatedString(AppConfig.FRONT_YARD_LABEL),
        style = TextStyle(color = AirwallColors.YellowTag, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    )
    val frontOffsetX = if (sin(frontAngleRad) >= 0) frontEnd.x + 6f else frontEnd.x - frontText.size.width - 6f
    drawText(
        textLayoutResult = frontText,
        topLeft = Offset(frontOffsetX, frontEnd.y - frontText.size.height / 2f)
    )

    // 3. House symbol at center
    drawCircle(
        color = AirwallColors.CyanGlow.copy(alpha = 0.25f),
        radius = 16f,
        center = center
    )
    drawCircle(
        color = AirwallColors.CyanGlow,
        radius = 5f,
        center = center
    )
    drawCircle(
        color = AirwallColors.PureBlack,
        radius = 2.5f,
        center = center
    )

    // HOME Tag positioned cleanly ABOVE center dot
    val homeText = textMeasurer.measure(
        text = AnnotatedString(AppConfig.HOME_LABEL),
        style = TextStyle(color = AirwallColors.CyanGlow, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    )
    val badgeTop = center.y - homeText.size.height - 12f
    drawRoundRect(
        color = Color(0xDD002B3D),
        topLeft = Offset(center.x - homeText.size.width / 2f - 4f, badgeTop),
        size = androidx.compose.ui.geometry.Size(homeText.size.width + 8f, homeText.size.height + 4f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
    )
    drawText(
        textLayoutResult = homeText,
        topLeft = Offset(center.x - homeText.size.width / 2f, badgeTop + 2f)
    )
}

private fun DrawScope.drawSecondaryAircraft(
    aircraft: AircraftState,
    center: Offset,
    pxPerMile: Float,
    textMeasurer: TextMeasurer
) {
    val (px, py) = calculateScreenPos(aircraft, center, pxPerMile)

    // Aircraft symbol (dimmer cyan/blue for secondary flights)
    drawAircraftIcon(Offset(px, py), aircraft.heading, AirwallColors.CyanMuted.copy(alpha = 0.7f), outlineColor = Color(0xFF001520), sizePx = 18f)

    // Callsign text
    val label = textMeasurer.measure(
        text = AnnotatedString(aircraft.callsign),
        style = TextStyle(color = AirwallColors.TextMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    )
    drawText(
        textLayoutResult = label,
        topLeft = Offset(px + 12f, py - label.size.height / 2f)
    )
}

private fun DrawScope.drawActiveFlight(
    flight: EnrichedFlight,
    breadcrumbs: List<Pair<Double, Double>>,
    center: Offset,
    pxPerMile: Float,
    maxRadiusPx: Float,
    isClosePass: Boolean,
    alertFlashAlpha: Float,
    textMeasurer: TextMeasurer
) {
    val state = flight.state
    val (px, py) = calculateScreenPos(state, center, pxPerMile)
    val aircraftPos = Offset(px, py)

    // 1. Flight path line (always projected ahead to the outer edge of the radar screen)
    val trackAngleRad = Math.toRadians((state.heading - 90.0))
    val tailDist = 65f
    val vx = cos(trackAngleRad).toFloat()
    val vy = sin(trackAngleRad).toFloat()

    // Ray-circle intersection to extend the forward flight path cleanly to the outer radar perimeter (maxRadiusPx)
    val dx = aircraftPos.x - center.x
    val dy = aircraftPos.y - center.y
    val b = 2f * (dx * vx + dy * vy)
    val c = (dx * dx + dy * dy) - (maxRadiusPx * maxRadiusPx)
    val discriminant = b * b - 4f * c

    val headDist = if (discriminant >= 0f) {
        val t2 = (-b + sqrt(discriminant)) / 2f
        if (t2 > 15f) t2 else maxRadiusPx * 2.2f
    } else {
        maxRadiusPx * 2.2f
    }

    val trailStart = Offset(
        aircraftPos.x - (tailDist * vx),
        aircraftPos.y - (tailDist * vy)
    )
    val trailEnd = Offset(
        aircraftPos.x + (headDist * vx),
        aircraftPos.y + (headDist * vy)
    )

    if (isClosePass) {
        val redNeon = Color(0xFFFF1744)

        // 1a. Wide glowing neon-red aura
        drawLine(
            color = redNeon.copy(alpha = 0.35f * alertFlashAlpha),
            start = aircraftPos,
            end = trailEnd,
            strokeWidth = 9.dp.toPx(),
            cap = StrokeCap.Round
        )

        // 1b. Flashing vivid red dashed trajectory vector
        drawLine(
            color = redNeon.copy(alpha = alertFlashAlpha),
            start = aircraftPos,
            end = trailEnd,
            strokeWidth = 3.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f))
        )

        // 1c. Solid past trail in deep crimson (actual GPS breadcrumbs if available)
        if (breadcrumbs.size >= 2) {
            val path = Path()
            val p0 = calculateScreenPos(state.copy(latitude = breadcrumbs[0].first, longitude = breadcrumbs[0].second), center, pxPerMile)
            path.moveTo(p0.first, p0.second)
            for (i in 1 until breadcrumbs.size) {
                val pt = calculateScreenPos(state.copy(latitude = breadcrumbs[i].first, longitude = breadcrumbs[i].second), center, pxPerMile)
                path.lineTo(pt.first, pt.second)
            }
            path.lineTo(aircraftPos.x, aircraftPos.y)
            drawPath(path = path, color = Color(0xFFD50000).copy(alpha = 0.85f), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        } else {
            drawLine(
                color = Color(0xFFD50000).copy(alpha = 0.85f),
                start = trailStart,
                end = aircraftPos,
                strokeWidth = 3.dp.toPx()
            )
        }

        // 1d. Flashing point at radar boundary
        drawCircle(
            color = redNeon.copy(alpha = alertFlashAlpha),
            radius = 4.5.dp.toPx(),
            center = trailEnd
        )
    } else {
        // Standard Ahead Trajectory: soft glow under-trail
        drawLine(
            color = AirwallColors.CyanGlow.copy(alpha = 0.25f),
            start = aircraftPos,
            end = trailEnd,
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Crisp dashed cyan trajectory extending all the way to radar edge
        drawLine(
            color = AirwallColors.CyanGlow.copy(alpha = 0.85f),
            start = aircraftPos,
            end = trailEnd,
            strokeWidth = 2.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
        )

        // Solid past trail (actual GPS breadcrumbs if available)
        if (breadcrumbs.size >= 2) {
            val path = Path()
            val p0 = calculateScreenPos(state.copy(latitude = breadcrumbs[0].first, longitude = breadcrumbs[0].second), center, pxPerMile)
            path.moveTo(p0.first, p0.second)
            for (i in 1 until breadcrumbs.size) {
                val pt = calculateScreenPos(state.copy(latitude = breadcrumbs[i].first, longitude = breadcrumbs[i].second), center, pxPerMile)
                path.lineTo(pt.first, pt.second)
            }
            path.lineTo(aircraftPos.x, aircraftPos.y)
            drawPath(path = path, color = AirwallColors.CyanGlow, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        } else {
            drawLine(
                color = AirwallColors.CyanGlow,
                start = trailStart,
                end = aircraftPos,
                strokeWidth = 2.5.dp.toPx()
            )
        }

        // Target point at radar perimeter
        drawCircle(
            color = AirwallColors.CyanGlow.copy(alpha = 0.75f),
            radius = 3.5.dp.toPx(),
            center = trailEnd
        )
    }

    // 2. Aircraft Icon: Bright Pure White with dark contrast outline (distinct from Cyan/Red flight path)
    val planeFill = Color(0xFFFFFFFF)
    val planeOutline = if (isClosePass) Color(0xFFFF1744).copy(alpha = alertFlashAlpha) else Color(0xFF001926)

    // Glowing alert halo for close flyover
    if (isClosePass) {
        drawCircle(
            color = Color(0xFFFF1744).copy(alpha = 0.35f * alertFlashAlpha),
            radius = 20.dp.toPx(),
            center = aircraftPos
        )
    }
    drawAircraftIcon(aircraftPos, state.heading, fillColor = planeFill, outlineColor = planeOutline, sizePx = 28f)

    // 3. Callsign Badge
    val callsignStr = if (isClosePass) "⚠️ ${flight.metadata.flightNumber.ifBlank { state.callsign }}" else flight.metadata.flightNumber.ifBlank { state.callsign }
    val callsignLayout = textMeasurer.measure(
        text = AnnotatedString(callsignStr),
        style = TextStyle(color = Color(0xFF000000), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    )

    val pillWidth = callsignLayout.size.width + 16f
    val pillHeight = callsignLayout.size.height + 6f

    // Clamp or flip to left if near right edge to prevent clipping
    val pillLeft = if (aircraftPos.x + 18f + pillWidth > size.width - 8f) {
        aircraftPos.x - pillWidth - 14f
    } else {
        aircraftPos.x + 18f
    }
    val pillTopLeft = Offset(pillLeft, aircraftPos.y - pillHeight / 2f)

    // Glowing pill (Flashing bright coral-red if close pass, yellow otherwise)
    val pillColor = if (isClosePass) Color(0xFFFF5252) else AirwallColors.YellowTag
    drawRoundRect(
        color = pillColor,
        topLeft = pillTopLeft,
        size = androidx.compose.ui.geometry.Size(pillWidth, pillHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
    )
    drawText(
        textLayoutResult = callsignLayout,
        topLeft = Offset(pillTopLeft.x + 8f, pillTopLeft.y + 3f)
    )

    // 4. Telemetry tag below callsign
    val speedMph = (state.speedKnots * 1.15078).toInt()
    val telemetryStr = if (isClosePass) "${state.altitudeFeet} FT • $speedMph MPH • FLYOVER!" else "${state.altitudeFeet} FT • $speedMph MPH"
    val telemetryLayout = textMeasurer.measure(
        text = AnnotatedString(telemetryStr),
        style = TextStyle(color = if (isClosePass) Color(0xFFFF8A80) else AirwallColors.CyanGlow, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    )
    drawRoundRect(
        color = Color(0xEE00121C),
        topLeft = Offset(pillTopLeft.x - 3f, pillTopLeft.y + pillHeight + 2f),
        size = androidx.compose.ui.geometry.Size(telemetryLayout.size.width + 6f, telemetryLayout.size.height + 3f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
    )
    drawText(
        textLayoutResult = telemetryLayout,
        topLeft = Offset(pillTopLeft.x, pillTopLeft.y + pillHeight + 3f)
    )
}

private fun DrawScope.drawAircraftIcon(
    pos: Offset,
    heading: Float,
    fillColor: Color,
    outlineColor: Color = Color(0xFF001520),
    sizePx: Float
) {
    rotate(degrees = heading, pivot = pos) {
        val s = sizePx / 2f
        val path = Path().apply {
            // Fuselage and nose
            moveTo(pos.x, pos.y - s)
            lineTo(pos.x + s * 0.15f, pos.y - s * 0.4f)
            // Main wings
            lineTo(pos.x + s * 0.95f, pos.y + s * 0.15f)
            lineTo(pos.x + s * 0.95f, pos.y + s * 0.35f)
            lineTo(pos.x + s * 0.18f, pos.y + s * 0.25f)
            // Fuselage aft
            lineTo(pos.x + s * 0.15f, pos.y + s * 0.7f)
            // Horizontal stabilizer
            lineTo(pos.x + s * 0.45f, pos.y + s * 0.95f)
            lineTo(pos.x + s * 0.45f, pos.y + s)
            lineTo(pos.x, pos.y + s * 0.85f)
            // Left horizontal stabilizer
            lineTo(pos.x - s * 0.45f, pos.y + s)
            lineTo(pos.x - s * 0.45f, pos.y + s * 0.95f)
            lineTo(pos.x - s * 0.15f, pos.y + s * 0.7f)
            // Left wing
            lineTo(pos.x - s * 0.18f, pos.y + s * 0.25f)
            lineTo(pos.x - s * 0.95f, pos.y + s * 0.35f)
            lineTo(pos.x - s * 0.95f, pos.y + s * 0.15f)
            lineTo(pos.x - s * 0.15f, pos.y - s * 0.4f)
            close()
        }
        // Dark contrast stroke first so wings and fuselage stand out cleanly over any lines
        drawPath(path = path, color = outlineColor, style = Stroke(width = 3.dp.toPx()))
        // Solid plane fill
        drawPath(path = path, color = fillColor)
    }
}

private fun DrawScope.drawScaleBar(size: androidx.compose.ui.geometry.Size, pxPerMile: Float, textMeasurer: TextMeasurer) {
    val miles = 2.0f
    val barWidth = miles * pxPerMile
    val barHeight = 8f
    val left = 24f
    val bottom = size.height - 24f

    // Background bar
    drawRect(
        color = Color(0xAA070E17),
        topLeft = Offset(left - 6f, bottom - 26f),
        size = androidx.compose.ui.geometry.Size(barWidth + 60f, 34f)
    )

    // Tick marks
    drawLine(color = Color.White, start = Offset(left, bottom), end = Offset(left + barWidth, bottom), strokeWidth = 2.5f)
    drawLine(color = Color.White, start = Offset(left, bottom - barHeight), end = Offset(left, bottom + 2f), strokeWidth = 2.5f)
    drawLine(color = Color.White, start = Offset(left + barWidth / 2f, bottom - barHeight * 0.6f), end = Offset(left + barWidth / 2f, bottom + 2f), strokeWidth = 2f)
    drawLine(color = Color.White, start = Offset(left + barWidth, bottom - barHeight), end = Offset(left + barWidth, bottom + 2f), strokeWidth = 2.5f)

    // Text labels
    val zeroText = textMeasurer.measure(AnnotatedString("0"), TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold))
    drawText(zeroText, topLeft = Offset(left - 2f, bottom - 22f))

    val labelText = textMeasurer.measure(AnnotatedString("2 miles"), TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold))
    drawText(labelText, topLeft = Offset(left + barWidth + 8f, bottom - 12f))
}

private fun calculateScreenPos(
    aircraft: AircraftState,
    center: Offset,
    pxPerMile: Float
): Pair<Float, Float> {
    // Relative distance in statute miles from home center
    val lat1 = AppConfig.HOME_LAT
    val lon1 = AppConfig.HOME_LON
    val lat2 = aircraft.latitude
    val lon2 = aircraft.longitude

    val latDegPerMile = 1.0 / 69.0
    val lonDegPerMile = 1.0 / (69.0 * cos(Math.toRadians(lat1)))

    val dxMiles = (lon2 - lon1) / lonDegPerMile
    val dyMiles = (lat1 - lat2) / latDegPerMile // South is +Y in screen space

    val px = center.x + (dxMiles * pxPerMile).toFloat()
    val py = center.y + (dyMiles * pxPerMile).toFloat()
    return Pair(px, py)
}
