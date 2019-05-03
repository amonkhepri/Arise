package com.example.rise.receivers

import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.os.Handler
import android.util.Log
import android.widget.Toast
import com.example.rise.extensions.config
import com.example.rise.extensions.hideNotification
import com.example.rise.extensions.isScreenOn
import com.example.rise.extensions.showAlarmNotification
import com.example.rise.helpers.ALARM_ID
import com.example.rise.models.Alarm
import com.example.rise.ui.ReminderActivity
import com.google.firebase.firestore.*
import com.google.firebase.firestore.EventListener
import java.util.*
import kotlin.collections.ArrayList


class AlarmReceiver  : BroadcastReceiver() , EventListener<QuerySnapshot>{

    override fun onEvent(p0: QuerySnapshot?, p1: FirebaseFirestoreException?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onReceive(context: Context, intent: Intent) {

        val id = intent.getIntExtra(ALARM_ID,-1)
        var idString: String=id.toString()
        lateinit var alarm:Alarm
        Toast.makeText(context,idString,Toast.LENGTH_LONG).show()
        /*val mFirestore =FirebaseFirestore.getInstance().document("sampleData/user/alarms/$idString")



        mFirestore.get().addOnSuccessListener { documentSnapshot ->
            alarm = documentSnapshot.toObject(Alarm::class.java)!!
        }.addOnFailureListener { exception ->
            Log.d(TAG, "get failed with ", exception)
        }

        //context.dbHelper.getAlarmWithId(id) ?: return



        if (context.isScreenOn()) {

            context.showAlarmNotification(alarm)
            Handler().postDelayed({
                context.hideNotification(id)
            }, context.config.alarmMaxReminderSecs * 1000L)
        } else {
            Intent(context, ReminderActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(ALARM_ID, id)
                context.startActivity(this)
            }
        }
*/
    }

        /*val id = intent.getIntExtra(ALARM_ID, -1)
        //TODO implement firestore

        val mFirestore = FirebaseFirestore.getInstance().collection("sampleData/user/alarms")

        val mSnapshots = ArrayList<DocumentSnapshot>()
        var mRegistration: ListenerRegistration? = null
        lateinit var alarm: Alarm
        lateinit var mQuery: Query

        Toast.makeText(context,"inAlarmReceiver",Toast.LENGTH_LONG).show()

        mQuery=mFirestore

        mQuery.addSnapshotListener(this)


        if (mQuery != null && mRegistration == null) {
                mRegistration = mQuery!!.addSnapshotListener(this)
            }

        fun getSnapshot(index: Int): DocumentSnapshot {
            return mSnapshots[index]
        }

        alarm=getSnapshot(id).toObject(Alarm::class.java)!!
        //val alarm = context.dbHelper.getAlarmWithId(id) ?: return


        if (context.isScreenOn()) {
            context.showAlarmNotification(alarm)
            Handler().postDelayed({
                context.hideNotification(id)
            }, 5 * 1000L)
        } else {
            Intent(context, ReminderActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(ALARM_ID, id)
                context.startActivity(this)
            }
        }


    }*/

}