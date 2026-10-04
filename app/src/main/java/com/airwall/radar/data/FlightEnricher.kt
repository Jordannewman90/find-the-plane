package com.airwall.radar.data

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.*

object FlightEnricher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // Cache responses so we don't hammer free endpoints
    private val routeCache = ConcurrentHashMap<String, RouteInfo>()
    private val aircraftCache = ConcurrentHashMap<String, AircraftInfo>()

    data class RouteInfo(
        val callsignIata: String,
        val airlineName: String,
        val airlineCode: String,
        val originCode: String,
        val originName: String,
        val originCity: String,
        val originLat: Double?,
        val originLon: Double?,
        val destCode: String,
        val destName: String,
        val destCity: String,
        val destLat: Double?,
        val destLon: Double?
    )

    data class AircraftInfo(
        val modelName: String,
        val icaoTypeCode: String,
        val registration: String
    )

    fun enrich(state: AircraftState): EnrichedFlight {
        val route = getOrFetchRoute(state.callsign)
        val aircraft = if (state.typeCode.isBlank() || state.registration.isBlank()) {
            getOrFetchAircraft(state.icao24)
        } else {
            AircraftInfo(resolveModelNameFromTypeCode(state.typeCode).ifBlank { "Commercial Aircraft" }, state.typeCode, state.registration)
        }

        val effectiveTypeCode = if (state.typeCode.isNotBlank()) state.typeCode else aircraft.icaoTypeCode
        val effectiveReg = if (state.registration.isNotBlank()) state.registration else aircraft.registration

        val flightNum = if (!route.callsignIata.isNullOrBlank()) route.callsignIata else state.callsign
        val airlineCode = when {
            route.airlineCode.isNotBlank() -> route.airlineCode
            state.callsign.startsWith("UAL") || state.callsign.startsWith("UA") -> "UAL"
            state.callsign.startsWith("DAL") || state.callsign.startsWith("DL") -> "DAL"
            state.callsign.startsWith("AAL") || state.callsign.startsWith("AA") -> "AAL"
            state.callsign.startsWith("SWA") || state.callsign.startsWith("WN") -> "SWA"
            state.callsign.startsWith("ROU") || state.callsign.startsWith("ACA") -> "ACA"
            state.callsign.startsWith("RPA") -> "RPA"
            state.callsign.startsWith("EDV") -> "EDV"
            state.callsign.startsWith("SKW") -> "SKW"
            else -> ""
        }

        val airlineName = when {
            route.airlineName.isNotBlank() -> route.airlineName
            airlineCode == "UAL" -> "United Airlines"
            airlineCode == "DAL" -> "Delta Air Lines"
            airlineCode == "AAL" -> "American Airlines"
            airlineCode == "SWA" -> "Southwest Airlines"
            airlineCode == "ACA" -> "Air Canada"
            airlineCode == "RPA" -> "Republic Airways"
            airlineCode == "EDV" -> "Endeavor Air"
            airlineCode == "SKW" -> "SkyWest Airlines"
            state.callsign.startsWith("N") -> "General Aviation"
            else -> state.originCountry
        }

        // Determine route phase and status text
        val (phase, statusText) = determinePhaseAndStatus(state)

        // Calculate progress percentage and ETA
        val (progress, etaTime, etaMinutes) = calculateProgressAndEta(state, route)

        // Refine model name if hexdb returned generic or fallback for GA
        val knownModel = resolveModelNameFromTypeCode(effectiveTypeCode)
        val refinedModelName = when {
            state.description.isNotBlank() -> state.description
            knownModel.isNotBlank() -> knownModel
            aircraft.modelName.isNotBlank() && !aircraft.modelName.contains("Boeing 737") -> aircraft.modelName
            state.callsign.startsWith("N") && state.speedKnots < 230 -> "Light Aircraft (Propeller)"
            airlineCode.isNotBlank() -> aircraft.modelName
            else -> aircraft.modelName
        }

        // Match aircraft image: try live photo from Planespotters first, fallback to bundled assets
        val livePhotoUrl = fetchPlanespottersPhoto(effectiveReg, state.icao24)
        val assetImage = livePhotoUrl ?: resolveAssetImage(effectiveTypeCode, airlineCode, state.callsign, state.speedKnots)

        // Calculate where the kids should run to look!
        val kidGuide = calculateKidSpotterGuide(state)

        val metadata = FlightMetadata(
            callsign = state.callsign,
            flightNumber = flightNum,
            airlineName = airlineName,
            airlineCode = airlineCode,
            originAirportCode = route.originCode,
            originAirportName = route.originName,
            originCity = route.originCity,
            destinationAirportCode = route.destCode,
            destinationAirportName = route.destName,
            destinationCity = route.destCity,
            aircraftType = effectiveTypeCode,
            aircraftModelName = refinedModelName,
            registration = if (effectiveReg.isNotBlank()) effectiveReg else state.callsign,
            etaTime = etaTime,
            etaMinutesRemaining = etaMinutes,
            assetImagePath = assetImage,
            kidGuide = kidGuide
        )

        return EnrichedFlight(state, metadata)
    }

    fun resolveModelNameFromTypeCode(typeCode: String): String {
        val tc = typeCode.uppercase().trim()
        return when (tc) {
            "B738" -> "Boeing 737-800"
            "B739" -> "Boeing 737-900"
            "B737" -> "Boeing 737"
            "B38M" -> "Boeing 737 MAX 8"
            "B39M" -> "Boeing 737 MAX 9"
            "B772" -> "Boeing 777-200"
            "B77W" -> "Boeing 777-300ER"
            "B788" -> "Boeing 787-8 Dreamliner"
            "B789" -> "Boeing 787-9 Dreamliner"
            "A320" -> "Airbus A320"
            "A321" -> "Airbus A321"
            "A21N" -> "Airbus A321neo"
            "A20N" -> "Airbus A320neo"
            "A319" -> "Airbus A319"
            "E75L", "E75S" -> "Embraer 175"
            "E190" -> "Embraer 190"
            "CRJ9" -> "Bombardier CRJ-900"
            "CRJ7" -> "Bombardier CRJ-700"
            "CRJ2" -> "Bombardier CRJ-200"
            "P28A" -> "Piper PA-28 Cherokee"
            "PA28" -> "Piper PA-28"
            "C172" -> "Cessna 172 Skyhawk"
            "C182" -> "Cessna 182 Skylane"
            "DV20", "DA20" -> "Diamond DA20 Katana"
            "DA40" -> "Diamond DA40 Star"
            "SR22" -> "Cirrus SR22"
            "C25B", "C25A" -> "Cessna Citation CJ3"
            "C56X" -> "Cessna Citation Excel"
            "GLF4" -> "Gulfstream IV"
            "GLF5" -> "Gulfstream V"
            "EC55" -> "Eurocopter EC155 (Survival Flight)"
            "B407" -> "Bell 407 Helicopter"
            else -> ""
        }
    }

    fun calculateKidSpotterGuide(state: AircraftState): KidSpotterGuide {
        val bearing = state.bearingDeg
        val dist = state.distanceMiles
        val alt = state.altitudeFeet
        val speedKts = state.speedKnots
        val speedMph = (speedKts * 1.15078).roundToInt()

        // 1. Calculate true sightline elevation angle above the horizon (degrees)
        // alt is in feet (5,280 ft/mile), dist is in statute miles
        val altMiles = alt / 5280.0
        val elevAngleDeg = Math.toDegrees(atan2(altMiles, max(0.1, dist))).roundToInt()

        // 2. Is aircraft approaching house or flying away?
        val bearingToHouse = (bearing + 180.0) % 360.0
        val angleToHouse = abs((state.heading - bearingToHouse + 180.0) % 360.0 - 180.0)
        val isApproaching = angleToHouse < 90.0

        val willCross2Mi = OpenSkyApi.willCrossWithin2Miles(state)

        // Estimated seconds to reach closest point to house
        val etaSeconds: Int? = if (isApproaching && speedMph > 25) {
            ((dist / speedMph) * 3600).roundToInt().coerceAtLeast(5)
        } else null

        // 3. Determine realistic physical spotting stage
        val stage = when {
            // Direct flyover: within 2 miles OR heading within 2 miles and already within 2.8 miles
            dist <= 2.0 || (willCross2Mi && dist <= 2.8) -> SpotterStage.DIRECT_FLYOVER

            // Real-world visual range (clearing trees and visible to naked eye):
            // Cruising jets: visible at <= 4.5 miles (or high angle >= 32° up to 5.2 mi)
            // Low planes (props/approach): visible at <= 3.2 miles
            isApproaching && (dist <= 4.2 || (alt >= 18000 && dist <= 5.2 && elevAngleDeg >= 30)) -> SpotterStage.RUN_OUTSIDE_NOW
            !isApproaching && dist <= 3.2 -> SpotterStage.RUN_OUTSIDE_NOW

            // Arriving shortly (4.2 - 7.5 miles): get shoes ready!
            isApproaching && dist in 4.2..7.5 -> SpotterStage.GET_READY

            // Approaching but too far for yard (> 7.5 miles): watch on radar
            isApproaching -> SpotterStage.MONITORING_INBOUND

            // Past us and flying away (> 3.2 miles)
            else -> SpotterStage.FADING_AWAY
        }

        // House door headings and labels from AppConfig:
        val frontDoorFacing = AppConfig.FRONT_DOOR_HEADING
        val diffFromFront = ((bearing - frontDoorFacing + 540.0) % 360.0) - 180.0
        val isFront = abs(diffFromFront) <= 90.0
        val doorTarget = if (isFront) AppConfig.FRONT_DOOR_LABEL else AppConfig.BACK_DOOR_LABEL
        val cardinal = when {
            bearing in 45.0..135.0 -> "EAST"
            bearing in 135.0..225.0 -> "SOUTH"
            bearing in 225.0..315.0 -> "WEST"
            else -> "NORTH"
        }

        // Relative angle from the exact perspective of the person stepping outside that door:
        // Negative = to their LEFT, Positive = to their RIGHT, ~0 = STRAIGHT ahead
        val doorFacing = if (isFront) frontDoorFacing else AppConfig.BACK_DOOR_HEADING
        val relAngle = ((bearing - doorFacing + 540.0) % 360.0) - 180.0

        val yardLook = if (isFront) {
            // Standing outside front door
            when {
                relAngle < -50.0 -> "Look far to your LEFT"
                relAngle in -50.0..-15.0 -> "Look to your LEFT across front yard"
                relAngle in -15.0..15.0 -> "Look STRAIGHT ahead across front yard"
                relAngle in 15.0..50.0 -> "Look to your RIGHT across front yard"
                else -> "Look far to your RIGHT"
            }
        } else {
            // Standing outside back door
            when {
                relAngle < -50.0 -> "Look far to your LEFT"
                relAngle in -50.0..-15.0 -> "Look to your LEFT across backyard"
                relAngle in -15.0..15.0 -> "Look STRAIGHT ahead across backyard"
                relAngle in 15.0..50.0 -> "Look to your RIGHT across backyard"
                else -> "Look far to your RIGHT"
            }
        }

        val door = when (stage) {
            SpotterStage.DIRECT_FLYOVER -> "ANY DOOR! (OVERHEAD)"
            SpotterStage.RUN_OUTSIDE_NOW -> "RUN TO: $doorTarget"
            SpotterStage.GET_READY -> "GET READY NEAR $doorTarget"
            SpotterStage.MONITORING_INBOUND -> "INSIDE (WATCH RADAR)"
            SpotterStage.FADING_AWAY -> "PASSED US (FADING)"
        }

        val skyHeightDesc = when {
            elevAngleDeg >= 75 -> "Look STRAIGHT UP overhead"
            elevAngleDeg >= 45 -> "Look HIGH UP in the sky"
            elevAngleDeg >= 22 -> "Look HALFWAY UP in the sky"
            else -> "Look LOW, just above the rooftops & trees"
        }

        val look = when (stage) {
            SpotterStage.DIRECT_FLYOVER -> "LOOK STRAIGHT UP! Right above our house!"
            SpotterStage.RUN_OUTSIDE_NOW -> "$yardLook • $skyHeightDesc"
            SpotterStage.GET_READY -> {
                val s = etaSeconds ?: 60
                "Heading our way! Visible in ~${s}s from $doorTarget"
            }
            SpotterStage.MONITORING_INBOUND -> "Too far to see from yard (${String.format(Locale.US, "%.1f", dist)} mi out). Watch on TV!"
            SpotterStage.FADING_AWAY -> "Flying away into distance ($yardLook)"
        }

        val spotterTip = when (stage) {
            SpotterStage.DIRECT_FLYOVER -> {
                if (alt < 5000) "🔊 ROARING LOW! Listen for the loud engine zoom right now!"
                else "☁️ DIRECT OVERHEAD! Look straight up for the white cloud line!"
            }
            SpotterStage.RUN_OUTSIDE_NOW -> {
                if (alt < 5000) "👀 RUN OUTSIDE NOW! Low enough to hear and see over the roofs!"
                else "👀 RUN OUTSIDE NOW! Look high in the sky — visible right now!"
            }
            SpotterStage.GET_READY -> "⏳ Stay inside for a minute — get your shoes on by the door!"
            SpotterStage.MONITORING_INBOUND -> "📡 Tracked on radar. We will tell you when it gets close enough to see!"
            SpotterStage.FADING_AWAY -> "👋 It flew past our house! Wave goodbye as it disappears."
        }

        val distanceText = when (stage) {
            SpotterStage.DIRECT_FLYOVER -> String.format(Locale.US, "%.1f mi • OVERHEAD", dist)
            SpotterStage.RUN_OUTSIDE_NOW -> String.format(Locale.US, "%.1f mi • VISIBLE NOW", dist)
            SpotterStage.GET_READY -> {
                val s = etaSeconds ?: 60
                String.format(Locale.US, "%.1f mi • IN ~%ds", dist, s)
            }
            SpotterStage.MONITORING_INBOUND -> String.format(Locale.US, "%.1f mi • ON RADAR", dist)
            SpotterStage.FADING_AWAY -> String.format(Locale.US, "%.1f mi • FLYING AWAY", dist)
        }

        val altitudeText = String.format(Locale.US, "%,d FT", alt)
        val speedText = "$speedMph MPH"

        return KidSpotterGuide(
            doorToRunTo = door,
            directionToLook = look,
            cardinalDirection = cardinal,
            spotterTip = spotterTip,
            distanceText = distanceText,
            altitudeText = altitudeText,
            speedMphText = speedText,
            isApproaching = isApproaching,
            stage = stage,
            elevationAngleDeg = elevAngleDeg,
            etaSeconds = etaSeconds
        )
    }

    private val photoCache = ConcurrentHashMap<String, String>()

    private fun fetchPlanespottersPhoto(registration: String, hex: String): String? {
        val key = if (registration.isNotBlank()) registration else hex
        if (key.isBlank()) return null
        photoCache[key]?.let { return it }

        try {
            val query = if (registration.isNotBlank()) "reg/$registration" else "hex/$hex"
            val url = "https://api.planespotters.net/pub/photos/$query"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "AirwallRadar/1.0 (contact@example.com)")
                .build()
            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val root = JsonParser.parseString(body).asJsonObject
                    if (root.has("photos")) {
                        val photos = root.getAsJsonArray("photos")
                        if (photos.size() > 0) {
                            val first = photos[0].asJsonObject
                            val src = if (first.has("thumbnail_large")) {
                                first.getAsJsonObject("thumbnail_large").get("src").asString
                            } else if (first.has("thumbnail")) {
                                first.getAsJsonObject("thumbnail").get("src").asString
                            } else null
                            if (src != null) {
                                photoCache[key] = src
                                return src
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun determinePhaseAndStatus(state: AircraftState): Pair<String, String> {
        return when {
            state.onGround -> Pair("GROUND", "STATUS: ON GROUND")
            state.verticalRateFpm < -600 -> {
                if (state.altitudeFeet < 10000) Pair("DESCENT", "STATUS: APPROACH (DESCENT)")
                else Pair("DESCENT", "STATUS: IN DESCENT")
            }
            state.verticalRateFpm > 600 -> Pair("CLIMB", "STATUS: CLIMBING")
            state.altitudeFeet >= 25000 -> Pair("CRUISE", "STATUS: EN ROUTE (CRUISE)")
            state.altitudeFeet in 10000..24999 -> Pair("TRANSIT", "STATUS: IN TRANSIT")
            else -> Pair("APPROACH", "STATUS: LOW ALTITUDE / APPROACH")
        }
    }

    private fun calculateProgressAndEta(state: AircraftState, route: RouteInfo): Triple<Float, String, Int> {
        val now = System.currentTimeMillis()
        val timeFormat = SimpleDateFormat("h:mm a", Locale.US)

        if (route.destLat != null && route.destLon != null) {
            val distToDest = OpenSkyApi.calculateDistanceMiles(state.latitude, state.longitude, route.destLat, route.destLon)
            val totalDist = if (route.originLat != null && route.originLon != null) {
                OpenSkyApi.calculateDistanceMiles(route.originLat, route.originLon, route.destLat, route.destLon)
            } else {
                distToDest * 2.5
            }

            val speed = max(state.speedKnots, 150)
            val hoursRemaining = distToDest / speed
            val minutesRemaining = (hoursRemaining * 60).roundToInt()
            val etaMillis = now + (minutesRemaining * 60 * 1000L)
            val etaStr = "${timeFormat.format(Date(etaMillis))} (IN ${String.format("%02d:%02d", minutesRemaining / 60, minutesRemaining % 60)} MIN)"

            val progress = ((totalDist - distToDest) / max(totalDist, 1.0)).toFloat().coerceIn(0.05f, 0.95f)
            return Triple(progress, etaStr, minutesRemaining)
        }

        // Fallback default calculation based on heading and location
        val defaultMinutes = 35
        val etaMillis = now + (defaultMinutes * 60 * 1000L)
        val etaStr = "${timeFormat.format(Date(etaMillis))} (IN 00:35 MIN)"
        return Triple(0.72f, etaStr, defaultMinutes)
    }

    private fun resolveAssetImage(icaoType: String, airlineCode: String, callsign: String, speedKnots: Int): String {
        val type = icaoType.uppercase().trim()
        val airline = airlineCode.uppercase().trim()
        val cs = callsign.uppercase().trim()

        // 1. General Aviation propeller planes (e.g. N-number with speed < 200 kts)
        if (cs.startsWith("N") && speedKnots in 50..210) {
            return "aircraft/general_aviation.jpg"
        }
        if (type.contains("PA28") || type.contains("C172") || type.contains("C182") || type.contains("SR22") || type.contains("BE36")) {
            return "aircraft/general_aviation.jpg"
        }

        // 2. Direct airline + type match
        if (airline == "UAL" && (type.contains("738") || type.contains("737") || type.contains("B738") || type.contains("B38M"))) {
            return "aircraft/B738_UAL.jpg"
        }
        if (airline == "DAL" && (type.contains("738") || type.contains("737") || type.contains("B738") || type.contains("A320") || type.contains("A321"))) {
            return "aircraft/B738_DAL.jpg"
        }
        if (airline == "AAL" && (type.contains("772") || type.contains("777") || type.contains("788") || type.contains("B772") || type.contains("B77W"))) {
            return "aircraft/B772_AAL.jpg"
        }

        // 3. Type matches
        return when {
            type.contains("738") || type.contains("739") || type.contains("737") || type.contains("B73") -> "aircraft/B738.jpg"
            type.contains("320") || type.contains("A320") -> "aircraft/A320.jpg"
            type.contains("321") || type.contains("A321") || type.contains("A21N") -> "aircraft/A321.jpg"
            type.contains("772") || type.contains("777") || type.contains("B77") -> "aircraft/B772.jpg"
            type.contains("E75") || type.contains("E19") || type.contains("E17") -> "aircraft/E75L.jpg"
            type.contains("CRJ") -> "aircraft/CRJ9.jpg"
            type.contains("GLF") || type.contains("C56") || type.contains("CIT") || type.contains("LJ") -> "aircraft/GLF4.jpg"
            airline == "UAL" -> "aircraft/B738_UAL.jpg"
            airline == "DAL" -> "aircraft/B738_DAL.jpg"
            airline == "AAL" -> "aircraft/B772_AAL.jpg"
            cs.startsWith("N") && speedKnots < 230 -> "aircraft/general_aviation.jpg"
            else -> "aircraft/default_jet.jpg"
        }
    }

    private fun getOrFetchRoute(callsign: String): RouteInfo {
        val cs = callsign.trim().uppercase()
        if (cs.isBlank()) return defaultRouteInfo(cs)

        routeCache[cs]?.let { return it }

        try {
            val url = "https://api.adsbdb.com/v0/callsign/$cs"
            val req = Request.Builder().url(url).header("User-Agent", "Airwall/1.0").build()
            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = JsonParser.parseString(body).asJsonObject
                    if (root.has("response")) {
                        val respObj = root.getAsJsonObject("response")
                        if (respObj.has("flightroute") && !respObj.get("flightroute").isJsonNull) {
                            val fr = respObj.getAsJsonObject("flightroute")
                            val callsignIata = if (fr.has("callsign_iata") && !fr.get("callsign_iata").isJsonNull) fr.get("callsign_iata").asString else cs
                            var airlineName = ""
                            var airlineCode = ""
                            if (fr.has("airline") && !fr.get("airline").isJsonNull) {
                                val al = fr.getAsJsonObject("airline")
                                airlineName = if (al.has("name") && !al.get("name").isJsonNull) al.get("name").asString else ""
                                airlineCode = if (al.has("icao") && !al.get("icao").isJsonNull) al.get("icao").asString else ""
                            }
                            var origCode = ""
                            var origName = ""
                            var origCity = ""
                            var origLat: Double? = null
                            var origLon: Double? = null
                            if (fr.has("origin") && !fr.get("origin").isJsonNull) {
                                val o = fr.getAsJsonObject("origin")
                                origCode = if (o.has("iata_code") && !o.get("iata_code").isJsonNull) o.get("iata_code").asString else ""
                                origName = if (o.has("name") && !o.get("name").isJsonNull) o.get("name").asString else ""
                                origCity = if (o.has("municipality") && !o.get("municipality").isJsonNull) o.get("municipality").asString else ""
                                origLat = if (o.has("latitude") && !o.get("latitude").isJsonNull) o.get("latitude").asDouble else null
                                origLon = if (o.has("longitude") && !o.get("longitude").isJsonNull) o.get("longitude").asDouble else null
                            }
                            var destCode = ""
                            var destName = ""
                            var destCity = ""
                            var destLat: Double? = null
                            var destLon: Double? = null
                            if (fr.has("destination") && !fr.get("destination").isJsonNull) {
                                val d = fr.getAsJsonObject("destination")
                                destCode = if (d.has("iata_code") && !d.get("iata_code").isJsonNull) d.get("iata_code").asString else ""
                                destName = if (d.has("name") && !d.get("name").isJsonNull) d.get("name").asString else ""
                                destCity = if (d.has("municipality") && !d.get("municipality").isJsonNull) d.get("municipality").asString else ""
                                destLat = if (d.has("latitude") && !d.get("latitude").isJsonNull) d.get("latitude").asDouble else null
                                destLon = if (d.has("longitude") && !d.get("longitude").isJsonNull) d.get("longitude").asDouble else null
                            }

                            val info = RouteInfo(
                                callsignIata = callsignIata,
                                airlineName = airlineName,
                                airlineCode = airlineCode,
                                originCode = origCode,
                                originName = origName,
                                originCity = origCity,
                                originLat = origLat,
                                originLon = origLon,
                                destCode = destCode,
                                destName = destName,
                                destCity = destCity,
                                destLat = destLat,
                                destLon = destLon
                            )
                            routeCache[cs] = info
                            return info
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val fallback = defaultRouteInfo(cs)
        routeCache[cs] = fallback
        return fallback
    }

    private fun getOrFetchAircraft(icao24: String): AircraftInfo {
        val hex = icao24.trim().lowercase()
        if (hex.isBlank()) return AircraftInfo("Commercial Jet", "B738", "")

        aircraftCache[hex]?.let { return it }

        try {
            val url = "https://hexdb.io/api/v1/aircraft/$hex"
            val req = Request.Builder().url(url).header("User-Agent", "Airwall/1.0").build()
            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val obj = JsonParser.parseString(body).asJsonObject
                    val manufacturer = if (obj.has("Manufacturer") && !obj.get("Manufacturer").isJsonNull) obj.get("Manufacturer").asString else ""
                    val type = if (obj.has("Type") && !obj.get("Type").isJsonNull) obj.get("Type").asString else ""
                    val icaoCode = if (obj.has("ICAOTypeCode") && !obj.get("ICAOTypeCode").isJsonNull) obj.get("ICAOTypeCode").asString else "B738"
                    val reg = if (obj.has("Registration") && !obj.get("Registration").isJsonNull) obj.get("Registration").asString else ""

                    val modelName = if (manufacturer.isNotBlank() && type.isNotBlank()) "$manufacturer $type" else if (type.isNotBlank()) type else "Boeing 737-800"
                    val info = AircraftInfo(modelName = modelName, icaoTypeCode = icaoCode, registration = reg)
                    aircraftCache[hex] = info
                    return info
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val fallback = AircraftInfo("Light Aircraft / Jet", "GA", "")
        aircraftCache[hex] = fallback
        return fallback
    }

    private fun defaultRouteInfo(callsign: String): RouteInfo {
        val code = when {
            callsign.startsWith("UAL") -> "UA"
            callsign.startsWith("DAL") -> "DL"
            callsign.startsWith("AAL") -> "AA"
            callsign.startsWith("SWA") -> "WN"
            callsign.startsWith("ACA") -> "AC"
            callsign.startsWith("ROU") -> "RV"
            else -> ""
        }
        val number = callsign.filter { it.isDigit() }
        val iata = if (code.isNotBlank() && number.isNotBlank()) "$code$number" else callsign

        // For General Aviation (N-numbers or no airline code)
        if (code.isBlank()) {
            val localApt = AppConfig.LOCAL_AIRPORT_CODE.ifBlank { "AIR" }
            return RouteInfo(
                callsignIata = callsign,
                airlineName = "General Aviation",
                airlineCode = "",
                originCode = localApt,
                originName = if (AppConfig.LOCAL_AIRPORT_CODE.isNotBlank()) "${AppConfig.LOCAL_AIRPORT_CODE} Regional" else "Regional Airport",
                originCity = "Local Flight",
                originLat = if (AppConfig.LOCAL_AIRPORT_LAT != 0.0) AppConfig.LOCAL_AIRPORT_LAT else null,
                originLon = if (AppConfig.LOCAL_AIRPORT_LON != 0.0) AppConfig.LOCAL_AIRPORT_LON else null,
                destCode = "",
                destName = AppConfig.LOCATION_LABEL,
                destCity = "Local Flight",
                destLat = null,
                destLon = null
            )
        }

        return RouteInfo(
            callsignIata = iata,
            airlineName = "",
            airlineCode = code,
            originCode = "ORD",
            originName = "Chicago O'Hare International",
            originCity = "Chicago",
            originLat = 41.9742,
            originLon = -87.9073,
            destCode = "DEN",
            destName = "Denver International",
            destCity = "Denver",
            destLat = 39.8561,
            destLon = -104.6737
        )
    }

    fun createDemoFlight(): EnrichedFlight {
        // Exactly matches the user's mockup image!
        val state = AircraftState(
            icao24 = "a38c21",
            callsign = "UA345",
            originCountry = "United States",
            longitude = AppConfig.HOME_LON - 0.045,
            latitude = AppConfig.HOME_LAT + 0.025,
            altitudeFeet = 35000,
            speedKnots = 480,
            heading = 305f,
            verticalRateFpm = 0,
            squawk = "4430",
            distanceMiles = 4.3,
            bearingDeg = 310.0,
            onGround = false,
            lastContactEpoch = System.currentTimeMillis() / 1000
        )

        val metadata = FlightMetadata(
            callsign = "UA345",
            flightNumber = "UA345",
            airlineName = "United Airlines",
            airlineCode = "UAL",
            originAirportCode = "ORD",
            originAirportName = "O'Hare International",
            originCity = "Chicago",
            destinationAirportCode = "DEN",
            destinationAirportName = "Denver International",
            destinationCity = "Denver",
            aircraftType = "B738",
            aircraftModelName = "Boeing 737-800",
            registration = "N37456",
            etaTime = "11:25 AM MST (IN 00:35 MIN)",
            etaMinutesRemaining = 35,
            assetImagePath = "aircraft/B738_UAL.jpg",
            kidGuide = calculateKidSpotterGuide(state)
        )

        return EnrichedFlight(state, metadata)
    }
}
