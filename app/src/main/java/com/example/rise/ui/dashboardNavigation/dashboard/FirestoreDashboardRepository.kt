package com.example.rise.ui.dashboardNavigation.dashboard

import com.example.rise.auth.UserSessionProvider
import com.example.rise.models.Alarm
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class FirestoreDashboardRepository(
    private val firestore: FirebaseFirestore,
    private val userSessionProvider: UserSessionProvider
) : DashboardRepository {

    override fun resolveUser(userId: String?): UserHandle {
        val resolvedId = userId ?: userSessionProvider.currentUserId()
        return UserHandle(resolvedId)
    }

    override fun buildAlarmQuery(handle: UserHandle): AlarmQuery {
        val document = firestore.document("users/${handle.userId}")
        return FirestoreAlarmQuery(document.collection("alarms"))
    }

    override fun incrementAlarmId(handle: UserHandle) {
        firestore.document("users/${handle.userId}")
            .update("id", FieldValue.increment(1))
    }

    override fun saveAlarm(handle: UserHandle, alarm: Alarm) {
        firestore.document("users/${handle.userId}")
            .collection("alarms")
            .document(alarm.idTimeStamp.toString())
            .set(alarm)
    }
}
