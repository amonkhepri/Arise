package com.example.rise.ui.fragments


import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.rise.R
import com.example.rise.extensions.scheduleNextAlarm
import com.example.rise.helpers.CHAT_CHANNEL
import com.example.rise.helpers.MESSAGE_CONTENT
import com.example.rise.models.Alarm
import com.example.rise.models.TextMessage
import com.example.rise.ui.recyclerview.MyFireStoreAlarmRecyclerViewAdapter
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.android.synthetic.main.fragment_dashboard.*
import java.util.*


class DashboardFragment : Fragment() {

    var byBottomNavigation: Boolean = false

    private lateinit var mQuery: Query
    private lateinit var myFireStoreAlarmRecyclerViewAdapter: MyFireStoreAlarmRecyclerViewAdapter
    val TAG = "DASHBOARD_FRAGMENT"

    var firstrun: Boolean = true
    var userID: String? = null
    lateinit var alarm: Alarm

    private val firestoreInstance : FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private var mFirestore : DocumentReference = firestoreInstance.document (
        "users/${FirebaseAuth.getInstance().currentUser?.uid ?: throw NullPointerException("UID is null.")}"
    )

    override fun onStart() {
        super.onStart()
        myFireStoreAlarmRecyclerViewAdapter.startListening()
    }

    override fun onStop() {
        super.onStop()
        myFireStoreAlarmRecyclerViewAdapter.stopListening()
    }

    override fun onCreateView (
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        FirebaseFirestore.setLoggingEnabled(true)

        floatingActionButton.setOnClickListener {
            alarm = Alarm()
            firstrun = false

            val userID: String? = activity?.intent?.getStringExtra("UsrID")
            val chatChannel: String? = activity?.intent?.getStringExtra(CHAT_CHANNEL)
            val message = activity?.intent?.getParcelableExtra<TextMessage>(MESSAGE_CONTENT)

            alarm = Alarm()
            alarm.chatChannel = chatChannel.toString()
            alarm.messsage = message

            /*arguments?.getString("chat_channel")*/
            alarm.userName = FirebaseAuth.getInstance().currentUser?.displayName.toString()

            val datePicker = DatePicker(view.context)
            val timePicker = simpleTimePicker
            val cal: Calendar = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_MONTH, datePicker.dayOfMonth)
            cal.set(Calendar.MONTH, datePicker.month)
            cal.set(Calendar.YEAR, datePicker.year)
            cal.set(Calendar.HOUR, timePicker.hour)
            cal.set(Calendar.MINUTE, timePicker.minute)
            val millis: Long = cal.timeInMillis/1000-3600*12
            alarm.timeInSeconds = millis.toInt()

            alarm.idTimeStamp = System.currentTimeMillis().toInt()
            if(message!=null) {
                println("insideFloatingActionButton")
                println(cal.timeInMillis.toString())
                println(alarm.timeInSeconds.toString())
                println(alarm.messsage.toString())
                context?.scheduleNextAlarm(alarm, true)
            }

            mQuery = queryFirestore()
        }

        val userID: String? = activity?.intent?.getStringExtra("UsrID")
        val chatChannel: String? = activity?.intent?.getStringExtra("ChannelId")

        val message = activity?.intent?.getParcelableExtra<TextMessage>("Message")

        if (userID != null && !byBottomNavigation) {
            this.userID = userID.toString()
            mFirestore = firestoreInstance.document(
                "users/$userID"
            )
        }

        alarm = Alarm()
        alarm.chatChannel = chatChannel.toString()
        alarm.messsage = message

        mQuery = queryFirestore()

        initRecyclerView(mQuery)
    }

    fun queryFirestore(): CollectionReference {

        if (!firstrun) {
            mFirestore.collection("alarms")
            mFirestore.update("id", FieldValue.increment(1))
            //TODO Consider more multiwriteproof id's
            mFirestore.collection("alarms")
                .document(alarm.idTimeStamp.toString()).set(alarm)
                .addOnSuccessListener { documentReference ->
                    Log.d(TAG, "DocumentSnapshot add with ID: ")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error adding document", e)
                }
            return mFirestore.collection("alarms")
        } else {
            return mFirestore.collection("alarms")
        }
    }

    private fun initRecyclerView(mQuery: Query) {

        myFireStoreAlarmRecyclerViewAdapter = object : MyFireStoreAlarmRecyclerViewAdapter(mQuery, context!!) {
            override fun onError(e: FirebaseFirestoreException) = Snackbar.make(
                    view!!.findViewById<View>(android.R.id.content),
                    "Error: check logs for info.", Snackbar.LENGTH_LONG
                ).show()
        }

        myFireStoreAlarmRecyclerViewAdapter.otherUsrId = this.userID
        alarmList.layoutManager = LinearLayoutManager(this.context)
        alarmList.adapter = myFireStoreAlarmRecyclerViewAdapter
        myFireStoreAlarmRecyclerViewAdapter.setQuery(mQuery)
    }
}