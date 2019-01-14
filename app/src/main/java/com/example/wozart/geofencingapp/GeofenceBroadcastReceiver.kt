package com.example.wozart.geofencingapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        GeofenceService().enqueueWork(context!!,intent!!)
    }
}