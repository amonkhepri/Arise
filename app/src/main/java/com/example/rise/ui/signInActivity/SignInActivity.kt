package com.example.rise.ui.signInActivity

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.rise.R
import com.example.rise.baseclasses.BaseActivity
import com.example.rise.baseclasses.koinViewModelFactory
import com.example.rise.data.auth.TelegramAuthData
import com.example.rise.featureflags.TelegramAuthFlagProvider
import com.example.rise.ui.mainActivity.MainActivity
import kotlinx.coroutines.flow.collectLatest
import org.koin.android.ext.android.inject
import java.util.Locale
import androidx.core.graphics.drawable.toDrawable

class SignInActivity : BaseActivity() {

    private val viewModel: SignInViewModel by viewModels {
        koinViewModelFactory(SignInViewModel::class)
    }
    private val telegramFlagProvider: TelegramAuthFlagProvider by inject()

    private val credentialManager by lazy { CredentialManager.create(this) }
    private val telegramAuthLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when (result.resultCode) {
            RESULT_OK -> {
                val authData = extractTelegramData(result.data)
                if (authData != null) {
                    viewModel.signInWithTelegram(authData)
                } else {
                    viewModel.showMessage(getString(R.string.sign_in_telegram_missing_data))
                }
            }
            RESULT_CANCELED -> {
                val message = result.data?.getStringExtra(TelegramAuthActivity.EXTRA_ERROR_MESSAGE)
                if (!message.isNullOrBlank()) {
                    viewModel.showMessage(message)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val backgroundColor = ContextCompat.getColor(this, R.color.chatBackground)
        window.statusBarColor = backgroundColor
        window.
        navigationBarColor = backgroundColor
        window.setBackgroundDrawable(backgroundColor.toDrawable())
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            MaterialTheme {
                val snackbarHostState = remember { SnackbarHostState() }

                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val telegramEnabled by telegramFlagProvider.observeEnabled().collectAsStateWithLifecycle(initialValue = false)
                var name by rememberSaveable { mutableStateOf("") }
                var email by rememberSaveable { mutableStateOf("") }
                var password by rememberSaveable { mutableStateOf("") }

                LaunchedEffect(Unit) {
                    viewModel.events.collectLatest { event ->
                        when (event) {
                            is SignInViewModel.Event.ShowMessage -> {
                                snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Short)
                            }
                            SignInViewModel.Event.NavigateToMain -> navigateToMain()
                            is SignInViewModel.Event.SaveCredentials -> saveCredentials(event.email, event.password)
                        }
                    }
                }

                SignInScreen(
                    uiState = uiState,
                    name = name,
                    onNameChange = { name = it },
                    email = email,
                    onEmailChange = { email = it },
                    password = password,
                    onPasswordChange = { password = it },
                    telegramEnabled = telegramEnabled,
                    snackbarHostState = snackbarHostState,
                    onPrimaryAction = { viewModel.submitPrimaryAction(name.trim(), email.trim(), password) },
                    onToggleMode = { viewModel.toggleMode() },
                    onSavedCredentials = { launchCredentialManager() },
                    onTelegram = {
                        if (telegramEnabled) {
                            launchTelegramAuth()
                        } else {
                            viewModel.showMessage(getString(R.string.sign_in_telegram_disabled))
                        }
                    }
                )
            }
        }
    }

