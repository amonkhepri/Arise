package com.example.rise.ui.alarm.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.os.BundleCompat
import com.example.rise.data.chat.ChatRepository
import com.example.rise.helpers.MESSAGE_CONTENT
import com.example.rise.ui.alarm.models.Alarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

class AlarmReceiver : BroadcastReceiver(), KoinComponent {

    private val chatRepository: ChatRepository by inject()

    override fun onReceive(@Suppress("UNUSED_PARAMETER") context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val alarm = intent.getBundleExtra(MESSAGE_CONTENT)?.let { bundle ->
            BundleCompat.getParcelable(bundle, "alarm", Alarm::class.java)
        }
        val message = alarm?.messsage
        val channelId = alarm?.chatChannel
        if (alarm == null || message == null || channelId.isNullOrBlank()) {
            pendingResult.finish()
            return
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                chatRepository.sendMessage(channelId, message)
            } catch (error: Throwable) {
                Timber.w(error, "Failed to dispatch scheduled message for channel=%s", channelId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
