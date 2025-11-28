package com.example.rise.briar.runtime

/**
 * Diagnostic events emitted by [BriarRuntimeManager]. Instrumentation tests can record
 * these to verify end-to-end initialisation without depending on logcat parsing.
 */
sealed interface BriarRuntimeEvent {

    data class StatusChanged(val status: BriarRuntimeStatus) : BriarRuntimeEvent
    data class Message(val value: String) : BriarRuntimeEvent
    data class IdentityStatus(val exists: Boolean) : BriarRuntimeEvent
}
