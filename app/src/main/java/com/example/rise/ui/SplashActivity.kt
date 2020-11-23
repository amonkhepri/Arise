package com.example.rise.ui


import android.content.Intent
import android.os.Bundle

import com.example.rise.baseclasses.BaseActivity
import com.example.rise.ui.dashboardNavigation.myAccount.signInActivity.SignInActivity
import com.example.rise.ui.mainActivity.MainActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.koin.android.ext.android.get
import org.koin.dsl.koinApplication

class SplashActivity : BaseActivity<SplashActivityViewModel>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        if (FirebaseAuth.getInstance().currentUser == null) {
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
        } else {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        finish()
    }

    override fun createViewModel() {
        viewModel = get()
    }
}
