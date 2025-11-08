package com.example.rise.data.myaccount

import com.example.rise.auth.AuthenticationService
import com.example.rise.data.people.PeopleSync
import com.example.rise.models.User
import com.example.rise.transport.TransportRuntimeBridge
import com.example.rise.transport.router.AccountConnector
import com.example.rise.transport.router.ConnectorRegistry
import com.example.rise.transport.router.TransportRouter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RouterMyAccountRepository(
    private val authService: AuthenticationService,
    private val peopleSync: PeopleSync,
    private val transportRouter: TransportRouter,
    private val connectorRegistry: ConnectorRegistry,
    private val transportBridge: TransportRuntimeBridge,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MyAccountRepository {

    override suspend fun fetchCurrentUser(): User = withContext(ioDispatcher) {
        accountConnector().fetchAccountProfile()
    }

    override suspend fun updateCurrentUser(name: String, bio: String) {
        withContext(ioDispatcher) {
            val update = AccountConnector.AccountProfileUpdate(
                name = name.takeIf { it.isNotBlank() },
                bio = bio.takeIf { it.isNotBlank() },
            )
            if (update.isEmpty()) return@withContext
            accountConnector().updateAccountProfile(update)
        }
    }

    override suspend fun signOut() {
        withContext(ioDispatcher) {
            peopleSync.stop()
            transportRouter.reset()
            authService.signOut()
        }
    }

    private fun accountConnector(): AccountConnector {
        val mode = transportBridge.currentMode.value
        val connector = connectorRegistry.primaryFor(mode)
        return connector as? AccountConnector
            ?: throw IllegalStateException("Connector ${connector.transport} does not support account profiles")
    }
}
