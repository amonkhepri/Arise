package com.example.rise.data.auth

import android.content.Intent

fun interface SignInIntentProvider {
    fun createSignInIntent(): Intent
}
