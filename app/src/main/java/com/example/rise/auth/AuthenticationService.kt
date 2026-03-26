package com.example.rise.auth

import android.net.Uri
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Abstraction over authentication interactions required by the app.
 * This allows the production code to remain unaware of the concrete backend (Firebase)
 * while unit tests can provide lightweight fakes without relying on inline mocking.
 */
interface AuthenticationService {

    data class User(
        val id: String,
        val displayName: String?,
        val email: String?,
        val photoUrl: Uri?
    )

    /**
     * Returns the currently authenticated user, or null when the user is signed out.
     */
    fun currentUser(): User?

    /**
     * Registers a listener that will be invoked whenever authentication state changes.
     * The returned handle should be used to unregister the listener when it is no longer needed.
     */
    fun addAuthStateListener(listener: (User?) -> Unit): AuthStateHandle

    /**
     * Signs the user in with email/password credentials.
     */
    suspend fun signInWithEmail(email: String, password: String)

    /**
     * Creates a new account with the provided email/password credentials.
     */
    suspend fun createUserWithEmail(email: String, password: String)

    /**
     * Signs the user in with the provided custom token (used for Telegram sign-in).
     */
    suspend fun signInWithCustomToken(customToken: String)

    /**
     * Updates the profile information for the current user if one exists.
     */
    suspend fun updateProfile(displayName: String?, photoUrl: Uri?)

    /**
     * Signs the current user out.
     */
    fun signOut()

    /**
     * Convenience flow exposing auth-state updates. Implementations may provide a more efficient
     * mechanism internally; the default simply bridges [addAuthStateListener].
     */
    fun authState(): Flow<User?> = callbackFlow {
        val handle = addAuthStateListener { trySend(it) }
        trySend(currentUser())
        awaitClose { handle.unregister() }
    }
}

class AuthStateHandle internal constructor(private val dispose: () -> Unit) {
    fun unregister() = dispose()
}
