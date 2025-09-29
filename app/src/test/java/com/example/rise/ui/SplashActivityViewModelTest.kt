package com.example.rise.ui

import com.example.rise.auth.AuthStateProvider
import com.example.rise.ui.SplashActivityViewModel.Destination
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SplashActivityViewModelTest {

    private lateinit var viewModel: SplashActivityViewModel
    private lateinit var authStateProvider: FakeAuthStateProvider

    @Before
    fun setUp() {
        authStateProvider = FakeAuthStateProvider()
        viewModel = SplashActivityViewModel(authStateProvider)
    }

    @Test
    fun `returns sign in destination when user is not authenticated`() {
        authStateProvider.isSignedIn = false

        val destination = viewModel.resolveDestination()

        assertEquals(Destination.SIGN_IN, destination)
    }

    @Test
    fun `returns main destination when user is authenticated`() {
        authStateProvider.isSignedIn = true

        val destination = viewModel.resolveDestination()

        assertEquals(Destination.MAIN, destination)
    }

    private class FakeAuthStateProvider : AuthStateProvider {
        var isSignedIn: Boolean = false

        override fun isUserSignedIn(): Boolean = isSignedIn
    }
}
