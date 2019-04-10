package com.example.rise.extensions

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import com.example.models.RadioItem
import com.example.rise.R
import com.example.rise.helpers.MINUTE_SECONDS
import java.util.*

fun Activity.showOverLockscreen() {
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
}


fun Activity.hideKeyboard() {
    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow((currentFocus ?: View(this)).windowToken, 0)
    window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    currentFocus?.clearFocus()
}

fun Activity.showPickSecondsDialog(curSeconds: Int, isSnoozePicker: Boolean = false, showSecondsAtCustomDialog: Boolean = false,
                                   cancelCallback: (() -> Unit)? = null, callback: (seconds: Int) -> Unit) {
    hideKeyboard()
    val seconds = TreeSet<Int>()
    seconds.apply {
        if (!isSnoozePicker) {
            add(-1)
            add(0)
        }
        add(1 * MINUTE_SECONDS)
        add(5 * MINUTE_SECONDS)
        add(10 * MINUTE_SECONDS)
        add(30 * MINUTE_SECONDS)
        add(60 * MINUTE_SECONDS)
        add(curSeconds)
    }

    val items = ArrayList<RadioItem>(seconds.size + 1)
    seconds.mapIndexedTo(items) { index, value ->
        RadioItem(index, getFormattedSeconds(value, !isSnoozePicker), value)
    }

    var selectedIndex = 0
    seconds.forEachIndexed { index, value ->
        if (value == curSeconds) {
            selectedIndex = index
        }
    }

    items.add(RadioItem(-2, getString(R.string.custom)))

    RadioGroupDialog(this, items, selectedIndex, showOKButton = isSnoozePicker, cancelCallback = cancelCallback) {
        if (it == -2) {
            CustomIntervalPickerDialog(this, showSeconds = showSecondsAtCustomDialog) {
                callback(it)
            }
        } else {
            callback(it as Int)
        }
    }
}

