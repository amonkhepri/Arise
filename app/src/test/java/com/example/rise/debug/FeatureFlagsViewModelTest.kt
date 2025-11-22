package com.example.rise.debug

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.featureflags.DataStoreTransportModeProviderImpl
import com.example.rise.featureflags.TelegramAuthFlagProvider
import com.example.rise.transport.router.BridgeOrchestrator
import com.example.rise.transport.router.ConnectorHealth
import com.example.rise.transport.router.ConnectorHealthProvider
import com.example.rise.transport.router.ConnectorInboundMessage
import com.example.rise.transport.router.ConnectorLifecycleState
import com.example.rise.transport.router.PrimaryRoutingReason
import com.example.rise.transport.router.PrimaryRoutingSnapshot
import com.example.rise.transport.router.PrimarySelectionTrigger
import com.example.rise.transport.router.TransportId
import com.example.rise.transport.router.ConnectorCapabilities
import com.example.rise.transport.router.ConnectorStatus
import com.example.rise.util.MainDispatcherRule
import java.io.File
import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureFlagsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    @get:Rule val temporaryFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var transportProvider: DataStoreTransportModeProviderImpl
    private lateinit var telegramProvider: TelegramAuthFlagProvider
    private lateinit var healthProvider: FakeConnectorHealthProvider

    private fun drain() {
        // run queued work on both dispatcher contexts
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun createViewModel(): FeatureFlagsViewModel {
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        val file = File(temporaryFolder.newFolder(), "flags.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        transportProvider = DataStoreTransportModeProviderImpl(dataStore)
        telegramProvider = TelegramAuthFlagProvider(dataStore)
        healthProvider = FakeConnectorHealthProvider()
        return FeatureFlagsViewModel(
            transportModeProvider = transportProvider,
            telegramAuthFlagProvider = telegramProvider,
            bridgeOrchestrator = FakeBridgeOrchestrator(),
            connectorHealthProvider = healthProvider,
        )
    }

    @After
    fun tearDown() {
        if (this::scope.isInitialized) {
            scope.cancel()
        }
    }

    private class FakeBridgeOrchestrator : BridgeOrchestrator {
        private val snapshot = PrimaryRoutingSnapshot(
            mode = BriarTransportMode.FIRESTORE,
            primary = TransportId.FIRESTORE,
            preferred = TransportId.FIRESTORE,
            fallbackTarget = null,
            reason = PrimaryRoutingReason.Initial,
            preferredLifecycle = ConnectorLifecycleState.STOPPED,
            trigger = PrimarySelectionTrigger.INITIAL,
            timestampMs = 0L,
        )
        override val routingState: StateFlow<PrimaryRoutingSnapshot> = MutableStateFlow(snapshot)
        override suspend fun onMessagesReceived(
            conversationId: String,
            source: TransportId,
            messages: List<ConnectorInboundMessage>
        ) = Unit
    }

    @Test
    fun `initial state defaults to Firestore and disables Telegram`() = runTest {
        val viewModel = createViewModel()
        drain()

        val state = viewModel.state.value
        assertEquals(BriarTransportMode.FIRESTORE, state.mode)
        assertFalse(state.telegramAuthEnabled)
    }

    @Test
    fun `non Firestore selection emits warning and keeps Firestore`() = runTest {
        val viewModel = createViewModel()
        drain()

        viewModel.events.test {
            viewModel.setMode(BriarTransportMode.HYBRID)
            drain()
            val event = awaitItem() as FeatureFlagsViewModel.Event.ShowMessageRes
            assertEquals(com.example.rise.R.string.feature_flags_transport_mode_stage0_warning, event.messageRes)
            cancelAndIgnoreRemainingEvents()
        }

        val state = viewModel.state.value
        assertEquals(BriarTransportMode.FIRESTORE, state.mode)
    }

}

private class FakeConnectorHealthProvider : ConnectorHealthProvider {
    override val health: MutableStateFlow<Map<TransportId, ConnectorHealth>> =
        MutableStateFlow(
            mapOf(
                TransportId.FIRESTORE to ConnectorHealth(
                    transport = TransportId.FIRESTORE,
                    lifecycle = ConnectorLifecycleState.READY,
                    status = ConnectorStatus.ACTIVE,
                    capabilities = ConnectorCapabilities(emptyMap()),
                    messagingReady = true,
                )
            )
        )
}
