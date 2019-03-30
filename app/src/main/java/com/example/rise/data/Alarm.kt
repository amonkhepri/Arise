package com.example.rise.data

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Alarm(val name:String="", val timeRemaining:Int=0, val time:Int=0)
