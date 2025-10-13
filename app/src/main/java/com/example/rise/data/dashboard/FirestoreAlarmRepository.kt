package com.example.rise.data.dashboard

import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.ui.alarm.models.Alarm
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class FirestoreAlarmRepository(
    private val firestore: FirebaseFirestore,
    private val transportBridge: TransportRuntimeBridge,
) : AlarmRepository {

    private fun userDocument(userId: String) = firestore.collection("users").document(userId)

    override fun alarmsQuery(userId: String): Query {
        transportBridge.requireFirestore("FirestoreAlarmRepository#alarmsQuery")
        return userDocument(userId)
            .collection("alarms")
            .orderBy("timeInMiliseconds")
    }

    override suspend fun saveAlarm(userId: String, alarm: Alarm) {
        transportBridge.requireFirestore("FirestoreAlarmRepository#saveAlarm")
        val userDoc = userDocument(userId)
        userDoc.collection("alarms")
            .document(alarm.idTimeStamp.toString())
            .set(alarm)
            .await()
        userDoc.update("id", FieldValue.increment(1)).await()
    }
}
