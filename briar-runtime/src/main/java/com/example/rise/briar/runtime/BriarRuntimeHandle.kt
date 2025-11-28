package com.example.rise.briar.runtime

import org.briarproject.bramble.api.account.AccountManager

/**
 * Wrapper returned by [BriarComponentFactory] so the runtime manager can surface only
 * the bridge interfaces we intend to wire into Koin.
 */
interface BriarRuntimeHandle : AutoCloseable {
    val chatGateway: BriarChatGateway
    val contactService: BriarContactService
    val hasIdentity: Boolean
    val accountManager: AccountManager

    fun markIdentityReady()
    fun signIn(password: String)
    override fun close()
}
