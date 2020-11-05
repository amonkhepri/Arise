package com.example.rise.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.rise.R
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
import com.xwray.groupie.Section
import com.xwray.groupie.kotlinandroidextensions.Item
import com.xwray.groupie.kotlinandroidextensions.ViewHolder
import kotlinx.android.synthetic.main.activity_chat.*
import java.util.*


class ChatActivity : AppCompatActivity() {

    private lateinit var currentChannelId: String
    private lateinit var currentUser: User
    private lateinit var otherUserId: String

    private lateinit var messagesListenerRegistration: ListenerRegistration
    private var shouldInitRecyclerView = true
    private lateinit var messagesSection: Section

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        supportActionBar?.title = intent.getStringExtra(AppConstants.USER_NAME)
        otherUserId = intent.getStringExtra(AppConstants.USER_ID)

        FirestoreUtil.getCurrentUser {
            currentUser = it
        }

        FirestoreUtil.getOrCreateChatChannel(otherUserId) { channelId ->

            currentChannelId = channelId
            messagesListenerRegistration = FirestoreUtil.addChatMessagesListener(channelId, this, this::updateRecyclerView)

            send_message_button.setOnClickListener {
                val messageToSend = TextMessage(editText_message.text.toString(), Calendar.getInstance().time,
                                FirebaseAuth.getInstance().currentUser!!.uid,
                                otherUserId, currentUser.name)
                editText_message.setText("")
                FirestoreUtil.sendMessage(messageToSend, channelId)
            }

            send_with_delay.setOnClickListener {
                //Run service and send with delay

                val messageToSend = TextMessage(editText_message.text.toString(), Calendar.getInstance().time,
                    FirebaseAuth.getInstance().currentUser!!.uid, otherUserId, currentUser.name)

                editText_message.setText("")

                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("UsrID", otherUserId)

                    putExtra(MESSAGE_CONTENT, messageToSend)
                    putExtra(CHAT_CHANNEL, channelId)
                }
                startActivity(intent)
            }
        }
    }

    private fun updateRecyclerView(messages: List<Item>) {

        fun init() {

            recycler_view_messages.apply {
                layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@ChatActivity)
                adapter = GroupAdapter<ViewHolder>().apply {
                    messagesSection = Section(messages)
                    this.add(messagesSection)
                }
            }
            shouldInitRecyclerView = false
        }

        fun updateItems() = messagesSection.update(messages)

        if (shouldInitRecyclerView)
            init()
        else
            updateItems()

        recycler_view_messages.scrollToPosition(recycler_view_messages.adapter!!.itemCount - 1)
    }
}