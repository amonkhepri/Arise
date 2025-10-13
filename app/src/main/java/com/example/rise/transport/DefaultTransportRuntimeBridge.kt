package com.example.rise.transport

import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.featureflags.TransportModeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Stage 0 transport bridge – observes the feature flag and blocks non-Firestore modes
 * until the hybrid Briar stack is ready.
 */
class DefaultTransportRuntimeBridge(
    private val transportModeProvider: TransportModeProvider,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : TransportRuntimeBridge {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + dispatcher)
    private val _currentMode = MutableStateFlow(BriarTransportMode.FIRESTORE)

    init {
        scope.launch {
            transportModeProvider.observeMode().collect { mode ->
                if (_currentMode.value != mode) {
                    Timber.tag(TAG).i("Transport mode changed: %s", mode)
                }
                _currentMode.value = mode
            }
        }
    }

    override val currentMode: StateFlow<BriarTransportMode> = _currentMode.asStateFlow()

    override fun requireFirestore(caller: String) {
        val mode = _currentMode.value
        if (mode != BriarTransportMode.FIRESTORE) {
            Timber.tag(TAG).w(
                "Ignoring %s while transport mode is %s; Firestore path is the only supported option in Stage 0.",
                caller,
                mode
            )
        }
    }

    companion object {
        private const val TAG = "TransportBridge"
    }
}
