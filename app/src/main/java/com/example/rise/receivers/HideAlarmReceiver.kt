package com.example.rise.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.rise.extensions.hideNotification
import com.example.rise.helpers.ALARM_ID


class HideAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(ALARM_ID, -1)
        context.hideNotification(id)
    }
}
