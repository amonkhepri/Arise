package com.example.rise.services

import android.app.IntentService
import android.content.Intent
import com.example.rise.extensions.hideNotification
import com.example.rise.extensions.setupAlarmClock
import com.example.rise.helpers.ALARM_ID
import com.example.rise.helpers.MINUTE_SECONDS
import com.simplemobiletools.clock.extensions.config
import com.simplemobiletools.clock.extensions.dbHelper
import com.simplemobiletools.clock.extensions.hideNotification
import com.simplemobiletools.clock.extensions.setupAlarmClock
import com.simplemobiletools.clock.helpers.ALARM_ID
import com.simplemobiletools.commons.helpers.MINUTE_SECONDS

class SnoozeService : IntentService("Snooze") {
    override fun onHandleIntent(intent: Intent) {
        val id = intent.getIntExtra(ALARM_ID, -1)
        val alarm = dbHelper.getAlarmWithId(id) ?: return
        hideNotification(id)
        setupAlarmClock(alarm, config.snoozeTime * MINUTE_SECONDS)
    }
}
