package com.example.rise.ui.dashboardNavigation.dashboard

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.rise.R
import com.example.rise.baseclasses.BaseFragment
import com.example.rise.databinding.FragmentDashboardBinding
import com.example.rise.extensions.scheduleNextAlarm
import com.example.rise.helpers.CHAT_CHANNEL
import com.example.rise.helpers.MESSAGE_CONTENT
import com.example.rise.helpers.MINUTE_SECONDS
import com.example.rise.models.Alarm
import com.example.rise.models.TextMessage
import com.example.rise.ui.dashboardNavigation.dashboard.recyclerview.MyFireStoreAlarmRecyclerViewAdapter
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import org.koin.android.ext.android.get
import timber.log.Timber
import java.util.Calendar

class DashboardFragment : BaseFragment<DashboardViewModel>() {

    var byBottomNavigation: Boolean = false

    private lateinit var mQuery: Query
    private lateinit var myFireStoreAlarmRecyclerViewAdapter: MyFireStoreAlarmRecyclerViewAdapter
    private val tag = "DASHBOARD_FRAGMENT"

    private var firstrun: Boolean = true
    private var userID: String? = null
    private lateinit var alarm: Alarm

    private val firestoreInstance: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private var mFirestore: DocumentReference = firestoreInstance.document(
        "users/${FirebaseAuth.getInstance().currentUser?.uid ?: throw NullPointerException("UID is null.")}"
    )

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onStart() {
        super.onStart()
        if (::myFireStoreAlarmRecyclerViewAdapter.isInitialized) {
            myFireStoreAlarmRecyclerViewAdapter.startListening()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::myFireStoreAlarmRecyclerViewAdapter.isInitialized) {
            myFireStoreAlarmRecyclerViewAdapter.stopListening()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        FirebaseFirestore.setLoggingEnabled(true)

        binding.floatingActionButton.setOnClickListener {
            alarm = Alarm()
            firstrun = false

            alarm.chatChannel = activity?.intent?.getStringExtra(CHAT_CHANNEL).orEmpty()
            alarm.messsage = activity?.intent?.getParcelableExtra<TextMessage>(MESSAGE_CONTENT)
            alarm.userName = FirebaseAuth.getInstance().currentUser?.displayName.orEmpty()

            val datePicker = DatePicker(view.context)
            val cal: Calendar = Calendar.getInstance()

            cal.set(Calendar.DAY_OF_MONTH, datePicker.dayOfMonth)
            cal.set(Calendar.MONTH, datePicker.month)
            cal.set(Calendar.YEAR, datePicker.year)
            cal.set(Calendar.HOUR_OF_DAY, binding.simpleTimePicker.hour)
            cal.set(Calendar.MINUTE, binding.simpleTimePicker.minute)

            val millis: Long = cal.timeInMillis

            alarm.timeInMiliseconds = millis
            alarm.idTimeStamp = System.currentTimeMillis().toInt()

            if (alarm.messsage != null) {
                context?.scheduleNextAlarm(alarm, true)
            }

            mQuery = queryFirestore()
        }

        val userID: String? = activity?.intent?.getStringExtra("UsrID")
        val chatChannel: String? = activity?.intent?.getStringExtra("ChannelId")
        val message = activity?.intent?.getParcelableExtra<TextMessage>("Message")

        if (userID != null && !byBottomNavigation) {
            this.userID = userID
            mFirestore = firestoreInstance.document(
                "users/$userID"
            )
        }

        alarm = Alarm()
        alarm.chatChannel = chatChannel.orEmpty()
        alarm.messsage = message
        mQuery = queryFirestore()
        initRecyclerView(mQuery)
    }

    private fun queryFirestore(): CollectionReference {
        if (!firstrun) {
            mFirestore.collection("alarms")
            mFirestore.update("id", FieldValue.increment(1))
            mFirestore.collection("alarms")
                .document(alarm.idTimeStamp.toString()).set(alarm)
                .addOnSuccessListener {
                    Timber.d("DocumentSnapshot add with ID: ")
                }
                .addOnFailureListener { e ->
                    Timber.tag(tag).w(e, "Error adding document")
                }
        }
        return mFirestore.collection("alarms")
    }

    private fun initRecyclerView(query: Query) {
        myFireStoreAlarmRecyclerViewAdapter = object : MyFireStoreAlarmRecyclerViewAdapter(query, requireContext()) {
            override fun onError(e: FirebaseFirestoreException) = Snackbar.make(
                binding.root,
                "Error: check logs for info.",
                Snackbar.LENGTH_LONG
            ).show()
        }

        myFireStoreAlarmRecyclerViewAdapter.otherUsrId = this.userID
        binding.alarmList.layoutManager = LinearLayoutManager(context)
        binding.alarmList.adapter = myFireStoreAlarmRecyclerViewAdapter
        myFireStoreAlarmRecyclerViewAdapter.setQuery(query)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun createViewModel() {
        viewModel = get()
    }
}
