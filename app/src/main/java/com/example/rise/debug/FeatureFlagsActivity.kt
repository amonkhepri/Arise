package com.example.rise.debug

import android.os.Bundle
import android.graphics.drawable.ColorDrawable
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rise.BuildConfig
import com.example.rise.R
import com.example.rise.baseclasses.BaseActivity
import com.example.rise.baseclasses.koinViewModelFactory
import com.example.rise.featureflags.BriarTransportMode
import kotlinx.coroutines.flow.collectLatest

class FeatureFlagsActivity : BaseActivity() {

    private val viewModel: FeatureFlagsViewModel by viewModels {
        koinViewModelFactory(FeatureFlagsViewModel::class)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val backgroundColor = ContextCompat.getColor(this, R.color.chatBackground)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        window.setBackgroundDrawable(ColorDrawable(backgroundColor))
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            isAppearanceLightStatusBars = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                isAppearanceLightNavigationBars = false
            }
        }
        if (!BuildConfig.DEBUG) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                val uiState by viewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    viewModel.events.collectLatest { event ->
                        when (event) {
                            is FeatureFlagsViewModel.Event.ShowMessageRes -> {
                                snackbarHostState.showSnackbar(
                                    message = getString(event.messageRes),
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    }
                }

                FeatureFlagsScreen(
                    uiState = uiState,
                    snackbarHostState = snackbarHostState,
                    onBack = { finish() },
                    onModeSelected = { viewModel.setMode(it) },
                    onTelegramToggle = { viewModel.setTelegramEnabled(it) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeatureFlagsScreen(
    uiState: FeatureFlagsViewModel.UiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onModeSelected: (BriarTransportMode) -> Unit,
    onTelegramToggle: (Boolean) -> Unit
) {
    val backgroundColor = colorResource(id = R.color.chatBackground)
    val textColor = colorResource(id = R.color.messageTextColor)
    val accentGreen = colorResource(id = R.color.matrixGreen)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = backgroundColor,
        contentColor = textColor,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    titleContentColor = textColor,
                    navigationIconContentColor = textColor
                ),
                title = { Text(text = stringResource(R.string.feature_flags_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = stringResource(id = R.string.feature_flags_title))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .background(backgroundColor),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = stringResource(R.string.feature_flags_transport_mode_label),
                style = MaterialTheme.typography.titleMedium,
                color = textColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.feature_flags_transport_mode_description),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(16.dp))

            BriarTransportMode.entries.forEach { mode ->
                val isSelected = uiState.mode == mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onModeSelected(mode) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = accentGreen,
                            unselectedColor = textColor
                        )
                    )
                    Text(
                        text = mode.toDisplayLabel(),
                        modifier = Modifier.padding(start = 8.dp),
                        color = textColor
                    )
                }
            }

            Text(
                text = stringResource(R.string.feature_flags_selected_value, uiState.mode.toDisplayLabel()),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.feature_flags_telegram_label),
                style = MaterialTheme.typography.titleMedium,
                color = textColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.feature_flags_telegram_description),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Switch(
                    checked = uiState.telegramAuthEnabled,
                    onCheckedChange = onTelegramToggle
                )
            }
        }
    }
}

@Composable
private fun BriarTransportMode.toDisplayLabel(): String {
    val labelRes = when (this) {
        BriarTransportMode.FIRESTORE -> R.string.mode_firestore
        BriarTransportMode.HYBRID -> R.string.mode_hybrid
        BriarTransportMode.BRIAR_ONLY -> R.string.mode_briar
    }
    return stringResource(labelRes)
}
