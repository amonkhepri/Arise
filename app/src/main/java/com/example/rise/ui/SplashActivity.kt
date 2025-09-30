package com.example.rise.ui


import android.content.Intent
import android.os.Bundle

import androidx.lifecycle.lifecycleScope
import com.example.rise.baseclasses.BaseActivity
import com.example.rise.ui.SplashActivityViewModel.NavigationEvent
import com.example.rise.ui.dashboardNavigation.myAccount.signInActivity.SignInActivity
import com.example.rise.ui.mainActivity.MainActivity
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.collect

class SplashActivity : BaseActivity<SplashActivityViewModel>() {

    override val viewModelClass = SplashActivityViewModel::class

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        lifecycleScope.launchWhenStarted {
            viewModel.events.collect { event ->
                when (event) {
                    NavigationEvent.ToSignIn -> startActivity(Intent(this@SplashActivity, SignInActivity::class.java))
                    NavigationEvent.ToMain -> startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                }
                finish()
            }
        }

        viewModel.determineDestination()
    }
}
