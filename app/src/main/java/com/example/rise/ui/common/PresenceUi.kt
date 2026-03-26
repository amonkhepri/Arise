package com.example.rise.ui.common

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.example.rise.R
import com.example.rise.transport.router.PresenceStatus

fun PresenceStatus.toDisplayText(context: Context): String = when (this) {
    PresenceStatus.ONLINE -> context.getString(R.string.presence_online)
    PresenceStatus.OFFLINE -> context.getString(R.string.presence_offline)
    PresenceStatus.UNKNOWN -> context.getString(R.string.presence_unknown)
}

@ColorInt
fun PresenceStatus.toDisplayColor(context: Context): Int {
    val colorRes = when (this) {
        PresenceStatus.ONLINE -> R.color.presenceOnline
        PresenceStatus.OFFLINE -> R.color.presenceOffline
        PresenceStatus.UNKNOWN -> R.color.presenceUnknown
    }
    return ContextCompat.getColor(context, colorRes)
}
