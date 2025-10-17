package com.example.rise.briar.runtime

/**
 * Stage 1 placeholder interface for the chat-facing bridge. The contract will grow in
 * Stage 2 once dual-stack messaging is wired in.
 */
interface BriarChatGateway {
    val isAvailable: Boolean
}
