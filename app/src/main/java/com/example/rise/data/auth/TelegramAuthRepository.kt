package com.example.rise.data.auth

interface TelegramAuthRepository {
    suspend fun exchange(authData: TelegramAuthData): TelegramAuthResponse
}
