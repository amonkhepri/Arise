package com.example.rise.data.auth

import android.content.Intent

interface SignInIntentProvider {
    fun createSignInIntent(): Intent
}
