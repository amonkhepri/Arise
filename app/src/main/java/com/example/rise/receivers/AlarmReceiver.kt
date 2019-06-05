package com.example.rise.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
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



        val bundle = intent.getBundleExtra(MESSAGE_CONTENT)
        var alarm  = bundle.getParcelable<Alarm>("alarm") as Alarm

        val chatChannel= alarm.chatChannel
        val messageToSend=alarm.messsage

        if(messageToSend!=null){

            FirestoreUtil.sendMessage(messageToSend, chatChannel)}else {
            Toast.makeText(context, chatChannel + "is null", Toast.LENGTH_LONG).show()
        }
    }

}