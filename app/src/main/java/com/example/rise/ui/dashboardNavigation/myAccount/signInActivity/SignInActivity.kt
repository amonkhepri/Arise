package com.example.rise.ui.dashboardNavigation.myAccount.signInActivity

import android.app.Activity
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Build
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.rise.R
import com.example.rise.baseclasses.BaseActivity
import com.example.rise.baseclasses.koinViewModelFactory
import com.example.rise.databinding.ActivitySignInBinding
import com.example.rise.ui.mainActivity.MainActivity
import com.firebase.ui.auth.ErrorCodes
import com.firebase.ui.auth.IdpResponse
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class SignInActivity : BaseActivity() {

    private val viewModel: SignInViewModel by viewModels {
        koinViewModelFactory(SignInViewModel::class)
    }

    private val RC_SIGN_IN = 1
    private lateinit var binding: ActivitySignInBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.accountSignIn.setOnClickListener {
            viewModel.onSignInClicked()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectState() }
                launch { collectEvents() }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val response = IdpResponse.fromResultIntent(data)

            if (resultCode == RESULT_OK) {
                viewModel.onSignInSuccess()
            } else {
                val failure = when {
                    response == null -> SignInViewModel.SignInFailure.Cancelled
                    response.error?.errorCode == ErrorCodes.NO_NETWORK -> SignInViewModel.SignInFailure.NoNetwork
                    response.error != null -> SignInViewModel.SignInFailure.Unknown(response.error?.localizedMessage)
                    else -> SignInViewModel.SignInFailure.Unknown(null)
                }
                viewModel.onSignInFailure(failure)
            }
        }
    }

    private suspend fun collectState() {
        viewModel.uiState.collect { state ->
            binding.progressBar.isVisible = state.isLoading
            binding.accountSignIn.isEnabled = !state.isLoading
        }
    }

    private suspend fun collectEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is SignInViewModel.Event.LaunchSignIn -> startActivityForResult(event.intent, RC_SIGN_IN)
                is SignInViewModel.Event.ShowMessage -> Snackbar.make(
                    binding.constraintLayout,
                    event.message,
                    Snackbar.LENGTH_LONG
                ).show()

                SignInViewModel.Event.NavigateToMain -> {
                    val intent = Intent(this, MainActivity::class.java).addFlags(
                        FLAG_ACTIVITY_CLEAR_TASK or FLAG_ACTIVITY_NEW_TASK
                    )
                    startActivity(intent)
                }
            }
        }
    }
}
