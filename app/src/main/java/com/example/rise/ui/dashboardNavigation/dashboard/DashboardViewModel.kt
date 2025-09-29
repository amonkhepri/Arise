package com.example.rise.ui.dashboardNavigation.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.rise.auth.UserSessionProvider
import com.example.rise.baseclasses.BaseViewModel
import com.example.rise.models.Alarm
import com.example.rise.models.TextMessage
import com.example.rise.util.Event

class DashboardViewModel(
    private val repository: DashboardRepository,
    private val userSessionProvider: UserSessionProvider
) : BaseViewModel() {

    sealed class DashboardEvent {
        data class ScheduleAlarm(val alarm: Alarm) : DashboardEvent()
    }

    private val _alarmQuery = MutableLiveData<AlarmQuery?>()
    val alarmQuery: LiveData<AlarmQuery?> = _alarmQuery

    private val _events = MutableLiveData<Event<DashboardEvent>>()
    val events: LiveData<Event<DashboardEvent>> = _events

    private val _otherUserId = MutableLiveData<String?>()
    val otherUserId: LiveData<String?> = _otherUserId

    private var userHandle: UserHandle? = null
    private var pendingMessage: TextMessage? = null
    private var pendingChatChannel: String = ""

    fun initialize(
        userIdFromIntent: String?,
        chatChannel: String?,
        message: TextMessage?,
        launchedFromBottomNavigation: Boolean
    ) {
        pendingMessage = message
        pendingChatChannel = chatChannel.orEmpty()

        val resolvedUserId = if (!launchedFromBottomNavigation && userIdFromIntent != null) {
            _otherUserId.value = userIdFromIntent
            userIdFromIntent
        } else {
            _otherUserId.value = null
            null
        }

        userHandle = repository.resolveUser(resolvedUserId)
        _alarmQuery.value = userHandle?.let { repository.buildAlarmQuery(it) }
    }

    fun createAlarm(timeInMillis: Long) {
        val handle = userHandle ?: return
        val alarm = Alarm().apply {
            chatChannel = pendingChatChannel
            messsage = pendingMessage
            userName = userSessionProvider.currentUserName()
            timeInMiliseconds = timeInMillis
            idTimeStamp = System.currentTimeMillis().toInt()
        }

        repository.incrementAlarmId(handle)
        repository.saveAlarm(handle, alarm)

        if (pendingMessage != null) {
            _events.value = Event(DashboardEvent.ScheduleAlarm(alarm))
        }
    }
}
