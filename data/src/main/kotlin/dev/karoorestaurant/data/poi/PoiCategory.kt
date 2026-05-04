package dev.karoorestaurant.data.poi

enum class PoiCategory(val label: String) {
    RESTAURANT("Restaurant"),
    SUPERMARKET("Supermarket"),
    FUEL("Fuel"),
    CAFE("Cafe"),
    HOTEL("Hotel"),
    DOCTOR("Doctor"),
    PHARMACY("Pharmacy"),
    BIKE_SHOP("Bike Shop"),
    ATM("ATM");

    companion object {
        fun fromTags(tags: Map<String, String>): PoiCategory? {
            val amenity = tags["amenity"]
            val shop = tags["shop"]
            val tourism = tags["tourism"]
            return when {
                amenity == "restaurant" -> RESTAURANT
                amenity == "fuel" -> FUEL
                shop in SUPERMARKET_SHOPS -> SUPERMARKET
                amenity in CAFE_AMENITIES -> CAFE
                tourism in HOTEL_TOURISM -> HOTEL
                amenity in DOCTOR_AMENITIES -> DOCTOR
                amenity == "pharmacy" -> PHARMACY
                shop == "bicycle" -> BIKE_SHOP
                amenity == "atm" -> ATM
                else -> null
            }
        }

        private val SUPERMARKET_SHOPS = setOf("supermarket", "convenience")
        private val CAFE_AMENITIES = setOf("bar", "cafe")
        private val HOTEL_TOURISM = setOf("hotel", "guest_house", "hostel", "motel")
        private val DOCTOR_AMENITIES = setOf("doctors", "clinic")
    }
}
