package com.example.rise.data.alarm

interface ReminderPreferences {
    val alarmMaxReminderSeconds: Int
    val timerMaxReminderSeconds: Int
    val increaseVolumeGradually: Boolean
    val useSameSnooze: Boolean
    var snoozeMinutes: Int
    val timerSoundUri: String?
}
