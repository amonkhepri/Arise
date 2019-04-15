package com.example.rise.extensions

import android.graphics.Color

fun Int.getContrastColor(): Int {
    val DARK_GREY = -13421773
    val y = (299 * Color.red(this) + 587 * Color.green(this) + 114 * Color.blue(this)) / 1000
    return if (y >= 149) DARK_GREY else Color.WHITE
}