package com.example.rise.data.auth

import com.example.rise.briar.runtime.BriarRuntimeManager
import com.example.rise.transport.router.CanonicalIdentity
import com.example.rise.transport.router.TransportRouter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class RuntimeBriarAccountRepository(
    private val runtimeManager: BriarRuntimeManager,
    private val transportRouter: TransportRouter,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BriarAccountRepository {

    override suspend fun createAccount(name: String, password: String) = withContext(ioDispatcher) {
        val created = runCatching { runtimeManager.createAccount(name, password) }
            .getOrElse { error ->
                if (error is AssertionError && error.message?.contains("database key", ignoreCase = true) == true) {
                    throw IllegalStateException("Account already exists for this nickname. Please sign in.")
                }
                throw error
            }
        if (!created) {
            throw IllegalStateException("Failed to create Briar account")
        }
        ensureIdentity()
        Unit
    }

    override suspend fun signIn(name: String, password: String) = withContext(ioDispatcher) {
        Timber.tag(TAG).i("Briar sign-in requested for %s", name)
        val signedIn = runtimeManager.signIn(password)
        if (!signedIn) {
            throw IllegalStateException("Failed to sign in to Briar account")
        }
        Timber.tag(TAG).i("Briar sign-in succeeded via existing account")
        val identity = ensureIdentity()
        Timber.tag(TAG).i("Briar sign-in resolved identity=%s", identity.id)
        if (identity.displayName != name) {
            throw IllegalStateException("Nickname does not match existing account")
        }
    }

    private suspend fun ensureIdentity(): CanonicalIdentity {
        // Make sure the runtime is at least started so the router can resolve identity.
        runtimeManager.ensureStarted()
        val result = runCatching { transportRouter.ensureCurrentIdentity() }
        return result.getOrElse { error ->
            Timber.tag(TAG).w(error, "Identity resolution failed")
            val message = error.message.orEmpty()
            throw IllegalStateException(message.ifBlank { "Failed to load Briar identity" })
        }.also { identity ->
            Timber.tag(TAG).i("ensureIdentity resolved %s (%s)", identity.id, identity.displayName)
        }
    }

    companion object {
        private const val TAG = "RuntimeBriarAccountRepo"
    }
}
