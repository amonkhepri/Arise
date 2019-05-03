package com.example.rise.ui


import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager

import com.example.rise.models.Alarm

import com.example.rise.ui.viewModel.MainActivityViewModel
import com.firebase.ui.auth.AuthUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.longToast
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : MyAlarmRecyclerViewAdapter.OnAlarmSelectedListener, AppCompatActivity() {

    override fun onAlarmSelected(restaurant: DocumentSnapshot) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    val TAG = "MainActivity"


    val mFirestore = FirebaseFirestore.getInstance().document("sampleData/user")


    private lateinit var mQuery: Query
    private lateinit var myAlarmRecyclerViewAdapter :MyAlarmRecyclerViewAdapter
    private lateinit var mViewModel:MainActivityViewModel
    val alarms = ArrayList<Alarm>()
    var firstrun:Boolean=true
    lateinit var alarm:Alarm
    lateinit var alarmManager :AlarmManager
    lateinit var context: Context



    private val RC_SIGN_IN = 9001


    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        //bottom navigation
        when (item.itemId) {
            com.example.rise.R.id.navigation_home -> {

                return@OnNavigationItemSelectedListener true
            }
            com.example.rise.R.id.navigation_dashboard -> {

                return@OnNavigationItemSelectedListener true
            }
            com.example.rise.R.id.navigation_notifications -> {

                return@OnNavigationItemSelectedListener true
            }
        }
        false
    }

    public override fun onStop() {
        super.onStop()
        myAlarmRecyclerViewAdapter.stopListening()
    }


    public override fun onStart() {
        super.onStart()
        // Start sign in if necessary
        if (shouldStartSignIn()) {
            startSignIn()
            return
        }
           myAlarmRecyclerViewAdapter.startListening() //test
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.example.rise.R.layout.activity_main)

        bottomNavigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)
        mViewModel = ViewModelProviders.of(this).get(MainActivityViewModel::class.java)
        FirebaseFirestore.setLoggingEnabled(true);

        context=this

        floatingActionButton.setOnClickListener {


            alarm=Alarm()
            firstrun=false
            alarm.timeInMinutes=simpleTimePicker.hour * 60 +  simpleTimePicker.minute
            mQuery=queryFirestore()

           /* alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
          *//*  val second = alarm.timeInMinutes*3600000*//*
            val second=alarm.timeInMinutes*1000
            val intent = Intent(context, AlarmReceiver::class.java)

            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
       *//*     context.scheduleNextAlarm(alarm,true)
*//*


            alarmManager.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + second, pendingIntent)*/

        }

        mQuery=queryFirestore()
        initRecyclerView(mQuery)
    }


    override  fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            mViewModel.mSignIn=false

            if (resultCode != RESULT_OK && shouldStartSignIn()) {
                startSignIn()
            }
        }
    }

    private fun startSignIn() {
        // Sign in with FirebaseUI
        val intent = AuthUI.getInstance().createSignInIntentBuilder()
            .setAvailableProviders(listOf<AuthUI.IdpConfig>(AuthUI.IdpConfig.EmailBuilder().build()))
            .setIsSmartLockEnabled(false)
            .build()

        startActivityForResult(intent, RC_SIGN_IN)
        mViewModel.mSignIn=true
    }


    private fun shouldStartSignIn(): Boolean {
        return !mViewModel.mSignIn && FirebaseAuth.getInstance().currentUser == null
    }


    fun  queryFirestore():CollectionReference {

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

        myAlarmRecyclerViewAdapter =object: MyAlarmRecyclerViewAdapter(mQuery,context)
             {
                 override fun onDataChanged() =
            // Show/hide content if the query returns empty
                if (itemCount == 0) {
                  //  longToast("VISIBLE")
                  //  alarmList.visibility = View.GONE
                } else {
                    // longToast("GONE")
                    // alarmList.visibility = View.GONE
                }

            override fun onError(e: FirebaseFirestoreException) =
                Snackbar.make(
                    findViewById<View>(android.R.id.content),
                    "Error: check logs for info.", Snackbar.LENGTH_LONG).show()
             }

        alarmList.layoutManager = LinearLayoutManager(this)
        alarmList.adapter = myAlarmRecyclerViewAdapter
        myAlarmRecyclerViewAdapter.setQuery(mQuery)

    }

    private  fun testQuery(mQuery: Query){

        mQuery.get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    Log.d(TAG, document.id + " => " + document.data)
                  //  longToast(document.data.toString())
                }
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error getting documents.", exception)
            }
    }
}
