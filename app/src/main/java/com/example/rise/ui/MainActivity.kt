package com.example.rise.ui


import android.content.Intent
import android.os.Bundle

import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.longToast


import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.*
import com.example.rise.ui.viewModel.MainActivityViewModel
import com.firebase.ui.auth.AuthUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth





class MainActivity : MyAlarmRecyclerViewAdapter.OnAlarmSelectedListener, AppCompatActivity() {

    override fun onAlarmSelected(restaurant: DocumentSnapshot) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    val TAG = "MainActivity"
    val mFirestore = FirebaseFirestore.getInstance().document("sampleData/user")
    private lateinit var mQuery: Query
    private lateinit var myAlarmRecyclerViewAdapter :MyAlarmRecyclerViewAdapter
    private lateinit var mViewModel:MainActivityViewModel


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

    public override fun onStart() {
        super.onStart()

        // Start sign in if necessary
        if (shouldStartSignIn()) {
            startSignIn()
            return
        }

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.example.rise.R.layout.activity_main)

        bottomNavigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)

        mViewModel = ViewModelProviders.of(this).get(MainActivityViewModel::class.java)

        FirebaseFirestore.setLoggingEnabled(true);

        mQuery=queryFirestore()
        testQuery(mQuery)
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

    fun  queryFirestore():CollectionReference{

        //Instead of using POJOS or data classes we use here HashMap

        val alarm=HashMap<String,Any>()
        alarm["testAlarm"]="23:58"

        mFirestore.collection("alarms")
            .add(alarm)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG,"DocumentSnapshot add with ID: " + documentReference.id)
            }
            .addOnFailureListener { e->
                Log.w(TAG, "Error adding document", e)
            }
      return mFirestore.collection("alarms")
    }

    private  fun testQuery(mQuery: Query){

        mQuery.get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    Log.d(TAG, document.id + " => " + document.data)
                }
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error getting documents.", exception)
            }
    }



    private fun initRecyclerView(mQuery: Query) {

        myAlarmRecyclerViewAdapter =object: MyAlarmRecyclerViewAdapter(/*this@MainActivity,,*/mQuery,this@MainActivity)
             {
                 override fun onDataChanged() =
            // Show/hide content if the query returns empty
                if (itemCount == 0) {
                    longToast("VISIBLE")
                    alarmList.visibility = View.GONE
                } else {
                    Toast.makeText(this@MainActivity, "GONE!", Toast.LENGTH_LONG).show()
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
        longToast(myAlarmRecyclerViewAdapter.itemCount.toString())
    }
}
