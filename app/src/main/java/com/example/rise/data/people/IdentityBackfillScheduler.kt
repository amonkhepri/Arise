package com.example.rise.data.people

import android.content.Context
import com.example.rise.auth.AuthenticationService
import com.example.rise.featureflags.BriarTransportMode
import timber.log.Timber

fun interface IdentityBackfillEnqueuer {
    fun enqueue(context: Context, force: Boolean)
}

class IdentityBackfillScheduler(
    private val applicationContext: Context,
    private val statusTracker: IdentityBackfillStatusTracker,
    private val authenticationService: AuthenticationService,
    private val enqueuer: IdentityBackfillEnqueuer = IdentityBackfillEnqueuer { context, force ->
        IdentityBackfillWorker.enqueue(context, force)
    },
) {

    suspend fun scheduleIfNeeded(mode: BriarTransportMode) {
        if (mode == BriarTransportMode.FIRESTORE) return
        val currentUser = authenticationService.currentUser()
        if (currentUser != null && statusTracker.isComplete(currentUser.id)) {
            Timber.tag(TAG).i("Backfill already completed for %s; skipping", currentUser.id)
            return
        }
        Timber.tag(TAG).i(
            "Scheduling identity backfill (user=%s)",
            currentUser?.id ?: "pending-auth"
        )
        enqueuer.enqueue(applicationContext, force = false)
    }

    fun forceSchedule() {
        enqueuer.enqueue(applicationContext, force = true)
    }

    companion object {
        private const val TAG = "IdentityBackfillScheduler"
    }
}
