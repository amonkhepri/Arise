@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.example.rise.briar.runtime

import java.io.File
import org.briarproject.bramble.BrambleCoreEagerSingletons
import org.briarproject.bramble.BrambleJavaEagerSingletons
import org.briarproject.briar.BriarCoreEagerSingletons
import org.briarproject.briar.headless.BriarHeadlessApp
import org.briarproject.briar.headless.DaggerBriarHeadlessApp
import org.briarproject.briar.headless.HeadlessEagerSingletons
import org.briarproject.briar.headless.HeadlessModule

/**
 * Production factory that wires the upstream Briar Dagger component packaged inside
 * the `briar-headless` composite build.
 */
class BriarComponentFactoryImpl : BriarComponentFactory {

    override fun create(config: BriarRuntimeConfig): BriarRuntimeHandle {
        ensureStorageDirectory(config.storageDir)
        val component = DaggerBriarHeadlessApp.builder()
            .headlessModule(HeadlessModule(config.storageDir))
            .build()

        initialiseEagerSingletons(component)

        return BriarRuntimeHandleImpl(component)
    }

    private fun ensureStorageDirectory(dir: File) {
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }

    private fun initialiseEagerSingletons(component: BriarHeadlessApp) {
        BrambleCoreEagerSingletons.Helper.injectEagerSingletons(component)
        BrambleJavaEagerSingletons.Helper.injectEagerSingletons(component)
        BriarCoreEagerSingletons.Helper.injectEagerSingletons(component)
        HeadlessEagerSingletons.Helper.injectEagerSingletons(component)
    }

    private class BriarRuntimeHandleImpl(
        private val _component: BriarHeadlessApp
    ) : BriarRuntimeHandle {

        override val chatGateway: BriarChatGateway = NoOpBriarChatGateway.also {
            it.markReady()
        }

        override val contactService: BriarContactService = NoOpBriarContactService.also {
            it.markReady()
        }

        override fun close() {
            NoOpBriarChatGateway.reset()
            NoOpBriarContactService.reset()
            // Stage 1: no additional lifecycle interactions are needed yet.
        }
    }
}
