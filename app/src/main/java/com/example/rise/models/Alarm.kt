package com.example.rise.models

import android.os.Parcelable
import com.google.firebase.firestore.IgnoreExtraProperties
import kotlinx.android.parcel.Parcelize

@IgnoreExtraProperties

@Parcelize
data class Alarm(var idTimeStamp: Int=0, var timeInMiliseconds: Long=0, var days: Int=0, var isEnabled: Boolean=true, var vibrate: Boolean=true, var soundTitle: String="title",
                 var soundUri: String="", var label: String="", var userName: String ="", var chatChannel:String = "", var messsage:TextMessage?=null):Parcelable