    private fun launchCredentialManager() {
        lifecycleScope.launchWhenStarted {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetPasswordOption())
                .build()
            runCatching {
                credentialManager.getCredential(this@SignInActivity, request).credential
            }.onSuccess { credential ->
                viewModel.onCredentialReceived(credential)
            }.onFailure { error ->
                viewModel.onCredentialError(error as? Exception ?: Exception(error))
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

    private fun launchTelegramAuth() {
        val intent = Intent(this, TelegramAuthActivity::class.java)
        telegramAuthLauncher.launch(intent)
    }

    private fun extractTelegramData(intent: Intent?): TelegramAuthData? {
        intent ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(TelegramAuthActivity.EXTRA_AUTH_DATA, TelegramAuthData::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(TelegramAuthActivity.EXTRA_AUTH_DATA)
        }
    }
}

internal object SignInTestTags {
    const val NameInput = "nameInput"
    const val EmailInput = "emailInput"
    const val PasswordInput = "passwordInput"
    const val PrimaryActionButton = "primaryActionButton"
    const val SavedCredentialsButton = "savedCredentialsButton"
    const val ToggleModeButton = "toggleModeButton"
}

@Composable
private fun SignInScreen(
    uiState: SignInViewModel.UiState,
    name: String,
    onNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    telegramEnabled: Boolean,
    snackbarHostState: SnackbarHostState,
    onPrimaryAction: () -> Unit,
    onToggleMode: () -> Unit,
    onSavedCredentials: () -> Unit,
    onTelegram: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val backgroundColor = colorResource(id = R.color.chatBackground)
    val fieldBackground = colorResource(id = R.color.cardBackground)
    val textColor = colorResource(id = R.color.messageTextColor)
    val outlineColor = colorResource(id = R.color.messageTextColor)
    val accentGreen = colorResource(id = R.color.matrixGreen)
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = backgroundColor,
        contentColor = textColor
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(backgroundColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_moon),
                    contentDescription = stringResource(id = R.string.app_name),
                    modifier = Modifier
                        .size(200.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                TextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SignInTestTags.NameInput),
                    placeholder = { Text(text = stringResource(R.string.sign_in_name_hint)) },
                    singleLine = true,
                    enabled = !uiState.isLoading,
                    trailingIcon = {
                        if (name.isNotEmpty()) {
                            IconButton(onClick = { onNameChange("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = textColor
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = {
                        focusManager.moveFocus(FocusDirection.Down)
                    }),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = fieldBackground,
                        unfocusedContainerColor = fieldBackground,
                        disabledContainerColor = fieldBackground,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        cursorColor = textColor,
                        focusedPlaceholderColor = textColor.copy(alpha = 0.6f),
                        unfocusedPlaceholderColor = textColor.copy(alpha = 0.6f),
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        disabledTextColor = textColor.copy(alpha = 0.5f)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = email,
                    onValueChange = onEmailChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SignInTestTags.EmailInput),
                    placeholder = { Text(text = stringResource(R.string.sign_in_email_optional_hint)) },
                    singleLine = true,
                    enabled = !uiState.isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = {
                        focusManager.moveFocus(FocusDirection.Down)
                    }),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = fieldBackground,
                        unfocusedContainerColor = fieldBackground,
                        disabledContainerColor = fieldBackground,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        cursorColor = textColor,
                        focusedPlaceholderColor = textColor.copy(alpha = 0.6f),
                        unfocusedPlaceholderColor = textColor.copy(alpha = 0.6f),
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        disabledTextColor = textColor.copy(alpha = 0.5f)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SignInTestTags.PasswordInput),
                    placeholder = { Text(text = stringResource(R.string.sign_in_password_hint)) },
                    singleLine = true,
                    enabled = !uiState.isLoading,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            val icon = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                            Icon(imageVector = icon, contentDescription = null, tint = textColor)
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus(force = true)
                        if (!uiState.isLoading) onPrimaryAction()
                    }),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = fieldBackground,
                        unfocusedContainerColor = fieldBackground,
                        disabledContainerColor = fieldBackground,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        cursorColor = textColor,
                        focusedPlaceholderColor = textColor.copy(alpha = 0.6f),
                        unfocusedPlaceholderColor = textColor.copy(alpha = 0.6f),
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        disabledTextColor = textColor.copy(alpha = 0.5f)
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        onPrimaryAction()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SignInTestTags.PrimaryActionButton),
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = fieldBackground,
                        contentColor = textColor
                    )
                ) {
                    val buttonLabel = if (uiState.mode == SignInViewModel.Mode.Register) {
                        stringResource(R.string.sign_in_create_account)
                    } else {
                        stringResource(R.string.sign_in_button)
                    }
                    Text(text = buttonLabel.uppercase(Locale.getDefault()), letterSpacing = 1.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onSavedCredentials,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SignInTestTags.SavedCredentialsButton),
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                    border = androidx.compose.foundation.BorderStroke(1.dp, outlineColor)
                ) {
                    Text(
                        text = stringResource(R.string.sign_in_use_saved).uppercase(Locale.getDefault()),
                        letterSpacing = 1.sp
                    )
                }

                AnimatedVisibility(visible = telegramEnabled) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onTelegram,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                            border = androidx.compose.foundation.BorderStroke(1.dp, outlineColor)
                        ) {
                            Text(
                                text = stringResource(R.string.sign_in_continue_telegram).uppercase(Locale.getDefault()),
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onToggleMode,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.testTag(SignInTestTags.ToggleModeButton),
                    colors = ButtonDefaults.textButtonColors(contentColor = accentGreen)
                ) {
                    val toggleLabel = if (uiState.mode == SignInViewModel.Mode.Register) {
                        stringResource(R.string.sign_in_mode_sign_in)
                    } else {
                        stringResource(R.string.sign_in_mode_register)
                    }
                    Text(
                        text = toggleLabel.uppercase(Locale.getDefault()),
                        letterSpacing = 1.sp
                    )
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = accentGreen
                )
            }
        }
    }
}
