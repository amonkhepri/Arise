package com.example.rise.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class TextMessage(
    val text: String,
    override val time: Date,
    override val senderId: String,
    override val recipientId: String,
    override val senderName: String,
    override val type: String = MessageType.TEXT
) : Message, Parcelable {
    constructor() : this("", Date(0), "", "", "")
}
