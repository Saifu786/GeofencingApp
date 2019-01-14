package com.example.wozart.geofencingapp

object Constant {


        private val PACKAGE_NAME = "com.example.wozart.geofencingapp"

        val GEOFENCES_ADDED_KEY = "$PACKAGE_NAME.GEOFENCES_ADDED_KEY"
        private val GEOFENCE_EXPIRATION_IN_HOURS: Long = 12
        val GEOFENCE_EXPIRATION_IN_MILLISECONDS = GEOFENCE_EXPIRATION_IN_HOURS * 60 * 60 * 1000
        val GEOFENCE_RADIUS_IN_METERS = "CIRCLE RADIUS"

        var LongLat  =  HashMap<String,LatLng>()
        init {
            LongLat.put("Wozart",LatLng(17.425794,78.444258))
        }
}
    data class LatLng(
        val latitude : Double,
        val longitude : Double

    )