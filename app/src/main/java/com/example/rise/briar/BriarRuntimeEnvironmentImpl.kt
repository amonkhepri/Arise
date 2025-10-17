package com.example.rise.briar

import android.content.Context
import com.example.rise.briar.runtime.BriarRuntimeConfig
import com.example.rise.briar.runtime.BriarRuntimeEnvironment

/**
 * Resolves a private storage area for the embedded Briar runtime. Using `getDir`
 * keeps the data sandboxed under the app's internal storage so instrumentation can
 * wipe it between scenarios.
 */
class BriarRuntimeEnvironmentImpl(
    private val context: Context
) : BriarRuntimeEnvironment {

    override fun createConfig(): BriarRuntimeConfig {
        val storage = context.getDir(BRIAR_STORAGE_SUBDIR, Context.MODE_PRIVATE)
        return BriarRuntimeConfig(storageDir = storage)
    }

    companion object {
        private const val BRIAR_STORAGE_SUBDIR = "briar"
    }
}
