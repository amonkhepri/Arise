package com.example.rise.auth

import android.content.Intent

fun interface SignInIntentProvider {
    fun createSignInIntent(): Intent
}
