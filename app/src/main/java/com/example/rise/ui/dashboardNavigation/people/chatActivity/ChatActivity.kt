package com.example.rise.ui.dashboardNavigation.people.chatActivity

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.rise.baseclasses.BaseActivity
import com.example.rise.databinding.ActivityChatBinding
import com.example.rise.helpers.AppConstants
import com.example.rise.helpers.CHAT_CHANNEL
import com.example.rise.helpers.MESSAGE_CONTENT
import com.example.rise.item.TextMessageItem
import com.example.rise.ui.mainActivity.MainActivity
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Section
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect

class ChatActivity : BaseActivity<ChatViewModel>() {

    override val viewModelClass = ChatViewModel::class

    private lateinit var binding: ActivityChatBinding
    private val messagesSection = Section()
    private val adapter = GroupAdapter<GroupieViewHolder>().apply { add(messagesSection) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val otherUserId = intent.getStringExtra(AppConstants.USER_ID).orEmpty()
        val otherUserName = intent.getStringExtra(AppConstants.USER_NAME).orEmpty()
        supportActionBar?.title = otherUserName

        setupRecyclerView()
        setupListeners()

        lifecycleScope.launchWhenStarted {
            viewModel.uiState.collectLatest { state ->
                renderState(state)
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.events.collect { event ->
                when (event) {
                    is ChatViewModel.ChatEvent.LaunchSchedule -> handleScheduleEvent(event)
                }
            }
        }

        viewModel.initialiseConversation(otherUserId, otherUserName)
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
            viewModel.scheduleMessage(message)
            binding.editTextMessage.setText("")
        }
    }

    private fun renderState(state: ChatViewModel.ChatUiState) {
        supportActionBar?.title = state.title
        val items = state.messages.map { message -> TextMessageItem(message) }
        messagesSection.update(items)
        if (items.isNotEmpty()) {
            binding.recyclerViewMessages.scrollToPosition(items.lastIndex)
        }
    }

    private fun handleScheduleEvent(event: ChatViewModel.ChatEvent.LaunchSchedule) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("UsrID", event.otherUserId)
            putExtra(MESSAGE_CONTENT, event.message)
            putExtra(CHAT_CHANNEL, event.channelId)
        }
        startActivity(intent)
    }
}
