package com.example.rise.data.auth

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TelegramAuthData(
    val id: Long,
    val firstName: String,
    val lastName: String?,
    val username: String?,
    val photoUrl: String?,
    val authDate: Long,
    val hash: String
) : Parcelable {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "first_name" to firstName,
        "last_name" to lastName,
        "username" to username,
        "photo_url" to photoUrl,
        "auth_date" to authDate,
        "hash" to hash
    )
}
