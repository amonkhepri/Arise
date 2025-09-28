package com.example.rise.ui.dashboardNavigation.people.chatActivity

import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.rise.R
import com.example.rise.baseclasses.BaseActivity
import com.example.rise.databinding.ActivityChatBinding
import com.example.rise.helpers.AppConstants
import com.example.rise.helpers.CHAT_CHANNEL
import com.example.rise.helpers.MESSAGE_CONTENT
import com.example.rise.models.TextMessage
import com.example.rise.models.User
import com.example.rise.ui.mainActivity.MainActivity
import com.example.rise.util.FirestoreUtil
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import com.xwray.groupie.Section
import org.koin.android.ext.android.get
import java.util.Calendar

class ChatActivity : BaseActivity<ChatViewModel>() {

    private lateinit var currentChannelId: String
    private lateinit var currentUser: User
    private lateinit var otherUserId: String
    private lateinit var messagesListenerRegistration: ListenerRegistration
    private var shouldInitRecyclerView = true
    private lateinit var messagesSection: Section
    private lateinit var binding: ActivityChatBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = intent.getStringExtra(AppConstants.USER_NAME)
        otherUserId = intent.getStringExtra(AppConstants.USER_ID).toString()

        FirestoreUtil.getCurrentUser {
            currentUser = it
        }

        FirestoreUtil.getOrCreateChatChannel(otherUserId) { channelId ->
            currentChannelId = channelId
            messagesListenerRegistration = FirestoreUtil.addChatMessagesListener(channelId, this, this::updateRecyclerView)

            binding.sendMessageButton.setOnClickListener {
                val messageToSend = TextMessage(
                    binding.editTextMessage.text.toString(),
                    Calendar.getInstance().time,
                    FirebaseAuth.getInstance().currentUser!!.uid,
                    otherUserId,
                    currentUser.name
                )
                binding.editTextMessage.setText("")
                FirestoreUtil.sendMessage(messageToSend, channelId)
            }

            binding.sendWithDelay.setOnClickListener {
                val messageToSend = TextMessage(
                    binding.editTextMessage.text.toString(),
                    Calendar.getInstance().time,
                    FirebaseAuth.getInstance().currentUser!!.uid,
                    otherUserId,
                    currentUser.name
                )
                binding.editTextMessage.setText("")

                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("UsrID", otherUserId)
                    putExtra(MESSAGE_CONTENT, messageToSend)
                    putExtra(CHAT_CHANNEL, channelId)
                }
                startActivity(intent)
            }
        }
    }

    private fun updateRecyclerView(messages: List<Item<*>>) {

        fun init() {
            binding.recyclerViewMessages.apply {
                layoutManager = LinearLayoutManager(this@ChatActivity)
                adapter = GroupAdapter<GroupieViewHolder>().apply {
                    messagesSection = Section(messages)
                    add(messagesSection)
                }
            }
            shouldInitRecyclerView = false
        }

        fun updateItems() = messagesSection.update(messages)

        if (shouldInitRecyclerView) {
            init()
        } else {
            updateItems()
        }

        binding.recyclerViewMessages.scrollToPosition(binding.recyclerViewMessages.adapter!!.itemCount - 1)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::messagesListenerRegistration.isInitialized) {
            messagesListenerRegistration.remove()
        }
    }

    override fun createViewModel() {
        viewModel = get()
    }
}
