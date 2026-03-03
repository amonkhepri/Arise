package com.example.rise.data.myaccount

import com.example.rise.auth.AuthenticationService
import com.example.rise.data.people.PeopleSync
import com.example.rise.models.User
import com.example.rise.briar.runtime.BriarRuntimeManager
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.router.AccountConnector
import com.example.rise.transport.router.ConnectorRegistry
import com.example.rise.transport.router.IdentityRegistry
import com.example.rise.transport.router.TransportRouter
import com.example.rise.transport.router.IdentityProfile
import com.example.rise.transport.router.TransportId
import com.example.rise.featureflags.BriarTransportMode
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RouterMyAccountRepository(
    private val authService: AuthenticationService,
    private val peopleSync: PeopleSync,
    private val transportRouter: TransportRouter,
    private val connectorRegistry: ConnectorRegistry,
    private val transportBridge: TransportRuntimeBridge,
    private val identityRegistry: IdentityRegistry,
    private val briarRuntimeManager: BriarRuntimeManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MyAccountRepository {

    override suspend fun fetchCurrentUser(): User = withContext(ioDispatcher) {
        if (transportBridge.currentMode.value == BriarTransportMode.BRIAR_ONLY) {
            val identity = transportRouter.ensureCurrentIdentity()
            val record = identityRegistry.identitiesSnapshot()
                .firstOrNull { it.canonicalIdentity.id == identity.id }
            val profile = record?.profile ?: IdentityProfile()
            return@withContext User(
                name = identity.displayName,
                bio = profile.bio.orEmpty(),
                profilePicturePath = profile.profilePicturePath,
                registrationTokens = mutableListOf(),
            )
        }
        accountConnector().fetchAccountProfile()
    }

    override suspend fun updateCurrentUser(
        name: String,
        bio: String,
        profilePicturePath: String?,
    ) {
        withContext(ioDispatcher) {
            if (transportBridge.currentMode.value == BriarTransportMode.BRIAR_ONLY) {
                if (name.isBlank() && bio.isBlank() && profilePicturePath.isNullOrBlank()) {
                    return@withContext
                }
                val identity = transportRouter.ensureCurrentIdentity()
                val record = identityRegistry.identitiesSnapshot()
                    .firstOrNull { it.canonicalIdentity.id == identity.id }
                val aliases = record?.aliases?.toMutableMap() ?: mutableMapOf()
                if (!aliases.containsKey(TransportId.BRIAR)) {
                    aliases[TransportId.BRIAR] = identity.id
                }
                val existingProfile = record?.profile ?: IdentityProfile()
                val updatedIdentity = if (name.isNotBlank()) {
                    identity.copy(displayName = name)
                } else identity
                val updatedProfile = existingProfile.copy(
                    bio = bio.takeIf { it.isNotBlank() } ?: existingProfile.bio,
                    profilePicturePath = profilePicturePath?.takeIf { it.isNotBlank() }
                        ?: existingProfile.profilePicturePath,
                    presence = existingProfile.presence,
                )
                identityRegistry.upsertIdentity(
                    identity = updatedIdentity,
                    aliases = aliases,
                    profile = updatedProfile,
                    setAsCurrent = true,
                )
                return@withContext
            }
            val update = AccountConnector.AccountProfileUpdate(
                name = name.takeIf { it.isNotBlank() },
                bio = bio.takeIf { it.isNotBlank() },
                profilePicturePath = profilePicturePath?.takeIf { it.isNotBlank() },
            )
            if (update.isEmpty()) return@withContext
            accountConnector().updateAccountProfile(update)
        }
    }

    override suspend fun signOut() {
        withContext(ioDispatcher) {
            Timber.tag(TAG).i("Sign-out requested (mode=%s)", transportBridge.currentMode.value)
            peopleSync.stop()
            transportRouter.reset()
            if (transportBridge.currentMode.value == BriarTransportMode.BRIAR_ONLY) {
                Timber.tag(TAG).i("Clearing identity registry for Briar-only sign-out")
                identityRegistry.clear()
            }
            val stopped = withTimeoutOrNull(5_000) {
                runCatching { briarRuntimeManager.stop() }
                    .onFailure { Timber.tag(TAG).w(it, "Failed to stop Briar runtime during sign-out") }
                true
            } ?: false
            if (!stopped) {
                Timber.tag(TAG).w("Timed out stopping Briar runtime during sign-out")
            }
            authService.signOut()
            Timber.tag(TAG).i("Sign-out completed")
        }
    }

    private fun accountConnector(): AccountConnector {
        val mode = transportBridge.currentMode.value
        val primaryConnector = connectorRegistry.primaryFor(mode)
        val primaryAccountConnector = primaryConnector as? AccountConnector

        val accountConnectors = connectorRegistry.connectors.mapNotNull { candidate ->
            val account = candidate as? AccountConnector ?: return@mapNotNull null
            candidate to account
        }
        val preferred = when (mode) {
            BriarTransportMode.FIRESTORE -> {
                if (primaryConnector.transport == TransportId.FIRESTORE && primaryAccountConnector != null) {
                    primaryAccountConnector
                } else {
                    accountConnectors.firstOrNull { it.first.transport == TransportId.FIRESTORE }?.second
                }
            }
            BriarTransportMode.HYBRID,
            BriarTransportMode.BRIAR_ONLY -> {
                if (primaryConnector.transport != TransportId.FIRESTORE && primaryAccountConnector != null) {
                    primaryAccountConnector
                } else {
                    accountConnectors.firstOrNull { it.first.transport != TransportId.FIRESTORE }?.second
                }
            }
        }
        return preferred
            ?: primaryAccountConnector
            ?: accountConnectors.firstOrNull { it.first.transport == TransportId.FIRESTORE }?.second
            ?: accountConnectors.firstOrNull()?.second
            ?: throw IllegalStateException(
                "No connector supports account profiles for mode=$mode primary=${primaryConnector.transport}"
            )
    }

    companion object {
        private const val TAG = "RouterMyAccountRepo"
    }
}
