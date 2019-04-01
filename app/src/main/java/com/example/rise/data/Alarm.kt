package com.example.rise.data

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Alarm(var time: String = "test")
