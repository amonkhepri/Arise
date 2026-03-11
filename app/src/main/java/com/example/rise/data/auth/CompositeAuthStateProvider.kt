package com.example.rise.data.auth

import com.example.rise.transport.router.IdentityRegistry

class CompositeAuthStateProvider(
    private val primary: AuthStateProvider,
    private val identityRegistry: IdentityRegistry,
) : AuthStateProvider {

    override fun isSignedIn(): Boolean {
        return primary.isSignedIn() || identityRegistry.currentIdentitySnapshot() != null
    }

    override fun currentUserId(): String? {
        return primary.currentUserId() ?: identityRegistry.currentIdentitySnapshot()?.id
    }

    override fun currentUserDisplayName(): String? {
        return primary.currentUserDisplayName() ?: identityRegistry.currentIdentitySnapshot()?.displayName
    }
}
