package com.example.rise.ui.dashboardNavigation.dashboard

import com.example.rise.models.Alarm
import com.google.firebase.firestore.Query

interface AlarmQuery {
    fun unwrap(): Query
}

data class FirestoreAlarmQuery(private val query: Query) : AlarmQuery {
    override fun unwrap(): Query = query
}

data class UserHandle(val userId: String)

interface DashboardRepository {
    fun resolveUser(userId: String? = null): UserHandle
    fun buildAlarmQuery(handle: UserHandle): AlarmQuery
    fun incrementAlarmId(handle: UserHandle)
    fun saveAlarm(handle: UserHandle, alarm: Alarm)
}
