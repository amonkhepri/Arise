package com.example.rise.data.people

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.rise.auth.AuthenticationService
import org.koin.java.KoinJavaComponent.getKoin
import timber.log.Timber

class IdentityBackfillWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val coordinator: IdentityBackfillCoordinator = getKoin().get()
    private val authService: AuthenticationService = getKoin().get()
    private val statusTracker: IdentityBackfillStatusTracker = getKoin().get()

    override suspend fun doWork(): Result {
        val force = inputData.getBoolean(KEY_FORCE, false)
        val currentUserId = authService.currentUser()?.id
        if (currentUserId == null) {
            Timber.tag(TAG).w("Identity backfill skipped: no authenticated user")
            return Result.retry()
        }
        if (!force && statusTracker.isComplete(currentUserId)) {
            Timber.tag(TAG).i("Identity backfill already completed; skipping")
            return Result.success()
        }
        return runCatching {
            coordinator.backfill(currentUserId)
            statusTracker.markComplete(currentUserId)
            Result.success()
        }.getOrElse { error ->
            Timber.tag(TAG).e(error, "Identity backfill failed")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "IdentityBackfillWorker"
        private const val UNIQUE_WORK_NAME = "identity-backfill"
        private const val KEY_FORCE = "force"

        fun enqueue(
            context: Context,
            force: Boolean,
        ) {
            val data: Data = workDataOf(KEY_FORCE to force)
            val request = OneTimeWorkRequestBuilder<IdentityBackfillWorker>()
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
