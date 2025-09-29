package com.example.rise.ui.dashboardNavigation.people.chatActivity

import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.rise.baseclasses.BaseActivity
import com.example.rise.databinding.ActivityChatBinding
import com.example.rise.helpers.AppConstants
import com.example.rise.helpers.CHAT_CHANNEL
import com.example.rise.helpers.MESSAGE_CONTENT
import com.example.rise.item.TextMessageItem
import com.example.rise.models.TextMessage
import com.example.rise.ui.mainActivity.MainActivity
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Section

class ChatActivity : BaseActivity<ChatViewModel>() {

    private lateinit var binding: ActivityChatBinding
    private var messagesSection: Section? = null

    override val viewModelClass = ChatViewModel::class

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = intent.getStringExtra(AppConstants.USER_NAME)
        val otherUserId = intent.getStringExtra(AppConstants.USER_ID).orEmpty()

        setupListeners()
        observeViewModel()

        viewModel.loadConversation(otherUserId)
    }

    private fun setupListeners() {
        binding.sendMessageButton.setOnClickListener {
            viewModel.sendMessage(binding.editTextMessage.text.toString())
        }

        binding.sendWithDelay.setOnClickListener {
            viewModel.scheduleMessage(binding.editTextMessage.text.toString())
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            updateRecyclerView(state.messages, state.shouldInitRecycler)
        }

        viewModel.events.observe(this) { event ->
            when (val content = event.getContentIfNotHandled()) {
                ChatViewModel.ChatEvent.ClearMessageInput -> binding.editTextMessage.setText("")
                is ChatViewModel.ChatEvent.LaunchDelayedMessage -> launchDelayedMessage(content)
                null -> Unit
            }
        }
    }

    private fun updateRecyclerView(messages: List<TextMessage>, shouldInit: Boolean) {
        if (shouldInit || messagesSection == null) {
            initRecyclerView(messages)
        } else {
            messagesSection?.update(messages.map(::TextMessageItem))
        }
        val lastIndex = (binding.recyclerViewMessages.adapter?.itemCount ?: 0) - 1
        if (lastIndex >= 0) {
            binding.recyclerViewMessages.scrollToPosition(lastIndex)
        }
    }

    private fun initRecyclerView(messages: List<TextMessage>) {
        val adapter = GroupAdapter<GroupieViewHolder>()
        messagesSection = Section(messages.map(::TextMessageItem)).also(adapter::add)
        binding.recyclerViewMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity)
            this.adapter = adapter
        }
    }

    private fun launchDelayedMessage(event: ChatViewModel.ChatEvent.LaunchDelayedMessage) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("UsrID", event.otherUserId)
            putExtra(MESSAGE_CONTENT, event.message)
            putExtra(CHAT_CHANNEL, event.channelId)
        }
        binding.editTextMessage.setText("")
        startActivity(intent)
    }
}
