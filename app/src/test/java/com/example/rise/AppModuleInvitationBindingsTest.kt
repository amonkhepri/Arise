package com.example.rise

import com.example.rise.ui.mainActivity.BriarInvitationDeepLinkEntrypoint
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.koin.dsl.koinApplication

class AppModuleInvitationBindingsTest {

    @Test
    fun `app module resolves invitation deep link entrypoint for splash onboarding`() {
        val koinApp = koinApplication {
            modules(App().appModule)
        }

        try {
            val entrypoint = koinApp.koin.get<BriarInvitationDeepLinkEntrypoint>()
            assertNotNull(entrypoint)
        } finally {
            koinApp.close()
        }
    }
}
