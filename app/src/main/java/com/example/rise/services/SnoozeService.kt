package com.example.rise.services

import android.app.IntentService
import android.content.ContentValues.TAG
import android.content.Intent
import android.util.Log
import com.example.rise.extensions.config
import com.example.rise.extensions.hideNotification
import com.example.rise.extensions.setupAlarmClock
import com.example.rise.helpers.ALARM_ID
import com.example.rise.helpers.MINUTE_SECONDS
import com.example.rise.models.Alarm
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore


class SnoozeService : IntentService("Snooze") {
    override fun onHandleIntent(intent: Intent) {
        val id = intent.getIntExtra(ALARM_ID, -1)

        val alarms = ArrayList<Alarm>()

        val mFirestore = FirebaseFirestore.getInstance().document("sampleData/user")

        fun queryFirestore(): CollectionReference {

//TODO read operation

            if (alarm["myAlarm"] != null) {

                mFirestore.collection("alarms")
                    .add(alarm)
                    .addOnSuccessListener { documentReference ->
                        Log.d(TAG, "DocumentSnapshot add with ID: " + documentReference.id)
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Error adding document", e)
                    }
            }
            return mFirestore.collection("alarms")
        }

        alarm =queryFirestore()

            //dbHelper.getAlarmWithId(id) ?: return
        hideNotification(id)
        setupAlarmClock(alarm, config.snoozeTime * MINUTE_SECONDS)




    }
}
