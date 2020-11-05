package com.example.rise.ui.mainActivity


import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProviders
import com.example.rise.ui.fragments.DashboardFragment
import com.example.rise.ui.fragments.MyAccountFragment
import com.example.rise.ui.fragments.PeopleFragment
import com.firebase.ui.auth.AuthUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    private lateinit var mViewModel: MainActivityViewModel
    private val RC_SIGN_IN = 9001

    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        //bottom navigation
            when (item.itemId) {
                com.example.rise.R.id.navigation_people -> {
                    replaceFragment(PeopleFragment())
                    return@OnNavigationItemSelectedListener true
                }

                com.example.rise.R.id.navigation_dashboard -> {
                    var fragment = DashboardFragment()
                    fragment.byBottomNavigation = true
                    replaceFragment(fragment)
                    return@OnNavigationItemSelectedListener true
                }

                com.example.rise.R.id.navigation_account -> {
                    replaceFragment(MyAccountFragment())
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
        // If we are here because of ChatActivity,else proceed to Myaccount

        if (intent.extras != null) {
            replaceFragment(DashboardFragment())
        }else replaceFragment(MyAccountFragment())

        mViewModel = ViewModelProviders.of(this).get(MainActivityViewModel::class.java)
    }

    private fun replaceFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(com.example.rise.R.id.fragment_layout, fragment)
            .commit()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            mViewModel.mSignIn = false

            if (resultCode != RESULT_OK && shouldStartSignIn()) {
                startSignIn()
            }
        }
    }

    private fun startSignIn() {
        // Sign in with FirebaseUI
        val intent = AuthUI.getInstance().createSignInIntentBuilder()
            .setAvailableProviders(
                listOf<AuthUI.IdpConfig>(
                    AuthUI.IdpConfig.EmailBuilder().build()
                )
            )
            .setIsSmartLockEnabled(false)
            .build()

        startActivityForResult(intent, RC_SIGN_IN)
        mViewModel.mSignIn = true
    }

    private fun shouldStartSignIn(): Boolean {
        return !mViewModel.mSignIn && FirebaseAuth.getInstance().currentUser == null
    }
}