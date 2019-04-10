package com.example.rise.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import com.example.rise.extensions.*
import com.example.rise.helpers.ALARM_ID
import com.example.rise.ui.ReminderActivity


class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(ALARM_ID, -1)
       //TODO implement firestore
        val alarm = context.dbHelper.getAlarmWithId(id) ?: return

        if (context.isScreenOn()) {
            context.showAlarmNotification(alarm)
            Handler().postDelayed({
                context.hideNotification(id)
            }, 5* 1000L)
        } else {
            Intent(context, ReminderActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(ALARM_ID, id)
                context.startActivity(this)
            }
        }
    }
}


}