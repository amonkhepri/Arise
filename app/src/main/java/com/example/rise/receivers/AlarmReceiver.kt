package com.example.rise.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.rise.helpers.ALARM_ID
import com.example.rise.helpers.CHAT_CHANNEL
import com.example.rise.helpers.MESSAGE_CONTENT
import com.example.rise.models.Alarm
import com.example.rise.models.TextMessage
import com.example.rise.util.FirestoreUtil
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot


class AlarmReceiver  : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val messageToSend=intent.getParcelableExtra<TextMessage>(MESSAGE_CONTENT)
        val channelId=intent.getStringExtra(CHAT_CHANNEL)

        if(messageToSend!=null){
        FirestoreUtil.sendMessage(messageToSend, channelId)}else Toast.makeText(context,channelId,Toast.LENGTH_LONG).show()

    }

}