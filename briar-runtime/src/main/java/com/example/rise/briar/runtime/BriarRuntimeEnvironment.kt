package com.example.rise.briar.runtime

/**
 * Factory responsible for building a fresh [BriarRuntimeConfig] whenever the runtime
 * needs to transition from stopped to running. This indirection makes it easy for
 * instrumentation tests to provide temporary sandboxes.
 */
fun interface BriarRuntimeEnvironment {
    fun createConfig(): BriarRuntimeConfig
}
