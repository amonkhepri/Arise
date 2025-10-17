package com.example.rise.briar.runtime

/**
 * Wrapper returned by [BriarComponentFactory] so the runtime manager can surface only
 * the bridge interfaces we intend to wire into Koin.
 */
interface BriarRuntimeHandle : AutoCloseable {
    val chatGateway: BriarChatGateway
    val contactService: BriarContactService

    override fun close()
}
