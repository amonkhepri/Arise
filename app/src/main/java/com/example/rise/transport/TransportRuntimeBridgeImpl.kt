package com.example.rise.transport

import com.example.rise.briar.runtime.BriarChatGateway
import com.example.rise.briar.runtime.BriarContactService
import com.example.rise.briar.runtime.BriarRuntimeEvent
import com.example.rise.briar.runtime.BriarRuntimeManager
import com.example.rise.briar.runtime.BriarRuntimeStatus
import com.example.rise.data.people.IdentityBackfillScheduler
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.featureflags.TransportModeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Stage 1 transport bridge – still blocks Firestore callers when the runtime flag changes,
 * but now coordinates with [BriarRuntimeManager] so the hybrid stack is ready as soon as
 * HYBRID or BRIAR_ONLY modes become active.
 */
class TransportRuntimeBridgeImpl(
    private val transportModeProvider: TransportModeProvider,
    private val runtimeManager: BriarRuntimeManager,
    private val identityBackfillScheduler: IdentityBackfillScheduler,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : TransportRuntimeBridge {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + dispatcher)
    private val _currentMode = MutableStateFlow(BriarTransportMode.BRIAR_ONLY)

    init {
        scope.launch {
            transportModeProvider.observeMode().collect { mode ->
                if (_currentMode.value != mode) {
                    Timber.tag(TAG).i("Transport mode changed: %s", mode)
                }
                when (mode) {
                    BriarTransportMode.FIRESTORE -> {
                        Timber.tag(TAG).i("Stopping Briar runtime (mode=%s)", mode)
                        runCatching { runtimeManager.stop() }
                            .onFailure { Timber.tag(TAG).e(it, "Failed to stop Briar runtime") }
                    }

                    BriarTransportMode.HYBRID,
                    BriarTransportMode.BRIAR_ONLY -> {
                        Timber.tag(TAG).i("Starting Briar runtime (mode=%s)", mode)
                        runCatching { runtimeManager.ensureStarted() }
                            .onFailure {
                                Timber.tag(TAG).e(
                                    it,
                                    "Failed to start Briar runtime for mode %s",
                                    mode
                                )
                            }
                    }
                }
                if (mode == BriarTransportMode.HYBRID || mode == BriarTransportMode.BRIAR_ONLY) {
                    runCatching { identityBackfillScheduler.scheduleIfNeeded(mode) }
                        .onFailure { Timber.tag(TAG).e(it, "Failed to schedule identity backfill") }
                }
                _currentMode.value = mode
            }
        }
        scope.launch {
            runtimeManager.status.collect { status ->
                Timber.tag(TAG).d(
                    "Briar runtime status -> phase=%s err=%s",
                    status.phase,
                    status.lastError?.message
                )
            }
        }
    }

    override val currentMode: StateFlow<BriarTransportMode> = _currentMode.asStateFlow()
    override val runtimeStatus: StateFlow<BriarRuntimeStatus> = runtimeManager.status
    override val diagnostics: SharedFlow<BriarRuntimeEvent> = runtimeManager.diagnostics
    override val briarChatGateway: StateFlow<BriarChatGateway> = runtimeManager.chatGateway
    override val briarContactService: StateFlow<BriarContactService> = runtimeManager.contactService

    override fun requireFirestore(caller: String) {
        val mode = _currentMode.value
        if (mode != BriarTransportMode.FIRESTORE) {
            throw IllegalStateException("Firestore path disabled in mode $mode (called from $caller)")
        }
    }

    companion object {
        private const val TAG = "TransportBridge"
    }
}
