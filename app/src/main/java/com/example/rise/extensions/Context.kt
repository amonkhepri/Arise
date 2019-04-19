package com.example.rise.extensions

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import java.util.*
import android.os.PowerManager
import com.example.rise.R
import com.example.rise.models.Alarm
import kotlin.math.pow
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.media.RingtoneManager
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import com.example.rise.models.AlarmSound
import com.example.rise.helpers.*
import com.example.rise.receivers.AlarmReceiver
import com.example.rise.receivers.HideAlarmReceiver
import com.example.rise.services.SnoozeService
import com.example.rise.ui.MainActivity
import com.example.rise.ui.SnoozeReminderActivity


fun Context.isScreenOn() = (getSystemService(Context.POWER_SERVICE) as PowerManager).isScreenOn


fun Context.updateTextColors(viewGroup: ViewGroup, tmpTextColor: Int = 0, tmpAccentColor: Int = 0) {
    val textColor = if (tmpTextColor == 0) baseConfig.textColor else tmpTextColor
    val backgroundColor = baseConfig.backgroundColor
    val accentColor = if (tmpAccentColor == 0) {
        if (isBlackAndWhiteTheme()) {
            Color.WHITE
        } else {
            baseConfig.primaryColor
        }
    } else {
        tmpAccentColor
    }

    //TODO stubbing?
/*    val cnt = viewGroup.childCount
    (0 until cnt).map { viewGroup.getChildAt(it) }
        .forEach {
            when (it) {
                is MyTextView -> it.setColors(textColor, accentColor, backgroundColor)
                is MyAppCompatSpinner -> it.setColors(textColor, accentColor, backgroundColor)
                is MySwitchCompat -> it.setColors(textColor, accentColor, backgroundColor)
                is MyCompatRadioButton -> it.setColors(textColor, accentColor, backgroundColor)
                is MyAppCompatCheckbox -> it.setColors(textColor, accentColor, backgroundColor)
                is MyEditText -> it.setColors(textColor, accentColor, backgroundColor)
                is MyFloatingActionButton -> it.setColors(textColor, accentColor, backgroundColor)
                is MySeekBar -> it.setColors(textColor, accentColor, backgroundColor)
                is MyButton -> it.setColors(textColor, accentColor, backgroundColor)
                is ViewGroup -> updateTextColors(it, textColor, accentColor)
            }
        }*/
}



fun Context.getDefaultAlarmUri(type: Int) = RingtoneManager.getDefaultUri(if (type == ALARM_SOUND_TYPE_NOTIFICATION) RingtoneManager.TYPE_NOTIFICATION else RingtoneManager.TYPE_ALARM)

fun Context.getDefaultAlarmTitle(type: Int): String {
    val alarmString = getString(R.string.alarm)
    return try {
        RingtoneManager.getRingtone(this, getDefaultAlarmUri(type))?.getTitle(this) ?: alarmString
    } catch (e: Exception) {
        alarmString
    }
}

fun Context.getDefaultAlarmSound(type: Int) = AlarmSound(0, getDefaultAlarmTitle(type), getDefaultAlarmUri(type).toString())


val Context.config: Config get() = Config.newInstance(applicationContext)



fun Context.getLaunchIntent() = packageManager.getLaunchIntentForPackage(baseConfig.appId)

fun Context.showAlarmNotification(alarm: Alarm) {
    val pendingIntent = getOpenAlarmTabIntent()
    val notification = getAlarmNotification(pendingIntent, alarm)
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(alarm.id, notification)
    scheduleNextAlarm(alarm, false)
}




