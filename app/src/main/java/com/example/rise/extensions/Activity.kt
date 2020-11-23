package com.example.rise.extensions

import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.example.rise.models.RadioItem
import com.example.rise.R
import com.example.rise.helpers.MINUTE_SECONDS
import com.example.rise.helpers.MyTextView
import com.example.rise.ui.dialogs.CustomIntervalPickerDialog
import com.example.rise.ui.dialogs.RadioGroupDialog
import kotlinx.android.synthetic.main.dialog_tile_textview.view.*
import java.util.*

fun AppCompatActivity.showOverLockscreen() {
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
}

fun AppCompatActivity.hideKeyboard() {
    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow((currentFocus ?: View(this)).windowToken, 0)
    window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    currentFocus?.clearFocus()
}

fun AppCompatActivity.showPickSecondsDialog(curSeconds: Int, isSnoozePicker: Boolean = false, showSecondsAtCustomDialog: Boolean = false,
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

fun AppCompatActivity.showKeyboard(et: EditText) {
    et.requestFocus()
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
}

fun AppCompatActivity.setupDialogStuff(view: View, dialog: AlertDialog, titleId: Int = 0, titleText: String = "", callback: (() -> Unit)? = null) {
    if (isDestroyed || isFinishing) {
        return
    }

    if (view is ViewGroup)
        updateTextColors(view)
    else if (view is MyTextView) {
        view.setColors(baseConfig.textColor, getAdjustedPrimaryColor(), baseConfig.backgroundColor)
    }

    var title: TextView? = null


    if (titleId != 0 || titleText.isNotEmpty()) {
        title = layoutInflater.inflate(R.layout.dialog_title, null) as TextView
        title.dialog_title_textview.apply {
            if (titleText.isNotEmpty()) {
                text = titleText
            } else {
                setText(titleId)
            }
            setTextColor(baseConfig.textColor)
        }
    }

    dialog.apply {
        setView(view)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCustomTitle(title)
        setCanceledOnTouchOutside(true)
        show()
        getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(baseConfig.textColor)
        getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(baseConfig.textColor)
        getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(baseConfig.textColor)
        window?.setBackgroundDrawable(ColorDrawable(baseConfig.backgroundColor))
    }
    callback?.invoke()
}