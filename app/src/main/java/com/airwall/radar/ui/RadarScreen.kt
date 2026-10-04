package com.airwall.radar.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import com.airwall.radar.data.AircraftState
import com.airwall.radar.data.EnrichedFlight
import com.airwall.radar.data.FlightEnricher
import com.airwall.radar.data.OpenSkyApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RadarScreen() {
    var radiusMiles by remember { mutableStateOf(10.0) }
    var isSimulationMode by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf(0) }

    var allNearbyFlights by remember { mutableStateOf<List<AircraftState>>(emptyList()) }
    var enrichedFlight by remember { mutableStateOf<EnrichedFlight?>(null) }

    val focusRequester = remember { FocusRequester() }

    // Live ADS-B polling loop
    LaunchedEffect(radiusMiles, isSimulationMode) {
        while (true) {
            if (isSimulationMode) {
                // If demo mode is active, simulate realistic flight movement of UA345
                val demo = FlightEnricher.createDemoFlight()
                // Slowly advance plane across local airspace
                val elapsedSec = (System.currentTimeMillis() / 1000) % 300
                val angleRad = Math.toRadians(305.0) // heading NW
                val movedDist = (elapsedSec * 0.04) % 15.0 - 5.0
                val newLon = OpenSkyApi.HOME_LON + (movedDist * sin(angleRad)) / 50.5
                val newLat = OpenSkyApi.HOME_LAT + (movedDist * cos(angleRad)) / 69.0
                val curDist = OpenSkyApi.calculateDistanceMiles(OpenSkyApi.HOME_LAT, OpenSkyApi.HOME_LON, newLat, newLon)
                val curBearing = OpenSkyApi.calculateBearing(OpenSkyApi.HOME_LAT, OpenSkyApi.HOME_LON, newLat, newLon)

                val simulatedState = demo.state.copy(
                    longitude = newLon,
                    latitude = newLat,
                    distanceMiles = curDist,
                    bearingDeg = curBearing
                )
                val updatedMeta = demo.metadata.copy(
                    kidGuide = FlightEnricher.calculateKidSpotterGuide(simulatedState)
                )
                enrichedFlight = demo.copy(state = simulatedState, metadata = updatedMeta)
                allNearbyFlights = listOf(simulatedState)
                delay(1000L)
            } else {
                // Fetch real live aircraft from OpenSky Network
                val liveAircraft = withContext(Dispatchers.IO) {
                    OpenSkyApi.fetchAircraftInRadius(radiusMiles)
                }
                allNearbyFlights = liveAircraft

                if (liveAircraft.isNotEmpty()) {
                    val safeIndex = selectedIndex.coerceIn(0, liveAircraft.size - 1)
                    val primaryAircraft = liveAircraft[safeIndex]
                    val enriched = withContext(Dispatchers.IO) {
                        FlightEnricher.enrich(primaryAircraft)
                    }
                    enrichedFlight = enriched
                } else {
                    enrichedFlight = null
                }
                // Poll live ADS-B every 4 seconds
                delay(4000L)
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    OledProtectionWrapper(
        modifier = Modifier
            .fillMaxSize()
            .background(AirwallColors.PureBlack)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            // Toggle Demo Simulation
                            isSimulationMode = !isSimulationMode
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (allNearbyFlights.isNotEmpty()) {
                                selectedIndex = (selectedIndex + 1) % allNearbyFlights.size
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (allNearbyFlights.isNotEmpty()) {
                                selectedIndex = (selectedIndex - 1 + allNearbyFlights.size) % allNearbyFlights.size
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            radiusMiles = when (radiusMiles) {
                                10.0 -> 20.0
                                20.0 -> 30.0
                                else -> 10.0
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            radiusMiles = when (radiusMiles) {
                                30.0 -> 20.0
                                20.0 -> 10.0
                                else -> 30.0
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Cockpit display outer bezel
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF141920), Color(0xFF070B10), Color(0xFF0F141A))
                    ),
                    RoundedCornerShape(16.dp)
                )
                .border(2.dp, Color(0xFF263545), RoundedCornerShape(16.dp))
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Panel: Radar Map (1:1 aspect area)
                RadarMapView(
                    selectedFlight = enrichedFlight,
                    allNearbyFlights = allNearbyFlights,
                    radiusMiles = radiusMiles,
                    modifier = Modifier.weight(1f)
                )

                // Right Panel: Telemetry & Flight Overview
                FlightOverviewPanel(
                    flight = enrichedFlight,
                    isSimulationMode = isSimulationMode,
                    modifier = Modifier.weight(1.05f)
                )
            }
        }
    }
}
