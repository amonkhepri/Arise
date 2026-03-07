package com.example.rise.ui.dashboardNavigation.people.chatActivity

import android.content.Context
import android.content.Intent
import android.app.TimePickerDialog
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.rise.R
import com.example.rise.auth.AuthenticationService
import com.example.rise.baseclasses.BaseActivity
import com.example.rise.baseclasses.koinViewModelFactory
import com.example.rise.data.dashboard.AlarmRepository
import com.example.rise.databinding.ActivityChatBinding
import com.example.rise.extensions.scheduleNextMessage
import com.example.rise.helpers.AppConstants
import com.example.rise.item.TextMessageItem
import com.example.rise.ui.alarm.models.Alarm
import com.example.rise.ui.common.toDisplayColor
import com.example.rise.ui.common.toDisplayText
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Section
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.*

class ChatActivity : BaseActivity() {

    companion object {
        internal fun createLaunchIntent(
            context: Context,
            launchContract: ChatLaunchContract,
        ): Intent = Intent(context, ChatActivity::class.java).apply {
            launchContract.toExtras().forEach { (key, value) ->
                putExtra(key, value)
            }
        }
    }

    private val viewModel: ChatViewModel by viewModels {
        koinViewModelFactory(ChatViewModel::class)
    }

    private val alarmRepository: AlarmRepository by inject()
    private val authenticationService: AuthenticationService by inject()

    private lateinit var binding: ActivityChatBinding
    private val messagesSection = Section()
    private val adapter = GroupAdapter<GroupieViewHolder>().apply { add(messagesSection) }
    private var lastMessageListRenderState: MessageListRenderState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val launchContract = resolveChatLaunchContract(
            userId = intent.getStringExtra(AppConstants.USER_ID),
            userName = intent.getStringExtra(AppConstants.USER_NAME),
            conversationId = intent.getStringExtra(AppConstants.CONVERSATION_ID),
        )
        supportActionBar?.title = launchContract.userName
        setTitleColor()

        setupRecyclerView()
        setupListeners()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        renderState(state)
                    }
                }

                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is ChatViewModel.ChatEvent.ShowTimePicker -> showTimePicker(event.messageText)
                            is ChatViewModel.ChatEvent.ScheduleDelayedMessage -> handleDelayedMessage(event)
                        }
                    }
                }
            }
        }

        viewModel.initialiseConversation(launchContract.userId, launchContract.userName)
    }

    private fun setupRecyclerView() {
        binding.recyclerViewMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity)
            adapter = this@ChatActivity.adapter
        }
    }

    private fun setupListeners() {
        binding.sendMessageButton.setOnClickListener {
            val message = binding.editTextMessage.text.toString()
            viewModel.sendMessage(message)
            binding.editTextMessage.setText("")
        }

        binding.sendWithDelay.setOnClickListener {
            val message = binding.editTextMessage.text.toString()
            if (message.trim().isEmpty()) {
                Toast.makeText(this, "Please enter a message first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.scheduleMessage(message)
        }
    }

    private fun renderState(state: ChatViewModel.ChatUiState) {
        supportActionBar?.title = state.title
        setTitleColor()
        binding.toolbar.subtitle = state.presence.toDisplayText(this)
        binding.toolbar.setSubtitleTextColor(state.presence.toDisplayColor(this))
        val currentUserId = resolveCurrentUserId(
            stateCurrentUserId = state.currentUser?.id,
            authenticatedUser = authenticationService.currentUser(),
        )
        val nextRenderState = MessageListRenderState(
            messages = state.messages,
            currentUserId = currentUserId,
        )
        val items = state.messages.map { message ->
            TextMessageItem(message = message, currentUserId = currentUserId)
        }

        if (shouldUpdateMessageRows(previousState = lastMessageListRenderState, nextState = nextRenderState)) {
            val previousCount = messagesSection.itemCount
            messagesSection.update(items)
            lastMessageListRenderState = nextRenderState
            if (items.isNotEmpty() && previousCount != items.size) {
                binding.recyclerViewMessages.scrollToPosition(items.lastIndex)
            }
        }

        // Show error messages
        state.errorMessage?.let { error ->
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        }
    }

    private fun showTimePicker(messageText: String) {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(
            this,
            R.style.CustomTimePickerDialog,
            { _, hourOfDay, minute ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)

                    // If the selected time is in the past, add one day
                    if (timeInMillis < System.currentTimeMillis()) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                viewModel.confirmScheduleMessage(messageText, selectedCalendar.timeInMillis)
                binding.editTextMessage.setText("")
            },
            currentHour,
            currentMinute,
            false // Use 12-hour format
        ).apply {
            setTitle("Schedule message")
            show()
        }
    }

    private fun handleDelayedMessage(event: ChatViewModel.ChatEvent.ScheduleDelayedMessage) {
        lifecycleScope.launch {
            try {
                val authenticatedUser = authenticationService.currentUser() ?: return@launch
                val alarm = Alarm(
                    idTimeStamp = System.currentTimeMillis().toInt(),
                    timeInMiliseconds = event.timeInMillis,
                    userName = resolveScheduledMessageSenderName(authenticatedUser),
                    chatChannel = event.conversationId,
                    messsage = event.message,
                )
                alarmRepository.saveAlarm(authenticatedUser.id, alarm)
                scheduleNextMessage(alarm)
                Toast.makeText(this@ChatActivity, "Message scheduled", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "Failed to schedule: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setTitleColor() {
        supportActionBar?.title?.let { title ->
            val receiverNameGray = ContextCompat.getColor(this, R.color.receiverNameGray)
            val spannableTitle = SpannableString(title)
            spannableTitle.setSpan(
                ForegroundColorSpan(receiverNameGray),
                0,
                title.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannableTitle.setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                title.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            supportActionBar?.title = spannableTitle
        }
    }
}

internal data class MessageListRenderState(
    val messages: List<com.example.rise.models.TextMessage>,
    val currentUserId: String?,
)

internal fun shouldUpdateMessageRows(
    previousState: MessageListRenderState?,
    nextState: MessageListRenderState,
): Boolean = previousState != nextState

internal fun resolveCurrentUserId(
    stateCurrentUserId: String?,
    authenticatedUser: AuthenticationService.User?,
): String? = stateCurrentUserId ?: authenticatedUser?.id

internal fun resolveScheduledMessageSenderName(
    authenticatedUser: AuthenticationService.User?,
): String = authenticatedUser?.displayName.orEmpty()

internal data class ChatLaunchContract(
    val userId: String,
    val userName: String,
    val conversationId: String? = null,
) {
    fun toExtras(): Map<String, String> {
        val extras = mutableMapOf(
            AppConstants.USER_ID to userId,
            AppConstants.USER_NAME to userName,
        )
        conversationId?.let { extras[AppConstants.CONVERSATION_ID] = it }
        return extras
    }
}

internal fun resolveChatLaunchContract(
    userId: String?,
    userName: String?,
    conversationId: String?,
): ChatLaunchContract = ChatLaunchContract(
    userId = userId.orEmpty(),
    userName = userName.orEmpty(),
    conversationId = conversationId,
)
