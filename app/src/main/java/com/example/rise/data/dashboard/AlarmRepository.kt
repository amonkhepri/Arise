package com.example.rise.data.dashboard

import com.example.rise.models.Alarm
import com.google.firebase.firestore.Query

interface AlarmRepository {
    fun alarmsQuery(userId: String): Query
    suspend fun saveAlarm(userId: String, alarm: Alarm)
}
