package com.example.rise.ui.mainActivity

import android.content.Intent
import com.example.rise.auth.AuthStateProvider
import com.example.rise.auth.SignInIntentProvider
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class MainActivityViewModelTest {

    private lateinit var viewModel: MainActivityViewModel
    private lateinit var authStateProvider: FakeAuthStateProvider
    private lateinit var signInIntentProvider: FakeSignInIntentProvider

    @Before
    fun setUp() {
        authStateProvider = FakeAuthStateProvider()
        signInIntentProvider = FakeSignInIntentProvider()
        viewModel = MainActivityViewModel(authStateProvider, signInIntentProvider)
    }

    @Test
    fun `requests sign in intent when user is not signed in and no flow in progress`() {
        authStateProvider.isSignedIn = false

        val intent = viewModel.requestSignInIfNeeded()

        assertSame(signInIntentProvider.intent, intent)
    }

    @Test
    fun `does not request sign in twice while already in progress`() {
        authStateProvider.isSignedIn = false

        viewModel.requestSignInIfNeeded()

        val intent = viewModel.requestSignInIfNeeded()

        assertNull(intent)
    }

    @Test
    fun `requests retry intent when sign in fails and user still signed out`() {
        authStateProvider.isSignedIn = false

        viewModel.requestSignInIfNeeded()

        val intent = viewModel.requestRetrySignIn(isResultSuccessful = false)

        assertSame(signInIntentProvider.intent, intent)
    }

    @Test
    fun `does not request retry when sign in succeeds`() {
        authStateProvider.isSignedIn = true

        viewModel.requestSignInIfNeeded()

        val intent = viewModel.requestRetrySignIn(isResultSuccessful = true)

        assertNull(intent)
    }

    private class FakeAuthStateProvider : AuthStateProvider {
        var isSignedIn: Boolean = false

        override fun isUserSignedIn(): Boolean = isSignedIn
    }

    private class FakeSignInIntentProvider : SignInIntentProvider {
        val intent: Intent = Intent("test")

        override fun createSignInIntent(): Intent = intent
    }
}
