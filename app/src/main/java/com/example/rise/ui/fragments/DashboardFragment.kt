package com.example.rise.ui.fragments


import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.rise.R
import com.example.rise.extensions.toast
import com.example.rise.models.Alarm
import com.example.rise.ui.MyAlarmRecyclerViewAdapter
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.android.synthetic.main.fragment_dashboard.*




class DashboardFragment : Fragment() {


    private lateinit var mQuery: Query
    private lateinit var myAlarmRecyclerViewAdapter : MyAlarmRecyclerViewAdapter
    val TAG = "DASHBOARD_FRAGMENT"

    var firstrun:Boolean=true

    var userID :String?=null

    lateinit var alarm:Alarm

    private val firestoreInstance: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

        private var mFirestore: DocumentReference=firestoreInstance.document(
            "users/${FirebaseAuth.getInstance().currentUser?.uid
                ?: throw NullPointerException("UID is null.")}"
        )



    override fun onStart() {
        super.onStart()
        myAlarmRecyclerViewAdapter.startListening()
    }

    override fun onStop() {
        super.onStop()
        myAlarmRecyclerViewAdapter.stopListening()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment

        var view : View= inflater.inflate(R.layout.fragment_dashboard, container, false)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        FirebaseFirestore.setLoggingEnabled(true)

        floatingActionButton.setOnClickListener {
            alarm= Alarm()
            firstrun=false
            alarm.timeInMinutes=simpleTimePicker.hour * 60 +  simpleTimePicker.minute
            mQuery=queryFirestore()
        }

        //if we are here because of ChatActivity
        val userID: String?= activity?.intent?.getStringExtra("UsrID")


        if(userID!=null){
            this.userID= userID.toString()
            mFirestore=firestoreInstance.document(
                "users/$userID")
        }



        mQuery=queryFirestore()
        initRecyclerView(mQuery)

    }

    fun  queryFirestore(): CollectionReference {

        if(!firstrun) {
            mFirestore.collection("alarms")
            mFirestore.update("id", FieldValue.increment(1))

            //TODO Consider more multiwriteprooof id's
            val timestamp=System.currentTimeMillis().toInt()
            alarm.id=timestamp

            mFirestore.collection("alarms")
                .document(timestamp.toString()).set(alarm)
                .addOnSuccessListener { documentReference ->
                    Log.d(TAG, "DocumentSnapshot add with ID: " )
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error adding document", e)
                }
            return mFirestore.collection("alarms")
        }else {


            return mFirestore.collection("alarms")
        }
    }

    private fun initRecyclerView(mQuery: Query) {

        myAlarmRecyclerViewAdapter =object: MyAlarmRecyclerViewAdapter(mQuery,context!!)
        {



            override fun onError(e: FirebaseFirestoreException) =
                Snackbar.make(
                    view!!.findViewById<View>(android.R.id.content),
                    "Error: check logs for info.", Snackbar.LENGTH_LONG).show()
        }
        myAlarmRecyclerViewAdapter.otherUsrId=this.userID

        alarmList.layoutManager = LinearLayoutManager(this.context)
        alarmList.adapter = myAlarmRecyclerViewAdapter
        myAlarmRecyclerViewAdapter.setQuery(mQuery)

    }
}
