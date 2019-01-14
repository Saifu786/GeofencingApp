package com.example.wozart.geofencingapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.res.ResourcesCompat
import android.support.v7.content.res.AppCompatResources
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.ResultCallback
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialOverlayLayout
import com.leinardi.android.speeddial.SpeedDialView
import java.io.IOException
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity(),OnCompleteListener<Void>,LocationListener, OnMapReadyCallback,GoogleMap.OnMarkerClickListener,
    ResultCallback<Status> {

    private val REQUEST_PERMISSIONS_REQUEST_CODE = 34
    var TAG:String  = "MainActivity"
    private  var mGeofencePendingIntent: PendingIntent ?= null
    private var mgoogleapiClient : GoogleApiClient  ?= null
    private var mLocationRequest: LocationRequest ?= null
    private var fusedLocation : FusedLocationProviderApi ?= null
    private var locationMarker: Marker? = null
    private var customMarker: Marker ?= null
    private var geoFenceLimits: Circle? = null
    private var customGeoFence: Circle ?= null
    private var latitude:Double ? = null
    private var longitude : Double ?= null
    var map = MapFragment()
    var googleMap: GoogleMap ?= null
    var geofencingClient : GeofencingClient ?= null
    var geofenceTa : Geofence ?= null
    var geofenceCustonm : Geofence ?= null
    private var data: Location? = null
    private var latLong : LatLng ?= null
    var geoModel = 50.0f
    var updateRadius : Float = 0.0f
    var Updated = 0f
    private val KEY_GEOFENCE_RADIUS = "GEOFENCE RADIUS"
    private var KEY_GEOFENCE_UPDATE_RADIUS = "GEOFENCE UPDATE rADIUS"
    var addressList : List<Address> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val speedDialView = findViewById<SpeedDialView>(R.id.speedDial)
        val overlay = findViewById<SpeedDialOverlayLayout>(R.id.overlay)
        speedDialView.overlayLayout = overlay
            speedDialView.setOnActionSelectedListener {
               condition(it)
        }
        var drawable = AppCompatResources.getDrawable(this@MainActivity, R.drawable.ic_add_location_black_24dp)
        val fabWithLabelView = speedDialView.addActionItem(
            SpeedDialActionItem.Builder(R.id.fab_add_action, drawable)
                .setFabImageTintColor(ResourcesCompat.getColor(resources, R.color.inbox_primary, theme))
                .setLabel("Add Geo-Fence")
                .setLabelColor(Color.WHITE)
                .setLabelBackgroundColor(ResourcesCompat.getColor(resources, R.color.circle_red, theme))
                .create()
        )


        speedDialView.setOnChangeListener(object : SpeedDialView.OnChangeListener {
            override fun onMainActionSelected(): Boolean {
                Toast.makeText(applicationContext,"Main action clicked!",Toast.LENGTH_SHORT).show()
                return false // True to keep the Speed Dial open
            }

            override fun onToggleChanged(isOpen: Boolean) {
                Log.d(TAG, "Speed dial toggle state changed. Open = $isOpen")
            }
        })

        map = fragmentManager.findFragmentById(R.id.map_id) as MapFragment
        map.getMapAsync(this)

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this).edit()
            sharedPref.putFloat(KEY_GEOFENCE_RADIUS,geoModel)
            sharedPref.apply()

        val pref = PreferenceManager.getDefaultSharedPreferences(this)
         Updated =  pref.getFloat(KEY_GEOFENCE_UPDATE_RADIUS,0f)

        geofencingClient = LocationServices.getGeofencingClient(this)!!
       if(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS){
            initGoogleApiClient()
           checkPermissions()

        }else{
            Log.i(TAG,"GOOGLE PLAY SERVICE IS NOT APPLICABLE")
        }
    }


    fun condition(item: SpeedDialActionItem): Boolean {

        if(item.label == "Add Geo-Fence"){
            addCustomGeofence(latLong!!)
            drawCustomGeofence(geoModel)
            Toast.makeText(this,"Geofence Added",Toast.LENGTH_SHORT).show()
            }
        this.finish()
        return  true
    }



    private fun initGoogleApiClient(){
        mgoogleapiClient = GoogleApiClient.Builder(this)
            .addApi(LocationServices.API)
            .addConnectionCallbacks(ConnectionAddListner())
            .addOnConnectionFailedListener(ConnectionFailedListner())
            .build()
        mgoogleapiClient?.connect()
    }


    @SuppressLint("MissingPermission")
    override fun onMapReady(p0: GoogleMap?) {
        googleMap = p0
        googleMap!!.uiSettings.isZoomControlsEnabled = true
        googleMap!!.setMinZoomPreference(15f)
        showCurrentLocationOnMap()
        googleMap!!.setOnMarkerClickListener(this)
        googleMap!!.setOnCameraIdleListener {
            var mCameraLocation = googleMap!!.cameraPosition.target
            getAddress(mCameraLocation.latitude,mCameraLocation.longitude)
        }

    }

    override fun onMarkerClick(p0: Marker?): Boolean {
        return false
    }

  @SuppressLint("MissingPermission")
    fun markerForGeofence(latLong: LatLng) {
        Log.i(TAG,"GeofenceMarker for $latLong")
        val title = "Current Location :"+latLong.latitude + " ," + latLong.longitude
        val markerOptions = MarkerOptions()
            .position(latLong)
            .title(title)

       if (googleMap != null) {
           if (locationMarker != null) {
               locationMarker!!.remove()
           }
           locationMarker = googleMap!!.addMarker(markerOptions)
           val zoom = 14f
           val cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLong, zoom)
           googleMap!!.animateCamera(cameraUpdate)
           googleMap!!.setOnMarkerClickListener {
              showDialogueRadius(latLong)
          }
       }
    }

    fun  showDialogueRadius(latLong: LatLng) : Boolean{
        var dialogue = AlertDialog.Builder(this)
        var view = layoutInflater.inflate(R.layout.dialogue_radus_set,null)
        dialogue.setView(view)
        dialogue.setTitle("Set Radius")
        dialogue.setCancelable(false)
        var seek_bar = view.findViewById<SeekBar>(R.id.seek_bar)
        var tv_meter = view.findViewById<TextView>(R.id.tv_meters)
        tv_meter.text = geoModel.toString()
        seek_bar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                tv_meter.text = progress.toString()
                updateRadius =progress.toFloat()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                Toast.makeText(this@MainActivity, "Selected Radius is " + seekBar.progress + "m", Toast.LENGTH_SHORT).show()
            }
        })
        dialogue.setPositiveButton(R.string.add_radius){ dialog,p1 ->
            geoModel = updateRadius
            var pref = PreferenceManager.getDefaultSharedPreferences(this).edit()
            getSharedPreferences(KEY_GEOFENCE_RADIUS,0).edit().clear().apply()
            pref.putFloat(KEY_GEOFENCE_UPDATE_RADIUS,updateRadius)
            pref.apply()
            updatePopulateGeofence(latLong,updateRadius)
        }
        dialogue.setNegativeButton(android.R.string.cancel) { dialog, p1 ->
              dialog.cancel()
          }
        dialogue.create()
        dialogue.show()
        return false
    }

    @SuppressLint("MissingPermission")
    fun showCurrentLocationOnMap() {
        if (isLocationAccessPermitted()) {
            requestPermissions()
        } else if (googleMap != null) {
            googleMap!!.isMyLocationEnabled = true
        }
    }
    fun isLocationAccessPermitted(): Boolean{
        return (ContextCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)
    }

    private fun ConnectionAddListner() = object : GoogleApiClient.ConnectionCallbacks {
        @SuppressLint("MissingPermission")
        override fun onConnected(p0: Bundle?) {
          getLastLocation()
          recoverGeofenceMarker()
        }
        override fun onConnectionSuspended(p0: Int) {
            Log.i(TAG,"Connection suspended for some reason")
        }
    }

    @SuppressLint("MissingPermission")
    fun getLastLocation(){
        if(checkPermissions()){
            Log.i(TAG,"Successfully connected")
            data = LocationServices.FusedLocationApi.getLastLocation(mgoogleapiClient)
            if(data == null){
                onMapDrag(data!!)
                requestLocation()
                fusedLocation!!.requestLocationUpdates(mgoogleapiClient,mLocationRequest,mGeofencePendingIntent)
                Log.i(TAG,"Location Request")
            }else{
                writeLocation(data!!)
                addGeofences(data!!)
            }
        }
        else{
            requestPermissions()
        }
    }

    fun writeLocation(data:Location){
        latitude = data.latitude
        longitude = data.longitude
        val latLong = com.google.android.gms.maps.model.LatLng(latitude!!,longitude!!)
        markerForGeofence(latLong)
    }

    private fun ConnectionFailedListner() = GoogleApiClient.OnConnectionFailedListener {
            Log.i(TAG,"Connection failed due to some reason")
    }

    private fun requestLocation(){
        mLocationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval(1*1000)
            .setFastestInterval(1*1000)
    }

     @SuppressLint("MissingSuperCall")
     override fun onStart(){
         super.onStart()
         if (!checkPermissions()) {
             requestPermissions()
         }
         mgoogleapiClient!!.connect()
    }

    override fun onResult(p0: Status) {
        Log.i(TAG, "onResult: $p0")
        if (p0.isSuccess()) {
            drawGeofence(geoModel)
        } else {

        }
    }

    @SuppressLint("MissingPermission")
    private fun addGeofences(data: Location) {
           recoverGeofenceMarker()
           geofenceTa =  populateGeofenceList(locationMarker!!.position,geoModel)
           geofencingClient!!.addGeofences(getGeofencingRequest(geofenceTa), getGeofencePendingIntent())
               .addOnCompleteListener(this)
    }

    @SuppressLint("MissingPermission")
    private fun addCustomGeofence(latLong: LatLng){
        geofenceCustonm = populateCustomGeofence(latLong,geoModel)
        geofencingClient!!.addGeofences(getGeofencingRequest(geofenceCustonm), getGeofencePendingIntent())
            .addOnCompleteListener(this)
    }

    private fun removeGeofences() {

        geofencingClient!!.removeGeofences(getGeofencePendingIntent()).addOnCompleteListener(this)

    }

    override fun onComplete(p0: Task<Void>) {
        if(p0.isSuccessful){
            updateGeofencesAdded(!getGeofencesAdded())
        }
        else{
            var error = GeofenceErrorMessages.getErrorString(this, p0.exception!!)
            Log.i(TAG,"Found error $error")
        }
    }

    private fun saveGeofence() {
        Log.d(TAG, "saveGeofence()")
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putFloat(KEY_GEOFENCE_RADIUS,geoModel)
            .apply()

    }

    private fun recoverGeofenceMarker() {
        Log.d(TAG, "recoverGeofenceMarker")
        if(Updated == 0f){
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
                var radiusSaved  = sharedPref.getFloat(KEY_GEOFENCE_RADIUS,0.0f)
                geoModel = radiusSaved
                drawGeofence(geoModel)
        }else{
            val pref = PreferenceManager.getDefaultSharedPreferences(this)
            Updated =  pref.getFloat(KEY_GEOFENCE_UPDATE_RADIUS,0f)
            geoModel=Updated
            drawGeofence(Updated)
        }
        drawGeofence(geoModel)

    }

    private fun showSnackbar(text: String) {
        val container = findViewById<View>(android.R.id.content)
        if (container != null) {
            Snackbar.make(container, text, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun populateGeofenceList(latLong: LatLng,geoModel: Float): Geofence? {

        var circleRadius = geoModel
        return Geofence.Builder()
                                .setRequestId(Constant.GEOFENCES_ADDED_KEY)
                                .setCircularRegion(latLong.latitude, latLong.longitude,circleRadius)
                                .setExpirationDuration(Constant.GEOFENCE_EXPIRATION_IN_MILLISECONDS)
                                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                                .build()
            }

    private fun populateCustomGeofence(latLong: LatLng,geoModel: Float): Geofence? {

        var circleRadius = geoModel
        return Geofence.Builder()
            .setRequestId(Constant.GEOFENCES_ADDED_KEY)
            .setCircularRegion(latLong.latitude, latLong.longitude,circleRadius)
            .setExpirationDuration(Constant.GEOFENCE_EXPIRATION_IN_MILLISECONDS)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()
    }

    private fun drawGeofence(geoModel: Float) {
        Log.d(TAG, "drawGeofence()")

        if (geoFenceLimits != null)
            geoFenceLimits!!.remove()

        val circleOptions = CircleOptions()
            .center(locationMarker!!.position)
            .strokeColor(Color.argb(206, 249, 57, 70))
            .fillColor(Color.argb(134, 222, 102, 110))
            .radius(geoModel.toDouble())
        geoFenceLimits = googleMap!!.addCircle(circleOptions)
    }

    private fun drawCustomGeofence(geoModel: Float){

        if(customGeoFence != null)
            customGeoFence!!.remove()

        val circleOptions = CircleOptions()
            .center(latLong)
            .strokeColor(Color.argb(206, 249, 57, 70))
            .fillColor(Color.argb(134, 222, 102, 110))
            .radius(geoModel.toDouble())
        customGeoFence = googleMap!!.addCircle(circleOptions)
    }

    fun updatePopulateGeofence(latLong: LatLng,geoModel: Float) {
      drawGeofence(geoModel)
      addGeofences(data!!)
    }

    private fun getGeofencesAdded(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
            Constant.GEOFENCES_ADDED_KEY, false
        )
    }

    private fun updateGeofencesAdded(added: Boolean) {
        if(added ==  true){
            PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putBoolean(Constant.GEOFENCES_ADDED_KEY, added)
                .apply()
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {

        val shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.")
            showSnackbarText(R.string.permission_rationale, android.R.string.ok,
                View.OnClickListener {
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        REQUEST_PERMISSIONS_REQUEST_CODE
                    )
                })
        }
        else {
            Log.i(TAG, "Requesting permission")
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private fun showSnackbarText(mainTextStringId:Int, actionStringId:Int, listener:View.OnClickListener)  {

    }

    private fun getGeofencingRequest(geofence: Geofence?): GeofencingRequest {
        return GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
           addGeofence(geofence)
        }.build()
    }

    private fun getGeofencePendingIntent(): PendingIntent{
        if (mGeofencePendingIntent != null) {
            return mGeofencePendingIntent!!
        }
        val intent =   Intent(this, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onLocationChanged(p0: Location?) {
        try{
            if(p0 == null){
                onMapDrag(p0!!)
                LocationServices.FusedLocationApi.removeLocationUpdates(
                    mgoogleapiClient, this
                )
            }
        }catch (e:Exception){
            Log.i(TAG,"ERROR $e")
        }
    }

    fun getAddress(latitude:Double,longitude:Double) {

        latLong = LatLng(latitude,longitude)
        try {
           val geocoder =  Geocoder(this, Locale.getDefault());
            addressList = geocoder.getFromLocation(latitude, longitude, 1)
            var mLocationMarkerText = findViewById<TextView>(R.id.locationMarkertext)
            mLocationMarkerText.setText("Lat : " + latitude + "," + "Long : " +longitude)
           // customMarker(latLong)
        } catch (e:Exception) {
            Log.i(TAG,"Error $e");
        }
    }

    @SuppressLint("MissingPermission")
    fun onMapDrag(location: Location){
        if(checkPermissions()){
            if(googleMap != null){
                googleMap!!.uiSettings.isZoomControlsEnabled = false
                googleMap!!.isMyLocationEnabled = true
                googleMap!!.uiSettings.isMyLocationButtonEnabled = true

                var latLong = LatLng(location.latitude,location.longitude)
                var camperPosition = CameraPosition.builder().target(latLong).zoom(14f).tilt(70f).build()
                var cameraUpdateFactory = CameraUpdateFactory.newCameraPosition(camperPosition)
                var mLocationMarkerText = findViewById<TextView>(R.id.locationMarkertext)
                mLocationMarkerText.setText("Lat : " + latitude + "," + "Long : " +longitude)
                googleMap!!.animateCamera(cameraUpdateFactory)
                //customMarker(latLong)
            }else{

                Toast.makeText(this,"Sorry! Unable to create map",Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun customMarker(latLong: LatLng){
        Log.i(TAG,"Location is $latLong")
        val title = "Update Location:"+latLong.latitude + " ," + latLong.longitude
        val markerOptions = MarkerOptions()
            .position(latLong)
            .title(title)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))

        if(googleMap != null){
            if(customMarker != null){
                customMarker!!.remove()
            }
            customMarker = googleMap!!.addMarker(markerOptions)
        }

    }

}

