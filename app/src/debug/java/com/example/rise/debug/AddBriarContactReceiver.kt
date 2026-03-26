package com.example.rise.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.rise.transport.briar.BriarContactRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import timber.log.Timber

/**
 * Debug-only receiver to add a Briar contact by link via ADB:
 *
 * ```
 * adb shell am broadcast \
 *   -n com.example.rise/.debug.AddBriarContactReceiver \
 *   -a com.example.rise.debug.ADD_BRIAR_CONTACT \
 *   --es link "briar://aaaa..." \
 *   --es alias "Test Contact"
 * ```
 */
class AddBriarContactReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != ACTION_ADD_BRIAR_CONTACT) return

        val link = intent.getStringExtra(EXTRA_LINK)
        if (link.isNullOrBlank()) {
            Timber.tag(TAG).w("Ignoring add-contact broadcast: missing link")
            return
        }
        val alias = intent.getStringExtra(EXTRA_ALIAS)
        val pendingResult = goAsync()
        val repository: BriarContactRepository = GlobalContext.get().get()

        Timber.tag(TAG).i("Adding Briar contact from debug broadcast (alias=%s)", alias ?: "<none>")
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { repository.addContactByLink(link, alias) }
                .onFailure { Timber.tag(TAG).e(it, "Failed to add Briar contact from debug receiver") }
            pendingResult.finish()
        }
    }

    companion object {
        private const val TAG = "AddBriarContactReceiver"
        const val ACTION_ADD_BRIAR_CONTACT = "com.example.rise.debug.ADD_BRIAR_CONTACT"
        const val EXTRA_LINK = "link"
        const val EXTRA_ALIAS = "alias"
    }
}
