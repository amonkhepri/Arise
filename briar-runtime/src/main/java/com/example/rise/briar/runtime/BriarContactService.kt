package com.example.rise.briar.runtime

/**
 * Stage 1 placeholder interface representing a minimal contact bridge. Later stages will
 * expose flows of contacts and sync hooks.
 */
interface BriarContactService {
    val isAvailable: Boolean
}
