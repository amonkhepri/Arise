package com.example.rise.ui.signInActivity

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.rise.R
import com.example.rise.baseclasses.BaseActivity
import com.example.rise.baseclasses.koinViewModelFactory
import com.example.rise.databinding.ActivitySignInBinding
import com.example.rise.ui.mainActivity.MainActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class SignInActivity : BaseActivity() {

    private val viewModel: SignInViewModel by viewModels {
        koinViewModelFactory(SignInViewModel::class)
    }

    private lateinit var binding: ActivitySignInBinding
    private val credentialManager by lazy { CredentialManager.create(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Primary call-to-action button; submits either sign-in or registration
        // depending on the active mode set via @toggleModeButton
        binding.primaryActionButton.setOnClickListener { onPrimaryActionClicked() }
        binding.toggleModeButton.setOnClickListener { viewModel.toggleMode() }
        binding.savedCredentialsButton.setOnClickListener { launchCredentialManager() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectState() }
                launch { collectEvents() }
            }
        }
    }

    private fun onPrimaryActionClicked() {
        val email = binding.emailInput.text?.toString().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()
        val name = binding.nameInput.text?.toString().orEmpty()
        viewModel.submitPrimaryAction(name, email, password)
    }

    private fun launchCredentialManager() {
        lifecycleScope.launch {
            val request = buildCredentialRequest()
            try {
                val result = credentialManager.getCredential(this@SignInActivity, request)
                viewModel.onCredentialReceived(result.credential)
            } catch (error: Exception) {
                viewModel.onCredentialError(error)
            }
        }
    }

    private fun buildCredentialRequest(): GetCredentialRequest {
        return GetCredentialRequest.Builder()
            .addCredentialOption(GetPasswordOption())
            .build()
    }

    private suspend fun collectState() {
        viewModel.uiState.collect { state ->
            binding.progressBar.isVisible = state.isLoading
            binding.primaryActionButton.isEnabled = !state.isLoading
            binding.savedCredentialsButton.isEnabled = !state.isLoading
            binding.toggleModeButton.isEnabled = !state.isLoading

            val isRegistering = state.mode == SignInViewModel.Mode.Register
            binding.nameInputLayout.isVisible = isRegistering
            binding.primaryActionButton.text = if (isRegistering) {
                getString(R.string.sign_in_create_account)
            } else {
                getString(R.string.sign_in_button)
            }
            binding.toggleModeButton.text = if (isRegistering) {
                getString(R.string.sign_in_mode_sign_in)
            } else {
                getString(R.string.sign_in_mode_register)
            }
        }
    }
    private suspend fun collectEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is SignInViewModel.Event.ShowMessage -> {
                    Snackbar.make(binding.constraintLayout, event.message, Snackbar.LENGTH_LONG).show()
                }
                SignInViewModel.Event.NavigateToMain -> navigateToMain()
                is SignInViewModel.Event.SaveCredentials -> {
                    saveCredentials(event.email, event.password)
                }
            }
        }
    }

    private suspend fun saveCredentials(email: String, password: String) {
        val request = CreatePasswordRequest(id = email, password = password)
        runCatching { credentialManager.createCredential(this, request) }
    }

    private fun navigateToMain() {
        setResult(RESULT_OK)
        if (isTaskRoot) {
            val intent = Intent(this, MainActivity::class.java).addFlags(
                FLAG_ACTIVITY_CLEAR_TASK or FLAG_ACTIVITY_NEW_TASK
            )
            startActivity(intent)
        }
        finish()
    }
}
