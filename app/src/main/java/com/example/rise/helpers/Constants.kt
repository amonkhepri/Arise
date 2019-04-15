package com.example.rise.helpers

import android.content.Context
import android.os.Build
import android.os.Looper
import com.example.rise.R
import java.util.*



const val SHOW_SECONDS = "show_seconds"
const val SELECTED_TIME_ZONES = "selected_time_zones"
const val EDITED_TIME_ZONE_TITLES = "edited_time_zone_titles"
const val TIMER_SECONDS = "timer_seconds"
const val TIMER_VIBRATE = "timer_vibrate"
const val TIMER_SOUND_URI = "timer_sound_uri"
const val TIMER_SOUND_TITLE = "timer_sound_title"
const val TIMER_MAX_REMINDER_SECS = "timer_max_reminder_secs"
const val ALARM_MAX_REMINDER_SECS = "alarm_max_reminder_secs"
const val USE_TEXT_SHADOW = "use_text_shadow"
const val INCREASE_VOLUME_GRADUALLY = "increase_volume_gradually"

const val TABS_COUNT = 4
const val EDITED_TIME_ZONE_SEPARATOR = ":"
const val ALARM_ID = "alarm_id"
const val DEFAULT_ALARM_MINUTES = 480
const val DEFAULT_MAX_ALARM_REMINDER_SECS = 300
const val DEFAULT_MAX_TIMER_REMINDER_SECS = 60

const val PICK_AUDIO_FILE_INTENT_ID = 9994
const val REMINDER_ACTIVITY_INTENT_ID = 9995
const val OPEN_ALARMS_TAB_INTENT_ID = 9996
const val UPDATE_WIDGET_INTENT_ID = 9997
const val OPEN_APP_INTENT_ID = 9998
const val TIMER_NOTIF_ID = 9999

const val REAL_FILE_PATH = "real_file_path_2"
const val IS_FROM_GALLERY = "is_from_gallery"
const val BROADCAST_REFRESH_MEDIA = "com.simplemobiletools.REFRESH_MEDIA"
const val REFRESH_PATH = "refresh_path"
const val OTG_PATH = "otg:/"
const val IS_CUSTOMIZING_COLORS = "is_customizing_colors"
const val ALARM_SOUND_TYPE_ALARM = 1
const val ALARM_SOUND_TYPE_NOTIFICATION = 2
const val YOUR_ALARM_SOUNDS_MIN_ID = 1000

const val OPEN_TAB = "open_tab"
const val TAB_CLOCK = 0
const val TAB_ALARM = 1
const val TAB_STOPWATCH = 2
const val TAB_TIMER = 3

const val SORT_BY_LAP = 1
const val SORT_BY_LAP_TIME = 2
const val SORT_BY_TOTAL_TIME = 4



const val HOUR_MINUTES = 60
const val DAY_MINUTES = 24 * HOUR_MINUTES
const val WEEK_MINUTES = DAY_MINUTES * 7
const val MONTH_MINUTES = DAY_MINUTES * 30
const val YEAR_MINUTES = DAY_MINUTES * 365

const val MINUTE_SECONDS = 60
const val HOUR_SECONDS = HOUR_MINUTES * 60
const val DAY_SECONDS = DAY_MINUTES * 60
const val WEEK_SECONDS = WEEK_MINUTES * 60
const val MONTH_SECONDS = MONTH_MINUTES * 60
const val YEAR_SECONDS = YEAR_MINUTES * 60

const val SILENT = "silent"


fun formatTime(showSeconds: Boolean, use24HourFormat: Boolean, hours: Int, minutes: Int, seconds: Int): String {
    val hoursFormat = if (use24HourFormat) "%02d" else "%01d"
    var format = "$hoursFormat:%02d"

    return if (showSeconds) {
        format += ":%02d"
        String.format(format, hours, minutes, seconds)
    } else {
        String.format(format, hours, minutes)
    }
}

fun isOnMainThread() = Looper.myLooper() == Looper.getMainLooper()


fun getPassedSeconds(): Int {


    val calendar = Calendar.getInstance()
    val isDaylightSavingActive = TimeZone.getDefault().inDaylightTime(Date())
    var offset = calendar.timeZone.rawOffset


    if (isDaylightSavingActive) {

        offset += TimeZone.getDefault().dstSavings

    }

    return ((calendar.timeInMillis + offset) / 1000).toInt()

}

const val PREFS_KEY = "Prefs"

fun isOreoPlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O




