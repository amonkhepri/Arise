package com.example.rise.briar.runtime

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.rise.transport.TransportRuntimeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Debug-only helper that subscribes to Briar runtime diagnostics and forwards them to Timber
 * so we can confirm when the runtime starts/stops and whether an identity exists.
 */
class BriarDiagnosticsLogger(
    private val transportRuntimeBridge: TransportRuntimeBridge,
    private val scope: CoroutineScope = CoroutineScope(Job() + Dispatchers.IO),
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            transportRuntimeBridge.diagnostics.collectLatest { event ->
                when (event) {
                    is BriarRuntimeEvent.Message ->
                        Timber.tag(TAG).i(event.value)
                    is BriarRuntimeEvent.StatusChanged ->
                        Timber.tag(TAG).i(
                            "Status -> phase=%s err=%s",
                            event.status.phase,
                            event.status.lastError?.message
                        )
                    is BriarRuntimeEvent.IdentityStatus ->
                        Timber.tag(TAG).i("Identity exists: %s", event.exists)
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        scope.coroutineContext[Job]?.cancel()
    }

    companion object {
        private const val TAG = "BriarDiagnostics"
    }
}
