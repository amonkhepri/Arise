package com.example.rise.briar.runtime

/**
 * Abstracts instantiation of the Briar dependency graph so tests can provide lightweight
 * fakes while production builds create the full Dagger component.
 */
interface BriarComponentFactory {
    fun create(config: BriarRuntimeConfig): BriarRuntimeHandle
}
