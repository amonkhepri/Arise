package com.example.rise.data.alarm

import com.example.rise.helpers.Config

class ConfigReminderPreferences(
    private val config: Config,
) : ReminderPreferences {

    override val alarmMaxReminderSeconds: Int
        get() = config.alarmMaxReminderSecs

    override val timerMaxReminderSeconds: Int
        get() = config.timerMaxReminderSecs

    override val increaseVolumeGradually: Boolean
        get() = config.increaseVolumeGradually

    override val useSameSnooze: Boolean
        get() = config.useSameSnooze

    override var snoozeMinutes: Int
        get() = config.snoozeTime
        set(value) {
            config.snoozeTime = value
        }

    override val timerSoundUri: String?
        get() = config.timerSoundUri
}
