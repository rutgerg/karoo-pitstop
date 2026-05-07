package dev.karoorestaurant

import dev.karoorestaurant.data.route.LatLng

/**
 * A single Karoo location reading bundled with the orientation captured at the same instant.
 * Orientation is in degrees (0 = north, 90 = east); null when the device cannot provide it
 * (e.g. stationary GPS, magnetometer disabled).
 */
data class RiderLocation(val point: LatLng, val orientationDegrees: Double?)
