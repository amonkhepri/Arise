package com.example.rise.extensions

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.text.TextUtils
import com.example.rise.helpers.ALARM_ID
import com.example.rise.helpers.BaseConfig
import com.example.rise.helpers.MESSAGE_CONTENT
import com.example.rise.helpers.PREFS_KEY
import com.example.rise.helpers.isMarshmallowPlus
import com.example.rise.ui.alarm.models.Alarm
import com.example.rise.ui.alarm.receivers.AlarmReceiver
import java.io.File
import java.util.*
import java.util.regex.Pattern


fun Context.scheduleNextMessage(alarm: Alarm) {
    val intent = Intent(this, AlarmReceiver::class.java)
    val bundle = Bundle()

    bundle.putParcelable("alarm", alarm)
    intent.putExtra(MESSAGE_CONTENT, bundle)
    intent.putExtra(ALARM_ID, alarm.idTimeStamp)

    val pendingIntent : PendingIntent = PendingIntent.getBroadcast (
        this,
        alarm.idTimeStamp,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val alarmManager : AlarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    // TODO time isn't exact, using "setAlarmClock()" instead might be a solution to this dilemma
    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.timeInMiliseconds, pendingIntent)
}

val Context.baseConfig: BaseConfig get() = BaseConfig.newInstance(this)

fun Context.getSharedPrefs() = getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)

fun Context.getSDCardPath(): String {
    val directories = getStorageDirectories().filter {
        it != getInternalStoragePath() && (baseConfig.OTGPartition.isEmpty() || !it.endsWith(
            baseConfig.OTGPartition
        ))
    }

    val fullSDpattern = Pattern.compile("^/storage/[A-Za-z0-9]{4}-[A-Za-z0-9]{4}$")
    var sdCardPath = directories.firstOrNull { fullSDpattern.matcher(it).matches() }
        ?: directories.firstOrNull { !physicalPaths.contains(it.lowercase(Locale.ROOT)) } ?: ""

    // on some devices no method retrieved any SD card path, so test if its not sdcard1 by any chance. It happened on an Android 5.1
    if (sdCardPath.trimEnd('/').isEmpty()) {
        val file = File("/storage/sdcard1")
        if (file.exists()) {
            return file.absolutePath
        }

        sdCardPath = directories.firstOrNull() ?: ""
    }

    if (sdCardPath.isEmpty()) {
        val SDpattern = Pattern.compile("^[A-Za-z0-9]{4}-[A-Za-z0-9]{4}$")
        try {
            File("/storage").listFiles()?.forEach {
                if (SDpattern.matcher(it.name).matches()) {
                    sdCardPath = "/storage/${it.name}"
                }
            }
        } catch (_: Exception) {
        }
    }

    val finalPath = sdCardPath.trimEnd('/')
    baseConfig.sdCardPath = finalPath
    return finalPath
}
fun getInternalStoragePath() = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')

// avoid these being set as SD card paths
private val physicalPaths = arrayListOf(
    "/storage/sdcard1", // Motorola Xoom
    "/storage/extsdcard", // Samsung SGS3
    "/storage/sdcard0/external_sdcard", // User request
    "/mnt/extsdcard", "/mnt/sdcard/external_sd", // Samsung galaxy family
    "/mnt/external_sd", "/mnt/media_rw/sdcard1", // 4.4.2 on CyanogenMod S3
    "/removable/microsd", // Asus transformer prime
    "/mnt/emmc", "/storage/external_SD", // LG
    "/storage/ext_sd", // HTC One Max
    "/storage/removable/sdcard1", // Sony Xperia Z1
    "/data/sdext", "/data/sdext2", "/data/sdext3", "/data/sdext4", "/sdcard1", // Sony Xperia Z
    "/sdcard2", // HTC One M8s
    "/storage/usbdisk0",
    "/storage/usbdisk1",
    "/storage/usbdisk2"
)

fun Context.getStorageDirectories(): Array<String> {
    val paths = HashSet<String>()
    val rawExternalStorage = System.getenv("EXTERNAL_STORAGE")
    val rawSecondaryStoragesStr = System.getenv("SECONDARY_STORAGE")
    val rawEmulatedStorageTarget = System.getenv("EMULATED_STORAGE_TARGET")
    if (TextUtils.isEmpty(rawEmulatedStorageTarget)) {
        if (isMarshmallowPlus()) {
            getExternalFilesDirs(null).filterNotNull().map { it.absolutePath }
                .mapTo(paths) { it.substring(0, it.indexOf("Android/data")) }
        } else {
            if (TextUtils.isEmpty(rawExternalStorage)) {
                paths.addAll(physicalPaths)
            } else {
                if (rawExternalStorage != null) {
                    paths.add(rawExternalStorage)
                }
            }
        }
    } else {
        val path = Environment.getExternalStorageDirectory().absolutePath
        val folders = Pattern.compile("/").split(path)
        val lastFolder = folders[folders.size - 1]
        var isDigit = false
        try {
            Integer.valueOf(lastFolder)
            isDigit = true
        } catch (_: NumberFormatException) {
        }

        val rawUserId = if (isDigit) lastFolder else ""
        if (TextUtils.isEmpty(rawUserId)) {
            rawEmulatedStorageTarget?.let(paths::add)
        } else {
            rawEmulatedStorageTarget?.let { target ->
                paths.add(target + File.separator + rawUserId)
            }
        }
    }

    if (!rawSecondaryStoragesStr.isNullOrEmpty()) {
        val rawSecondaryStorages = rawSecondaryStoragesStr.split(File.pathSeparator.toRegex())
            .dropLastWhile(String::isEmpty).toTypedArray()
        Collections.addAll(paths, *rawSecondaryStorages)
    }
    return paths.map { it.trimEnd('/') }.toTypedArray()
}
