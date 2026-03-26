package com.example.rise.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.rise.data.people.IdentityBackfillWorker
import timber.log.Timber

class IdentityBackfillTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.tag(TAG).i("Debug trigger received; forcing identity backfill")
        IdentityBackfillWorker.enqueue(context, force = true)
    }

    companion object {
        private const val TAG = "IdentityBackfillTrigger"
    }
}