fun Context.scheduleNextAlarm(alarm: Alarm, showToast: Boolean) {
    val calendar = Calendar.getInstance()
    calendar.firstDayOfWeek = Calendar.MONDAY
    for (i in 0..7) {
        val currentDay = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val isCorrectDay = alarm.days and 2.0.pow(currentDay).toInt() != 0
        val currentTimeInMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        if (isCorrectDay && (alarm.timeInMinutes > currentTimeInMinutes || i > 0)) {
            val triggerInMinutes = alarm.timeInMinutes - currentTimeInMinutes + (i * DAY_MINUTES)
            setupAlarmClock(alarm, triggerInMinutes * 60 - calendar.get(Calendar.SECOND))

            if (showToast) {
                showRemainingTimeMessage(triggerInMinutes)
            }
            break
        } else {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
}



fun Context.formatMinutesToTimeString(totalMinutes: Int) = formatSecondsToTimeString(totalMinutes * 60)

fun Context.formatSecondsToTimeString(totalSeconds: Int): String {
    val days = totalSeconds / DAY_SECONDS
    val hours = (totalSeconds % DAY_SECONDS) / HOUR_SECONDS
    val minutes = (totalSeconds % HOUR_SECONDS) / MINUTE_SECONDS
    val seconds = totalSeconds % MINUTE_SECONDS
    val timesString = StringBuilder()

    if (days > 0) {
        val daysString = String.format(resources.getQuantityString(R.plurals.days, days, days))
        timesString.append("$daysString, ")
    }


    if (hours > 0) {
        val hoursString = String.format(resources.getQuantityString(R.plurals.hours, hours, hours))
        timesString.append("$hoursString, ")
    }

    if (minutes > 0) {
        val minutesString = String.format(resources.getQuantityString(R.plurals.minutes, minutes, minutes))
        timesString.append("$minutesString, ")
    }

    if (seconds > 0) {
        val secondsString = String.format(resources.getQuantityString(R.plurals.seconds, seconds, seconds))
        timesString.append(secondsString)
    }

    var result = timesString.toString().trim().trimEnd(',')
    if (result.isEmpty()) {
        result = String.format(resources.getQuantityString(R.plurals.minutes, 0, 0))
    }
    return result
}


val Context.baseConfig: BaseConfig get() = BaseConfig.newInstance(this)



fun Context.getAdjustedPrimaryColor() = if (isBlackAndWhiteTheme()) Color.WHITE else baseConfig.primaryColor

fun Context.isBlackAndWhiteTheme() = baseConfig.textColor == Color.WHITE && baseConfig.primaryColor == Color.BLACK && baseConfig.backgroundColor == Color.BLACK




fun Context.showRemainingTimeMessage(totalMinutes: Int) {
    val fullString = String.format(getString(R.string.alarm_goes_off_in), formatMinutesToTimeString(totalMinutes))
    toast(fullString, Toast.LENGTH_LONG)
}



fun Context.setupAlarmClock(alarm: Alarm, triggerInSeconds: Int) {
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val targetMS = System.currentTimeMillis() + triggerInSeconds * 1000
    AlarmManagerCompat.setAlarmClock(alarmManager, targetMS, getOpenAlarmTabIntent(), getAlarmIntent(alarm))


}

fun Context.getOpenAlarmTabIntent(): PendingIntent {
    val intent = getLaunchIntent() ?: Intent(this, MainActivity::class.java)
    intent.putExtra(OPEN_TAB, TAB_ALARM)
    return PendingIntent.getActivity(this, OPEN_ALARMS_TAB_INTENT_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT)
}



fun Context.getAlarmIntent(alarm: Alarm): PendingIntent {
    val intent = Intent(this, AlarmReceiver::class.java)
    intent.putExtra(ALARM_ID, alarm.id)
    return PendingIntent.getBroadcast(this, alarm.id, intent, PendingIntent.FLAG_UPDATE_CURRENT)
}


fun Context.grantReadUriPermission(uriString: String) {
    try {
        // ensure custom reminder sounds play well
        grantUriPermission("com.android.systemui", Uri.parse(uriString), Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (ignored: Exception) {
    }
}



fun Context.getSharedPrefs() = getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)

@SuppressLint("NewApi")
fun Context.getAlarmNotification(pendingIntent: PendingIntent, alarm: Alarm): Notification {
    var soundUri = alarm.soundUri
    if (soundUri == SILENT) {
        soundUri = ""
    } else {
        grantReadUriPermission(soundUri)
    }

    val channelId = "simple_alarm_channel_$soundUri"
    val label = if (alarm.label.isNotEmpty()) alarm.label else getString(R.string.alarm)



    if (isOreoPlus()) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_ALARM)
            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val importance = NotificationManager.IMPORTANCE_HIGH
        NotificationChannel(channelId, label, importance).apply {
            setBypassDnd(true)
            enableLights(true)
            lightColor = getAdjustedPrimaryColor()
            enableVibration(alarm.vibrate)
            setSound(Uri.parse(soundUri), audioAttributes)
            notificationManager.createNotificationChannel(this)
        }
    }

    val builder = NotificationCompat.Builder(this)
        .setContentTitle(label)
        .setContentText(getFormattedTime(getPassedSeconds(), false, false))
        .setSmallIcon(R.drawable.ic_alarm)
        .setContentIntent(pendingIntent)
        .setPriority(Notification.PRIORITY_HIGH)
        .setDefaults(Notification.DEFAULT_LIGHTS)
        .setAutoCancel(true)
        .setSound(Uri.parse(soundUri), AudioManager.STREAM_ALARM)
        .setChannelId(channelId)
        .addAction(R.drawable.ic_snooze, getString(R.string.snooze), getSnoozePendingIntent(alarm))
        .addAction(R.drawable.ic_cross, getString(R.string.dismiss), getHideAlarmPendingIntent(alarm))

    builder.setVisibility(Notification.VISIBILITY_PUBLIC)

    if (alarm.vibrate) {
        val vibrateArray = LongArray(2) { 500 }
        builder.setVibrate(vibrateArray)
    }

    val notification = builder.build()
    notification.flags = notification.flags or Notification.FLAG_INSISTENT
    return notification
}

fun Context.hideNotification(id: Int) {
    val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.cancel(id)
}


fun Context.toast(id: Int, length: Int = Toast.LENGTH_SHORT) {
    toast(getString(id), length)
}

fun Context.toast(msg: String, length: Int = Toast.LENGTH_SHORT) {
    try {
        if (isOnMainThread()) {
            Toast.makeText(applicationContext, msg, length).show()
        } else {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, msg, length).show()
            }
        }
    } catch (e: Exception) {
    }
}


