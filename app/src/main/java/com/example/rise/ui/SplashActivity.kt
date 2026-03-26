package com.example.rise.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.rise.baseclasses.BaseActivity
import com.example.rise.baseclasses.koinViewModelFactory
import com.example.rise.ui.SplashActivityViewModel.NavigationEvent
import com.example.rise.ui.mainActivity.MainActivity
import com.example.rise.ui.signInActivity.SignInActivity
import kotlinx.coroutines.launch

class SplashActivity : BaseActivity() {

    private val viewModel: SplashActivityViewModel by viewModels {
        koinViewModelFactory(SplashActivityViewModel::class)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.events.collect { event ->
                        when (event) {
                        is NavigationEvent.ToSignIn -> {
                            val intent = Intent(this@SplashActivity, SignInActivity::class.java)
                            event.onboardingAction?.applyTo(intent)
                            startActivity(intent)
                        }
                        is NavigationEvent.ToMain -> {
                            val intent = Intent(this@SplashActivity, MainActivity::class.java)
                            event.onboardingAction?.applyTo(intent)
                            startActivity(intent)
                        }
                    }
                    finish()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.determineDestination(rawDeepLink = intent?.dataString)
    }
}
