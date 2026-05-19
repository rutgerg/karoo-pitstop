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
    ATM("ATM"),
    TRAIN_STATION("Train Station"),
    DRINKING_WATER("Drinking Water"),
    TOILETS("Toilets"),
    CEMETERY("Cemetery");

    companion object {
        fun fromFlag(value: String): PoiCategory? = when (value.trim().lowercase()) {
            "restaurant", "fast_food", "snack", "snack_bar" -> RESTAURANT
            "supermarket" -> SUPERMARKET
            "fuel" -> FUEL
            "cafe", "bar" -> CAFE
            "hotel" -> HOTEL
            "doctor", "doctors", "clinic" -> DOCTOR
            "pharmacy" -> PHARMACY
            "bike", "bike_shop", "bicycle" -> BIKE_SHOP
            "atm" -> ATM
            "train", "station", "train_station", "halt" -> TRAIN_STATION
            "water", "drinking_water", "tap", "water_tap", "well", "water_well" -> DRINKING_WATER
            "toilet", "toilets", "wc" -> TOILETS
            "cemetery", "graveyard", "grave_yard" -> CEMETERY
            else -> null
        }

        fun fromTags(tags: Map<String, String>): PoiCategory? {
            val amenity = tags["amenity"]
            val shop = tags["shop"]
            val tourism = tags["tourism"]
            val railway = tags["railway"]
            val manMade = tags["man_made"]
            val landuse = tags["landuse"]
            val drinkingWater = tags["drinking_water"]
            return when {
                amenity in RESTAURANT_AMENITIES -> RESTAURANT
                amenity == "fuel" -> FUEL
                shop in SUPERMARKET_SHOPS -> SUPERMARKET
                amenity in CAFE_AMENITIES -> CAFE
                tourism in HOTEL_TOURISM -> HOTEL
                amenity in DOCTOR_AMENITIES -> DOCTOR
                amenity == "pharmacy" -> PHARMACY
                shop == "bicycle" -> BIKE_SHOP
                amenity == "atm" -> ATM
                railway in TRAIN_STATION_RAILWAY -> TRAIN_STATION
                amenity == "drinking_water" && drinkingWater != "no" -> DRINKING_WATER
                manMade in DRINKING_WATER_MAN_MADE && drinkingWater != "no" -> DRINKING_WATER
                amenity == "toilets" -> TOILETS
                landuse == "cemetery" || amenity == "grave_yard" -> CEMETERY
                else -> null
            }
        }

        private val RESTAURANT_AMENITIES = setOf("restaurant", "fast_food")
        private val SUPERMARKET_SHOPS = setOf("supermarket", "convenience")
        private val CAFE_AMENITIES = setOf("bar", "cafe")
        private val HOTEL_TOURISM = setOf("hotel", "guest_house", "hostel", "motel")
        private val DOCTOR_AMENITIES = setOf("doctors", "clinic")
        private val TRAIN_STATION_RAILWAY = setOf("station", "halt")
        private val DRINKING_WATER_MAN_MADE = setOf("water_tap", "water_well")
    }
}
