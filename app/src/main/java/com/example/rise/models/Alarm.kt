package com.example.rise.models

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties

data class Alarm(var id: Int=0, var timeInMinutes: Int=0, var days: Int=0, var isEnabled: Boolean=true, var vibrate: Boolean=true, var soundTitle: String="title",
                 var soundUri: String="", var label: String="", var userName: String ="", var chatChannel:String = "",var messsage:TextMessage?=null)