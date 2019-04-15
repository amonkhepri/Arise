package com.example.rise.helpers

import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import com.example.rise.extensions.getContrastColor

fun ImageView.setFillWithStroke(fillColor: Int, backgroundColor: Int) {
    val strokeColor = backgroundColor.getContrastColor()
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fillColor)
        setStroke(2, strokeColor)
        setBackgroundDrawable(this)
    }
}

fun ImageView.applyColorFilter(color: Int) = setColorFilter(color, PorterDuff.Mode.SRC_IN)
