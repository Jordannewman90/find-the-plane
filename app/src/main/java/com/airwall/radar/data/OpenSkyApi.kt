package com.airwall.radar.data

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.*

object OpenSkyApi {
    val HOME_LAT get() = AppConfig.HOME_LAT
    val HOME_LON get() = AppConfig.HOME_LON
    val DEFAULT_RADIUS_MILES get() = AppConfig.DEFAULT_RADIUS_MILES

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun calculateBoundingBox(lat: Double, lon: Double, radiusMiles: Double): Array<Double> {
        val latDegPerMile = 1.0 / 69.0
        val lonDegPerMile = 1.0 / (69.0 * cos(Math.toRadians(lat)))
        val deltaLat = radiusMiles * latDegPerMile
        val deltaLon = radiusMiles * lonDegPerMile

        val lamin = lat - deltaLat
        val lamax = lat + deltaLat
        val lomin = lon - deltaLon
        val lomax = lon + deltaLon
        return arrayOf(lamin, lomin, lamax, lomax)
    }

    fun calculateDistanceMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMiles = 3958.8
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMiles * c
    }

    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLonRad = Math.toRadians(lon2 - lon1)

        val y = sin(dLonRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLonRad)
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }

    fun willCrossWithin2Miles(state: AircraftState): Boolean {
        val d = state.distanceMiles
        if (d <= 2.0) return true

        // Bearing from aircraft to house
        val bearingToHouse = (state.bearingDeg + 180.0) % 360.0
        val angleDiffDeg = abs((state.heading - bearingToHouse + 180.0) % 360.0 - 180.0)

        // Heading is pointed towards house vicinity (< 90 degrees)
        if (angleDiffDeg < 90.0) {
            val minDistanceMiles = d * sin(Math.toRadians(angleDiffDeg))
            return minDistanceMiles <= 2.0
        }
        return false
    }

    fun fetchAircraftInRadius(
        radiusMiles: Double = DEFAULT_RADIUS_MILES,
        centerLat: Double = HOME_LAT,
        centerLon: Double = HOME_LON
    ): List<AircraftState> {
        // 1. Try adsb.fi (ultra-fast, unthrottled, rich aircraft specs)
        val fiList = fetchFromAdsbFi(radiusMiles, centerLat, centerLon)
        if (fiList.isNotEmpty()) {
            return fiList
        }

        // 2. Try adsb.lol (community high-frequency ADS-B backup)
        val lolList = fetchFromAdsbLol(radiusMiles, centerLat, centerLon)
        if (lolList.isNotEmpty()) {
            return lolList
        }

        // 3. Fallback to OpenSky Network
        val box = calculateBoundingBox(centerLat, centerLon, radiusMiles)
        val url = "https://opensky-network.org/api/states/all?lamin=${box[0]}&lomin=${box[1]}&lamax=${box[2]}&lomax=${box[3]}"

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 AirwallRadar/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                parseStatesJson(body, centerLat, centerLon, radiusMiles)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun fetchFromAdsbFi(
        radiusMiles: Double,
        centerLat: Double,
        centerLon: Double
    ): List<AircraftState> {
        val radiusNm = (radiusMiles * 0.868976).roundToInt().coerceAtLeast(5)
        val url = "https://opendata.adsb.fi/api/v2/lat/$centerLat/lon/$centerLon/dist/$radiusNm"

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AirwallRadar/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                parseAdsbFiJson(body, centerLat, centerLon, radiusMiles)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseAdsbFiJson(
        jsonString: String,
        centerLat: Double,
        centerLon: Double,
        maxRadiusMiles: Double
    ): List<AircraftState> {
        val result = mutableListOf<AircraftState>()
        try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            val acList = root.getAsJsonArray("aircraft") ?: return emptyList()

            for (element in acList) {
                val obj = element.asJsonObject
                val hex = if (obj.has("hex") && !obj.get("hex").isJsonNull) obj.get("hex").asString else ""
                val flight = if (obj.has("flight") && !obj.get("flight").isJsonNull) obj.get("flight").asString.trim() else ""
                val reg = if (obj.has("r") && !obj.get("r").isJsonNull) obj.get("r").asString.trim() else ""
                val typeCode = if (obj.has("t") && !obj.get("t").isJsonNull) obj.get("t").asString.trim() else ""
                val desc = if (obj.has("desc") && !obj.get("desc").isJsonNull) obj.get("desc").asString.trim() else ""
                val lat = if (obj.has("lat") && !obj.get("lat").isJsonNull) obj.get("lat").asDouble else null
                val lon = if (obj.has("lon") && !obj.get("lon").isJsonNull) obj.get("lon").asDouble else null

                if (lat != null && lon != null) {
                    val dist = calculateDistanceMiles(centerLat, centerLon, lat, lon)
                    if (dist <= maxRadiusMiles) {
                        val altFeet = if (obj.has("alt_baro") && !obj.get("alt_baro").isJsonNull) {
                            try {
                                obj.get("alt_baro").asInt
                            } catch (_: Exception) {
                                0
                            }
                        } else 0
                        val onGround = if (obj.has("alt_baro") && !obj.get("alt_baro").isJsonNull) {
                            obj.get("alt_baro").asString == "ground"
                        } else false

                        val speedKts = if (obj.has("gs") && !obj.get("gs").isJsonNull) {
                            obj.get("gs").asDouble.roundToInt()
                        } else 0

                        val track = if (obj.has("track") && !obj.get("track").isJsonNull) {
                            obj.get("track").asFloat
                        } else 0f

                        val vertRateFpm = if (obj.has("baro_rate") && !obj.get("baro_rate").isJsonNull) {
                            obj.get("baro_rate").asInt
                        } else 0

                        val squawk = if (obj.has("squawk") && !obj.get("squawk").isJsonNull) {
                            obj.get("squawk").asString
                        } else null

                        val bearing = calculateBearing(centerLat, centerLon, lat, lon)

                        result.add(
                            AircraftState(
                                icao24 = hex,
                                callsign = if (flight.isNotBlank()) flight else if (reg.isNotBlank()) reg else hex.uppercase(),
                                originCountry = "USA",
                                longitude = lon,
                                latitude = lat,
                                altitudeFeet = altFeet,
                                speedKnots = speedKts,
                                heading = track,
                                verticalRateFpm = vertRateFpm,
                                squawk = squawk,
                                distanceMiles = dist,
                                bearingDeg = bearing,
                                onGround = onGround,
                                lastContactEpoch = System.currentTimeMillis() / 1000,
                                typeCode = typeCode,
                                registration = reg,
                                description = desc
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result.sortedBy { it.distanceMiles }
    }

    private fun fetchFromAdsbLol(
        radiusMiles: Double,
        centerLat: Double,
        centerLon: Double
    ): List<AircraftState> {
        val radiusNm = (radiusMiles * 0.868976).roundToInt().coerceAtLeast(5)
        val url = "https://api.adsb.lol/v2/point/$centerLat/$centerLon/$radiusNm"

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AirwallRadar/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                parseAdsbLolJson(body, centerLat, centerLon, radiusMiles)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseAdsbLolJson(
        jsonString: String,
        centerLat: Double,
        centerLon: Double,
        maxRadiusMiles: Double
    ): List<AircraftState> {
        val result = mutableListOf<AircraftState>()
        try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            val acList = root.getAsJsonArray("ac") ?: return emptyList()

            for (element in acList) {
                val obj = element.asJsonObject
                val hex = if (obj.has("hex") && !obj.get("hex").isJsonNull) obj.get("hex").asString else ""
                val flight = if (obj.has("flight") && !obj.get("flight").isJsonNull) obj.get("flight").asString.trim() else ""
                val reg = if (obj.has("r") && !obj.get("r").isJsonNull) obj.get("r").asString.trim() else ""
                val typeCode = if (obj.has("t") && !obj.get("t").isJsonNull) obj.get("t").asString.trim() else ""
                val lat = if (obj.has("lat") && !obj.get("lat").isJsonNull) obj.get("lat").asDouble else null
                val lon = if (obj.has("lon") && !obj.get("lon").isJsonNull) obj.get("lon").asDouble else null

                if (lat != null && lon != null) {
                    val dist = calculateDistanceMiles(centerLat, centerLon, lat, lon)
                    if (dist <= maxRadiusMiles) {
                        val altFeet = if (obj.has("alt_baro") && !obj.get("alt_baro").isJsonNull) {
                            try {
                                obj.get("alt_baro").asInt
                            } catch (_: Exception) {
                                0
                            }
                        } else 0
                        val onGround = if (obj.has("alt_baro") && !obj.get("alt_baro").isJsonNull) {
                            obj.get("alt_baro").asString == "ground"
                        } else false

                        val speedKts = if (obj.has("gs") && !obj.get("gs").isJsonNull) {
                            obj.get("gs").asDouble.roundToInt()
                        } else 0

                        val track = if (obj.has("track") && !obj.get("track").isJsonNull) {
                            obj.get("track").asFloat
                        } else 0f

                        val vertRateFpm = if (obj.has("baro_rate") && !obj.get("baro_rate").isJsonNull) {
                            obj.get("baro_rate").asInt
                        } else 0

                        val squawk = if (obj.has("squawk") && !obj.get("squawk").isJsonNull) {
                            obj.get("squawk").asString
                        } else null

                        val bearing = calculateBearing(centerLat, centerLon, lat, lon)

                        result.add(
                            AircraftState(
                                icao24 = hex,
                                callsign = if (flight.isNotBlank()) flight else if (reg.isNotBlank()) reg else hex.uppercase(),
                                originCountry = "USA",
                                longitude = lon,
                                latitude = lat,
                                altitudeFeet = altFeet,
                                speedKnots = speedKts,
                                heading = track,
                                verticalRateFpm = vertRateFpm,
                                squawk = squawk,
                                distanceMiles = dist,
                                bearingDeg = bearing,
                                onGround = onGround,
                                lastContactEpoch = System.currentTimeMillis() / 1000,
                                typeCode = typeCode,
                                registration = reg
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result.sortedBy { it.distanceMiles }
    }

    private fun parseStatesJson(
        jsonString: String,
        centerLat: Double,
        centerLon: Double,
        maxRadiusMiles: Double
    ): List<AircraftState> {
        val result = mutableListOf<AircraftState>()
        try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            val states = root.getAsJsonArray("states") ?: return emptyList()

            for (element in states) {
                val arr = element.asJsonArray
                val icao24 = if (arr.size() > 0 && !arr[0].isJsonNull) arr[0].asString else ""
                val callsign = if (arr.size() > 1 && !arr[1].isJsonNull) arr[1].asString.trim() else ""
                val originCountry = if (arr.size() > 2 && !arr[2].isJsonNull) arr[2].asString else "USA"
                val lastContact = if (arr.size() > 4 && !arr[4].isJsonNull) arr[4].asLong else 0L
                val lon = if (arr.size() > 5 && !arr[5].isJsonNull) arr[5].asDouble else null
                val lat = if (arr.size() > 6 && !arr[6].isJsonNull) arr[6].asDouble else null
                val baroAltMeters = if (arr.size() > 7 && !arr[7].isJsonNull) arr[7].asDouble else null
                val onGround = if (arr.size() > 8 && !arr[8].isJsonNull) arr[8].asBoolean else false
                val velocityMps = if (arr.size() > 9 && !arr[9].isJsonNull) arr[9].asDouble else null
                val trueTrack = if (arr.size() > 10 && !arr[10].isJsonNull) arr[10].asFloat else 0f
                val verticalRateMps = if (arr.size() > 11 && !arr[11].isJsonNull) arr[11].asDouble else 0.0
                val squawk = if (arr.size() > 14 && !arr[14].isJsonNull) arr[14].asString else null

                if (lat != null && lon != null) {
                    val dist = calculateDistanceMiles(centerLat, centerLon, lat, lon)
                    if (dist <= maxRadiusMiles) {
                        val altFeet = ((baroAltMeters ?: 0.0) * 3.28084).roundToInt()
                        val speedKts = ((velocityMps ?: 0.0) * 1.94384).roundToInt()
                        val vertRateFpm = (verticalRateMps * 196.85).roundToInt()
                        val bearing = calculateBearing(centerLat, centerLon, lat, lon)

                        result.add(
                            AircraftState(
                                icao24 = icao24,
                                callsign = if (callsign.isNotBlank()) callsign else icao24.uppercase(),
                                originCountry = originCountry,
                                longitude = lon,
                                latitude = lat,
                                altitudeFeet = altFeet,
                                speedKnots = speedKts,
                                heading = trueTrack,
                                verticalRateFpm = vertRateFpm,
                                squawk = squawk,
                                distanceMiles = dist,
                                bearingDeg = bearing,
                                onGround = onGround,
                                lastContactEpoch = lastContact
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Sort closest to home first
        return result.sortedBy { it.distanceMiles }
    }
}
