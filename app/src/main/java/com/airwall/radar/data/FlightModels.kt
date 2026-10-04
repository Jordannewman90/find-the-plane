package com.airwall.radar.data

data class AircraftState(
    val icao24: String,
    val callsign: String,
    val originCountry: String,
    val longitude: Double,
    val latitude: Double,
    val altitudeFeet: Int,
    val speedKnots: Int,
    val heading: Float,
    val verticalRateFpm: Int,
    val squawk: String?,
    val distanceMiles: Double,
    val bearingDeg: Double,
    val onGround: Boolean = false,
    val lastContactEpoch: Long = 0L,
    val typeCode: String = "",
    val registration: String = "",
    val description: String = ""
)

enum class SpotterStage {
    MONITORING_INBOUND, // > 7 miles: too far to see from yard, watch on radar
    GET_READY,          // ~4.5 to 7 miles: approaching, get ready by door in 45-90s
    RUN_OUTSIDE_NOW,    // In visual range: run to front or back door now!
    DIRECT_FLYOVER,     // < 2 miles: direct flyover, look straight up!
    FADING_AWAY         // Passed us, flying away into distance
}

data class KidSpotterGuide(
    val doorToRunTo: String,
    val directionToLook: String,
    val cardinalDirection: String,
    val spotterTip: String,
    val distanceText: String,
    val altitudeText: String,
    val speedMphText: String,
    val isApproaching: Boolean,
    val stage: SpotterStage = SpotterStage.RUN_OUTSIDE_NOW,
    val elevationAngleDeg: Int = 0,
    val etaSeconds: Int? = null
)

data class FlightMetadata(
    val callsign: String,
    val flightNumber: String,
    val airlineName: String,
    val airlineCode: String,
    val originAirportCode: String,
    val originAirportName: String,
    val originCity: String,
    val destinationAirportCode: String,
    val destinationAirportName: String,
    val destinationCity: String,
    val aircraftType: String,
    val aircraftModelName: String,
    val registration: String,
    val etaTime: String,
    val etaMinutesRemaining: Int,
    val assetImagePath: String,
    val kidGuide: KidSpotterGuide
)

data class EnrichedFlight(
    val state: AircraftState,
    val metadata: FlightMetadata
)
