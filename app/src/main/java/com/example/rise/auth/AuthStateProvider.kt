package com.example.rise.auth

fun interface AuthStateProvider {
    fun isUserSignedIn(): Boolean
}
