package com.example.rise.data.auth

data class TelegramAuthResponse(
    val customToken: String,
    val displayName: String?,
    val photoUrl: String?
)
