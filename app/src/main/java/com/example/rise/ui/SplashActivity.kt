package com.example.rise.ui

import android.content.Intent
import android.os.Bundle

import com.example.rise.baseclasses.BaseActivity
import com.example.rise.ui.mainActivity.MainActivity
import com.google.firebase.FirebaseApp
import com.example.rise.ui.dashboardNavigation.myAccount.signInActivity.SignInActivity

class SplashActivity : BaseActivity<SplashActivityViewModel>() {

    override val viewModelClass = SplashActivityViewModel::class

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        val destination = viewModel.resolveDestination()
        val target = when (destination) {
            SplashActivityViewModel.Destination.SIGN_IN -> SignInActivity::class.java
            SplashActivityViewModel.Destination.MAIN -> MainActivity::class.java
        }
        startActivity(Intent(this, target))
        finish()
    }
}