fun Context.showErrorToast(msg: String, length: Int = Toast.LENGTH_LONG) {
    toast(String.format("An error occurred", msg), length)
}

fun Context.showErrorToast(exception: Exception, length: Int = Toast.LENGTH_LONG) {
    showErrorToast(exception.toString(), length)
}

fun Context.getSnoozePendingIntent(alarm: Alarm): PendingIntent {
    val snoozeClass = if (config.useSameSnooze) SnoozeService::class.java else SnoozeReminderActivity::class.java
    val intent = Intent(this, snoozeClass).setAction("Snooze")
    intent.putExtra(ALARM_ID, alarm.id)
    return if (config.useSameSnooze) {
        PendingIntent.getService(this, alarm.id, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    } else {
        PendingIntent.getActivity(this, alarm.id, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

fun Context.getHideAlarmPendingIntent(alarm: Alarm): PendingIntent {
    val intent = Intent(this, HideAlarmReceiver::class.java)
    intent.putExtra(ALARM_ID, alarm.id)
    return PendingIntent.getBroadcast(this, alarm.id, intent, PendingIntent.FLAG_UPDATE_CURRENT)
}

fun Context.getFormattedSeconds(seconds: Int, showBefore: Boolean = true) = when (seconds) {
    -1 -> getString(R.string.no_reminder)
    0 -> getString(R.string.at_start)
    else -> {
        if (seconds % YEAR_SECONDS == 0)
            resources.getQuantityString(R.plurals.years, seconds / YEAR_SECONDS, seconds / YEAR_SECONDS)

        when {
            seconds % MONTH_SECONDS == 0 -> resources.getQuantityString(R.plurals.months, seconds / MONTH_SECONDS, seconds / MONTH_SECONDS)
            seconds % WEEK_SECONDS == 0 -> resources.getQuantityString(R.plurals.weeks, seconds / WEEK_SECONDS, seconds / WEEK_SECONDS)
            seconds % DAY_SECONDS == 0 -> resources.getQuantityString(R.plurals.days, seconds / DAY_SECONDS, seconds / DAY_SECONDS)
            seconds % HOUR_SECONDS == 0 -> {
                val base = if (showBefore) R.plurals.hours_before else R.plurals.by_hours
                resources.getQuantityString(base, seconds / HOUR_SECONDS, seconds / HOUR_SECONDS)
            }
            seconds % MINUTE_SECONDS == 0 -> {
                val base = if (showBefore) R.plurals.minutes_before else R.plurals.by_minutes
                resources.getQuantityString(base, seconds / MINUTE_SECONDS, seconds / MINUTE_SECONDS)
            }
            else -> {
                val base = if (showBefore) R.plurals.seconds_before else R.plurals.by_seconds
                resources.getQuantityString(base, seconds, seconds)
            }
        }
    }
}

fun Context.getFormattedTime(passedSeconds: Int, showSeconds: Boolean, makeAmPmSmaller: Boolean): SpannableString {
    val use24HourFormat = config.use24HourFormat
    val hours = (passedSeconds / 3600) % 24
    val minutes = (passedSeconds / 60) % 60
    val seconds = passedSeconds % 60

    return if (!use24HourFormat) {
        val formattedTime = formatTo12HourFormat(showSeconds, hours, minutes, seconds)
        val spannableTime = SpannableString(formattedTime)
        val amPmMultiplier = if (makeAmPmSmaller) 0.4f else 1f
        spannableTime.setSpan(RelativeSizeSpan(amPmMultiplier), spannableTime.length - 5, spannableTime.length, 0)
        spannableTime
    } else {
        val formattedTime = formatTime(showSeconds, use24HourFormat, hours, minutes, seconds)
        SpannableString(formattedTime)
    }
}

fun Context.formatTo12HourFormat(showSeconds: Boolean, hours: Int, minutes: Int, seconds: Int): String {
    val appendable = getString(if (hours >= 12) R.string.p_m else R.string.a_m)
    val newHours = if (hours == 0 || hours == 12) 12 else hours % 12
    return "${formatTime(showSeconds, false, newHours, minutes, seconds)} $appendable"
}