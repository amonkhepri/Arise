package com.example.rise.briar.runtime

import java.io.File

/**
 * Immutable configuration bundle used by [BriarComponentFactory] when instantiating
 * the upstream Briar graph.
 */
data class BriarRuntimeConfig(
    val storageDir: File
)
