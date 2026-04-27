package dev.karoorestaurant.data.route

data class Route(
    val id: String,
    val name: String,
    val polyline: List<LatLng>,
    val distanceMeters: Double,
)
