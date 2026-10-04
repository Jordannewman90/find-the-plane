package com.airwall.radar.data

/**
 * =======================================================================
 * AIRWALL / FLIGHTWALL CONFIGURATION
 * =======================================================================
 *
 * Customize this single file to point the radar to your home, set up your
 * yard/door perspectives, and personalize your family app branding!
 *
 * TIP: You can use ChatGPT / Claude / Gemini to fill this out for you:
 * "Configure this AppConfig.kt for my address: [Your Address],
 *  front door facing [Street/Compass Direction],
 *  back door facing [Yard/Compass Direction]."
 * =======================================================================
 */
object AppConfig {
    // -------------------------------------------------------------------
    // 1. Home Location (GPS Coordinates)
    // -------------------------------------------------------------------
    // Replace with your home's exact Latitude and Longitude.
    // (You can right-click your house on Google Maps to copy lat/lon)
    // Default example: Geographic center of the Continental US
    const val HOME_LAT: Double = 39.8283
    const val HOME_LON: Double = -98.5795

    // Display label shown on the center radar icon
    const val HOME_LABEL: String = "OUR HOUSE"

    // Display label shown on the idle scanning screen
    const val LOCATION_LABEL: String = "LOCAL AIRSPACE"

    // -------------------------------------------------------------------
    // 2. Door & Yard Perspectives (Observer Eyeline Angles)
    // -------------------------------------------------------------------
    // Compass bearing (0° - 360°) facing directly out from your doors:
    //   North = 0°, East = 90°, South = 180°, West = 270°
    // The app calculates whether to tell kids to run to the front door
    // or back door, and gives relative directions ("Look to your LEFT",
    // "Look STRAIGHT UP", "Look to your RIGHT") based on these angles!
    const val FRONT_DOOR_HEADING: Double = 180.0 // e.g. Facing South
    const val FRONT_DOOR_LABEL: String = "FRONT DOOR"
    const val FRONT_YARD_LABEL: String = "FRONT YARD"

    const val BACK_DOOR_HEADING: Double = 0.0   // e.g. Facing North
    const val BACK_DOOR_LABEL: String = "BACK DOOR (Backyard)"
    const val BACKYARD_LABEL: String = "BACKYARD"

    // -------------------------------------------------------------------
    // 3. App Title & Branding
    // -------------------------------------------------------------------
    // Displayed on the top header and TV launcher banner
    const val APP_TITLE: String = "FIND THE PLANE"
    const val APP_SUBTITLE: String = "FAMILY FLIGHTWALL"

    // -------------------------------------------------------------------
    // 4. Default Radar Radius & Thresholds
    // -------------------------------------------------------------------
    const val DEFAULT_RADIUS_MILES: Double = 10.0
    const val CLOSE_FLYOVER_MILES: Double = 2.0

    // -------------------------------------------------------------------
    // 5. Optional Local Airport Landmark (Runway & Code on Radar)
    // -------------------------------------------------------------------
    // Leave code blank ("") if you don't want an airport drawn on your radar.
    // Set to your local airport IATA/ICAO code to draw runway orientation:
    const val LOCAL_AIRPORT_CODE: String = "" // e.g., "ORD", "DTW", "LAX"
    const val LOCAL_AIRPORT_LAT: Double = 0.0
    const val LOCAL_AIRPORT_LON: Double = 0.0
    const val LOCAL_AIRPORT_RUNWAY_ANGLE_DEG: Double = 90.0
}
