package com.example.rise.briar.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Debug-only broadcast receiver that allows QA & CI scripts to trigger the diagnostic worker via ADB:
 *
 * ```
 * adb shell am broadcast \
 *      -n com.example.rise/.briar.work.BriarRuntimeDiagnosticsReceiver \
 *      -a com.example.rise.debug.RUN_BRIAR_RUNTIME_DIAGNOSTIC \
 *      --ez stop_on_completion true \
 *      --el start_timeout_ms 20000
 * ```
 */
class BriarRuntimeDiagnosticsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != BriarRuntimeWorker.ACTION_RUN_DIAGNOSTIC) return

        val stop = intent.getBooleanExtra(EXTRA_STOP_ON_COMPLETION, true)
        val timeout = intent.getLongExtra(EXTRA_START_TIMEOUT_MS, BriarRuntimeWorker.DEFAULT_START_TIMEOUT_MS)

        Timber.tag(TAG).i("Received diagnostic broadcast (stop=%s, timeout=%d)", stop, timeout)
        BriarRuntimeWorker.enqueue(
            context.applicationContext,
            stopOnCompletion = stop,
            startTimeoutMs = timeout
        )
    }

    companion object {
        private const val TAG = "BriarRuntimeReceiver"
        const val EXTRA_STOP_ON_COMPLETION = "stop_on_completion"
        const val EXTRA_START_TIMEOUT_MS = "start_timeout_ms"
    }
}
