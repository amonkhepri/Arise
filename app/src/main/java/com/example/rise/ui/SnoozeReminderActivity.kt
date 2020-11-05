package com.example.rise.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.rise.helpers.ALARM_ID


class SnoozeReminderActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(ALARM_ID, -1)
    }
}
