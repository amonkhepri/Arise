package com.example.rise.extensions

import android.widget.TextView
import com.example.rise.helpers.applyColorFilter

fun TextView.colorLeftDrawable(color: Int) {
    val leftImage = compoundDrawables.first()
    leftImage.applyColorFilter(color)
    setCompoundDrawables(leftImage, null, null, null)
}
