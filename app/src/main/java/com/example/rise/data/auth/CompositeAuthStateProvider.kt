package com.example.rise.data.auth

import com.example.rise.briar.runtime.BriarRuntimeManager
import com.example.rise.transport.router.IdentityRegistry
import timber.log.Timber

class CompositeAuthStateProvider(
    private val primary: AuthStateProvider,
    private val identityRegistry: IdentityRegistry,
    private val briarRuntimeManager: BriarRuntimeManager,
) : AuthStateProvider {

    override fun isSignedIn(): Boolean {
        val primarySignedIn = primary.isSignedIn()
        val cachedIdentityPresent = identityRegistry.currentIdentitySnapshot() != null
        val runtimeStatus = briarRuntimeManager.status.value
        val briarSessionSignedIn = runtimeStatus.hasDatabaseKey && cachedIdentityPresent
        Timber.tag(TAG).i(
            "isSignedIn primary=%s hasDatabaseKey=%s hasIdentity=%s cachedIdentity=%s",
            primarySignedIn,
            runtimeStatus.hasDatabaseKey,
            runtimeStatus.hasIdentity,
            cachedIdentityPresent,
        )
        return primarySignedIn || briarSessionSignedIn
    }

    override fun currentUserId(): String? {
        return primary.currentUserId() ?: identityRegistry.currentIdentitySnapshot()?.id
    }

    override fun currentUserDisplayName(): String? {
        return primary.currentUserDisplayName() ?: identityRegistry.currentIdentitySnapshot()?.displayName
    }

    private companion object {
        private const val TAG = "CompositeAuthState"
    }
}
