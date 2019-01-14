package com.example.wozart.geofencingapp

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.support.v4.app.JobIntentService
import android.text.TextUtils
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import okhttp3.*
import java.io.IOException


class GeofenceService : JobIntentService() {
    private lateinit var client: OkHttpClient
     private var JOB_ID = 573
     private var TAG = "GeoService"

     fun enqueueWork(context: Context, intent: Intent) {
          enqueueWork(context, GeofenceService::class.java, JOB_ID, intent)
     }

     override fun onHandleWork(intent: Intent) {
         var geofenceEvent = GeofencingEvent.fromIntent(intent)
         var geofencetriggring: MutableList<Geofence> = ArrayList()
         if(geofenceEvent.hasError()){
             var geofenceError = GeofenceErrorMessages.getErrorString(this,geofenceEvent.errorCode)
             Log.i(TAG, "Found error$geofenceError")
         }
         val geofenceTransition = geofenceEvent.geofenceTransition
         if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER || geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT ){
             geofencetriggring = geofenceEvent.triggeringGeofences
             var geofenceDetails = getGeofenceDetails(this,geofenceTransition,geofencetriggring)
             Log.i(TAG,geofenceDetails)
         }else{
             Log.i(TAG,"Error in transition")
         }
     }

     fun getGeofenceDetails(context: Context,geofenceTransition:Int,triggring:MutableList<Geofence>):String {
         Log.d(TAG, "===============> getGeofenceTransitionDetails()")
         val geofenceTransitionString = getTransitionString(geofenceTransition)
         val triggeringGeofencesIdsList = triggring.map { geofence -> geofence.requestId }
         return geofenceTransitionString.toString() + ": " + TextUtils.join(", " , triggeringGeofencesIdsList)
     }


   private fun sendNotification(message:String,Detail:String){
       client = OkHttpClient()
       val request = Request.Builder().url("https://kxulur3hsi.execute-api.ap-south-1.amazonaws.com/beta/")
           .addHeader("Geo-fence", "Transition " + message)
           .build()
       client.newCall(request).enqueue(object : Callback {
           override fun onFailure(call: Call, e: IOException) {
               call.cancel()
           }

           override fun onResponse(call: Call, response: Response) {
                   try {
                        val message = response.body().string()
                   } catch (ioe: IOException) {
                       Log.d("Geofence", "Entered : " + "Error during get body")
                   }
               }
       })
    }

    fun getTransitionString(geofenceTransition:Int){
         var am: AudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
         Log.i(TAG, "geofenceTransition = " + geofenceTransition + " Enter : " +
                 Geofence.GEOFENCE_TRANSITION_ENTER + "Exit : " + Geofence.GEOFENCE_TRANSITION_EXIT)
         when (geofenceTransition) {
             Geofence.GEOFENCE_TRANSITION_ENTER  -> {
                 sendNotification("Entered", "Entered the Location,Your all selected lights is 0N")
                 am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
             }
             Geofence.GEOFENCE_TRANSITION_EXIT -> {
                 sendNotification("Exited", "Exited the Location, Your all lights is Off")
                 am.ringerMode = AudioManager.RINGER_MODE_NORMAL
             }
             else -> {
                 println("oh! cow eyes something bad happen!!")
                 Log.e(TAG, "Error ")
             }
         }
    }
}