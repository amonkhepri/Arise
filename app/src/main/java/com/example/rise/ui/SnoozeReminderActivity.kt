package com.example.rise.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.rise.extensions.config
import com.example.rise.extensions.hideNotification
import com.example.rise.extensions.setupAlarmClock
import com.example.rise.extensions.showPickSecondsDialog
import com.example.rise.helpers.ALARM_ID
import com.example.rise.helpers.MINUTE_SECONDS


class SnoozeReminderActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(ALARM_ID, -1)

        val alarm = dbHelper.getAlarmWithId(id) ?: return

        hideNotification(id)

        showPickSecondsDialog(config.snoozeTime * MINUTE_SECONDS, true, cancelCallback = { dialogCancelled() }) {
            config.snoozeTime = it / MINUTE_SECONDS
            setupAlarmClock(alarm, it)
            finishActivity()
        }
    }

    private fun dialogCancelled() {
        finishActivity()
    }

    private fun finishActivity() {
        finish()
        overridePendingTransition(0, 0)
    }
}
