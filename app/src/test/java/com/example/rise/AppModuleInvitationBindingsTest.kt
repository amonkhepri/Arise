package com.example.rise

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.rise.transport.briar.BriarContactRepository
import com.example.rise.transport.briar.invite.BriarInvitationAcceptanceUseCase
import com.example.rise.transport.router.IdentityRegistry
import com.example.rise.transport.router.TransportRouter
import com.example.rise.ui.dashboardNavigation.people.peopleFragment.BriarManualInvitationCoordinator
import com.example.rise.ui.mainActivity.BriarInvitationDeepLinkEntrypoint
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppModuleInvitationBindingsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `app module resolves invitation deep link entrypoint for splash onboarding`() {
        val koinApp = koinApplication {
            androidContext(context)
            modules(App().appModule)
        }

        try {
            val entrypoint = koinApp.koin.get<BriarInvitationDeepLinkEntrypoint>()
            assertNotNull(entrypoint)
        } finally {
            koinApp.close()
        }
    }

    @Test
    fun `app module resolves invitation acceptance flow for people screen`() {
        val overrides = module {
            single<BriarContactRepository> { mockk(relaxed = true) }
            single<IdentityRegistry> { mockk(relaxed = true) }
            single<TransportRouter> { mockk(relaxed = true) }
        }
        val koinApp = koinApplication {
            androidContext(context)
            modules(App().appModule, overrides)
        }

        try {
            val useCase = koinApp.koin.get<BriarInvitationAcceptanceUseCase>()
            val coordinator = koinApp.koin.get<BriarManualInvitationCoordinator>()
            assertNotNull(useCase)
            assertNotNull(coordinator)
        } finally {
            koinApp.close()
        }
    }
}
