package com.example.rise.data.dashboard

import com.example.rise.ui.alarm.models.Alarm
import com.google.firebase.firestore.Query

interface AlarmRepository {
    fun alarmsQuery(userId: String): AlarmQuery
    suspend fun saveAlarm(userId: String, alarm: Alarm)

    interface AlarmQuery {
        fun asFirestoreQuery(): Query
    }
}
