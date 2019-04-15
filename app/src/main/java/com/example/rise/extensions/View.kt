package com.example.rise.extensions

import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewTreeObserver

fun View.beInvisible() {
    visibility = View.INVISIBLE
}

fun View.beGone() {
    visibility = View.GONE
}

fun View.performHapticFeedback() = performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

fun View.onGlobalLayout(callback: () -> Unit) {
    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            viewTreeObserver.removeOnGlobalLayoutListener(this)
            callback()
        }
    })
}