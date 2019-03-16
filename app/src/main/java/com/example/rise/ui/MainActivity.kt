package com.example.rise.ui

import android.os.Bundle
import android.support.design.widget.BottomNavigationView
import android.support.v7.app.AppCompatActivity
import android.util.Log

import com.example.rise.data.Alarm
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.longToast
import android.support.v7.widget.LinearLayoutManager
import android.support.design.widget.Snackbar
import com.example.rise.R
import com.google.firebase.firestore.FirebaseFirestoreException

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query


class MainActivity : MyAlarmRecyclerViewAdapter.OnAlarmSelectedListener, AppCompatActivity() {

    override fun onAlarmSelected(restaurant: DocumentSnapshot) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    val TAG = "MainActivity"
    val mFirestore = FirebaseFirestore.getInstance().document("sampleData/user")
    private val mQuery: Query? = null
    private val myAlarmRecyclerViewAdapter :MyAlarmRecyclerViewAdapter


    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->

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

    override fun onListFragmentInteraction(item: Alarm?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }





    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.example.rise.R.layout.activity_main)

        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)

        if(savedInstanceState==null){
                supportFragmentManager
                .beginTransaction()
                .add(R.id.fragment, AlarmListFragment.newInstance(), "alarmList")
                .commit()

        }


        firestoreAddUsername()
        firestoreAddAlarm()
        firestoreReadAlarmsIntoUI()
        initRecyclerView()

    }

    fun firestoreReadAlarmsIntoUI(){

        mFirestore.collection("users")
            .get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    Log.d(TAG, document.id + " => " + document.data)


                    longToast(document.data.toString())
                }
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error getting documents.", exception)
            }


    }

    private fun initRecyclerView() {

        if (mQuery == null) {
            Log.w(TAG, "No query, not initializing RecyclerView")
        }

        myAlarmRecyclerViewAdapter = object : MyAlarmRecyclerViewAdapter(this,mQuery) {

            protected fun onDataChanged() {
                // Show/hide content if the query returns empty.
                if (getItemCount() === 0) {
                    mRestaurantsRecycler.setVisibility(View.GONE)
                    mEmptyView.setVisibility(View.VISIBLE)
                } else {
                    mRestaurantsRecycler.setVisibility(View.VISIBLE)
                    mEmptyView.setVisibility(View.GONE)
                }
            }

            protected fun onError(e: FirebaseFirestoreException) {
                // Show a snackbar on errors
                Snackbar.make(
                    findViewById<View>(android.R.id.content),
                    "Error: check logs for info.", Snackbar.LENGTH_LONG
                ).show()
            }
        }

        mRestaurantsRecycler.setLayoutManager(LinearLayoutManager(this))
        mRestaurantsRecycler.setAdapter(mAdapter)
    }

    fun firestoreAddAlarm(){

        val alarm=HashMap<String,Any>()
        alarm["testAlarm"]="23:59"

        mFirestore.collection("alarms")
            .add(alarm)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG,"DocumentSnapshot add with ID: " + documentReference.id)
            }
            .addOnFailureListener { e->
                Log.w(TAG, "Error adding document", e)
            }


    }

    fun firestoreAddUsername(){

        //We're using here HashMap's instead of POJO's which I think is neat

        val user = HashMap<String, Any>()
        user["first"] = "Ada"
        user["last"] = "Lovelace"
        user["born"] = 1815

// Add a new document with a generated ID
        mFirestore.collection("users")
            .add(user)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "DocumentSnapshot added with ID: " + documentReference.id)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error adding document", e)
            }



        val user1 = HashMap<String, Any>()
        user1["first"] = "Alan"
        user1["middle"] = "Mathison"
        user1["last"] = "Turring"
        user1["born"] = 1912

// Add a new document with a generated ID
        mFirestore.collection("users")
            .add(user1)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "DocumentSnapshot added with ID: " + documentReference.id)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error adding document", e)
            }


        //read

             mFirestore.collection("users")
            .get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    Log.d(TAG, document.id + " => " + document.data)
                }
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error getting documents.", exception)
            }

    }

}
