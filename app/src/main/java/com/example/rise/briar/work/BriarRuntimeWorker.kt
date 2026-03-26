package com.example.rise.briar.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.rise.briar.runtime.BriarRuntimeManager
import com.example.rise.briar.runtime.BriarRuntimePhase
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.transport.TransportRuntimeBridge
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.koin.java.KoinJavaComponent.getKoin
import timber.log.Timber

/**
 * Diagnostic worker that spins up the embedded Briar runtime and optionally tears it down again.
 * Instrumentation can enqueue this worker to validate start/stop sequencing without touching
 * production repositories.
 */
class BriarRuntimeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val runtimeManager: BriarRuntimeManager = getKoin().get()
    private val transportBridge: TransportRuntimeBridge = getKoin().get()

    override suspend fun doWork(): Result {
        val stopOnCompletion = inputData.getBoolean(KEY_STOP_ON_COMPLETION, true)
        val timeout = inputData.getLong(KEY_START_TIMEOUT_MS, DEFAULT_START_TIMEOUT_MS).coerceAtLeast(0L)

        val mode = transportBridge.currentMode.value
        if (mode == BriarTransportMode.FIRESTORE) {
            Timber.tag(TAG).i("Skipping diagnostic worker because mode=%s", mode)
            return Result.success()
        }

        val statusResult = runCatching {
            Timber.tag(TAG).i("Diagnostic worker starting Briar runtime (timeout=%dms)", timeout)
            runtimeManager.ensureStarted()
            if (timeout == 0L) {
                runtimeManager.status.value
            } else {
                withTimeout(timeout) {
                    runtimeManager.status.first { it.phase != BriarRuntimePhase.STARTING }
                }
            }
        }

        val status = statusResult.getOrElse { throwable ->
            Timber.tag(TAG).e(throwable, "Failed to initialise Briar runtime from diagnostic worker")
            if (stopOnCompletion) {
                runtimeManager.stop()
            }
            return if (throwable is TimeoutCancellationException) {
                Result.retry()
            } else {
                Result.failure()
            }
        }

        val result = when (status.phase) {
            BriarRuntimePhase.RUNNING -> {
                Timber.tag(TAG).i("Briar runtime is RUNNING")
                Result.success()
            }

            BriarRuntimePhase.FAILED -> {
                Timber.tag(TAG).w("Briar runtime reported FAILED state: %s", status.lastError?.message)
                Result.failure()
            }

            else -> {
                Timber.tag(TAG).w("Briar runtime finished in %s state; retrying", status.phase)
                Result.retry()
            }
        }

        if (stopOnCompletion) {
            Timber.tag(TAG).i("Stopping Briar runtime after diagnostics")
            runtimeManager.stop()
        }

        return result
    }

    companion object {
        private const val TAG = "BriarRuntimeWorker"
        private const val KEY_STOP_ON_COMPLETION = "stop_on_completion"
        private const val KEY_START_TIMEOUT_MS = "start_timeout_ms"
        const val UNIQUE_WORK_NAME = "briar-runtime-diagnostic"
        const val DEFAULT_START_TIMEOUT_MS = 20_000L

        fun inputData(
            stopOnCompletion: Boolean = true,
            startTimeoutMs: Long = DEFAULT_START_TIMEOUT_MS
        ): Data = workDataOf(
            KEY_STOP_ON_COMPLETION to stopOnCompletion,
            KEY_START_TIMEOUT_MS to startTimeoutMs
        )

        fun enqueue(
            context: Context,
            stopOnCompletion: Boolean = true,
            startTimeoutMs: Long = DEFAULT_START_TIMEOUT_MS
        ) {
            val request = OneTimeWorkRequestBuilder<BriarRuntimeWorker>()
                .setInputData(inputData(stopOnCompletion, startTimeoutMs))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
