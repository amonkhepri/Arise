package com.example.rise.helpers

import android.content.Context
import com.example.rise.extensions.getDefaultAlarmTitle
import com.example.rise.extensions.getDefaultAlarmUri

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    var timerSoundUri: String?
        get() = prefs.getString(TIMER_SOUND_URI, context.getDefaultAlarmUri(ALARM_SOUND_TYPE_ALARM).toString())
        set(timerSoundUri) = prefs.edit().putString(TIMER_SOUND_URI, timerSoundUri).apply()

    var timerMaxReminderSecs: Int
        get() = prefs.getInt(TIMER_MAX_REMINDER_SECS, DEFAULT_MAX_TIMER_REMINDER_SECS)
        set(timerMaxReminderSecs) = prefs.edit().putInt(TIMER_MAX_REMINDER_SECS, timerMaxReminderSecs).apply()

    var alarmMaxReminderSecs: Int
        get() = prefs.getInt(ALARM_MAX_REMINDER_SECS, DEFAULT_MAX_ALARM_REMINDER_SECS)
        set(alarmMaxReminderSecs) = prefs.edit().putInt(ALARM_MAX_REMINDER_SECS, alarmMaxReminderSecs).apply()

    var increaseVolumeGradually: Boolean
        get() = prefs.getBoolean(INCREASE_VOLUME_GRADUALLY, true)
        set(increaseVolumeGradually) = prefs.edit().putBoolean(INCREASE_VOLUME_GRADUALLY, increaseVolumeGradually).apply()
}