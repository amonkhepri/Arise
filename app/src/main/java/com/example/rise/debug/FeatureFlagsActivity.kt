package com.example.rise.debug

import android.os.Bundle
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rise.BuildConfig
import com.example.rise.R
import com.example.rise.baseclasses.BaseActivity
import com.example.rise.baseclasses.koinViewModelFactory
import com.example.rise.featureflags.BriarTransportMode
import com.example.rise.transport.router.ConnectorHealth
import com.example.rise.transport.router.ConnectorLifecycleState
import com.example.rise.transport.router.PrimaryRoutingReason
import com.example.rise.transport.router.PrimaryRoutingSnapshot
import com.example.rise.transport.router.PrimarySelectionTrigger
import com.example.rise.transport.router.TransportId
import kotlinx.coroutines.flow.collectLatest
import java.text.DateFormat
import java.util.*

class FeatureFlagsActivity : BaseActivity() {

    private val viewModel: FeatureFlagsViewModel by viewModels {
        koinViewModelFactory(FeatureFlagsViewModel::class)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val backgroundColor = ContextCompat.getColor(this, R.color.chatBackground)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        window.setBackgroundDrawable(backgroundColor.toDrawable())
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
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
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.feature_flags_title))
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

            uiState.routingSnapshot?.let { snapshot ->
                Spacer(modifier = Modifier.height(32.dp))
                RoutingStatusSection(
                    snapshot = snapshot,
                    textColor = textColor,
                )
            }

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

            Spacer(modifier = Modifier.height(32.dp))

            ConnectorHealthSection(
                health = uiState.connectorHealth,
                textColor = textColor,
            )
        }
    }
}

@Composable
private fun RoutingStatusSection(
    snapshot: PrimaryRoutingSnapshot,
    textColor: Color,
) {
    val timestamp = remember(snapshot.timestampMs) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
            .format(Date(snapshot.timestampMs))
    }
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.feature_flags_routing_label),
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.feature_flags_routing_description),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor.copy(alpha = 0.8f),
        )
        Spacer(modifier = Modifier.height(12.dp))
        RoutingRow(
            label = stringResource(R.string.feature_flags_routing_primary),
            value = snapshot.primary.toDisplayLabel(),
            textColor = textColor,
        )
        RoutingRow(
            label = stringResource(R.string.feature_flags_routing_preferred),
            value = snapshot.preferred.toDisplayLabel(),
            textColor = textColor,
        )
        snapshot.fallbackTarget?.let { fallback ->
            RoutingRow(
                label = stringResource(R.string.feature_flags_routing_fallback),
                value = fallback.toDisplayLabel(),
                textColor = textColor,
            )
        }
        RoutingRow(
            label = stringResource(R.string.feature_flags_routing_reason),
            value = snapshot.reason.toDisplayLabel(),
            textColor = textColor,
        )
        snapshot.preferredLifecycle?.let { lifecycle ->
            RoutingRow(
                label = stringResource(R.string.feature_flags_routing_lifecycle),
                value = lifecycle.toDisplayLabel(),
                textColor = textColor,
            )
        }
        RoutingRow(
            label = stringResource(R.string.feature_flags_routing_trigger),
            value = snapshot.trigger.toDisplayLabel(),
            textColor = textColor,
        )
        RoutingRow(
            label = stringResource(R.string.feature_flags_routing_timestamp),
            value = timestamp,
            textColor = textColor,
        )
    }
}

@Composable
private fun ConnectorHealthSection(
    health: List<ConnectorHealth>,
    textColor: Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.feature_flags_connector_health_title),
            style = MaterialTheme.typography.titleMedium,
            color = textColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (health.isEmpty()) {
            Text(
                text = stringResource(R.string.feature_flags_connector_health_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.7f)
            )
        } else {
            health.forEach { snapshot ->
                Text(
                    text = buildString {
                        append(snapshot.transport.name)
                        append(": ")
                        append(snapshot.lifecycle.name)
                        append(" (")
                        append(snapshot.status.name)
                        append(")")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RoutingRow(
    label: String,
    value: String,
    textColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.8f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
        )
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

@Composable
private fun TransportId.toDisplayLabel(): String {
    val labelRes = when (this) {
        TransportId.BRIAR -> R.string.transport_briar
        TransportId.FIRESTORE -> R.string.transport_firestore
        TransportId.TELEGRAM -> R.string.transport_telegram
    }
    return stringResource(labelRes)
}

@Composable
private fun PrimaryRoutingReason.toDisplayLabel(): String {
    val labelRes = when (this) {
        PrimaryRoutingReason.PreferredReady -> R.string.routing_reason_preferred_ready
        PrimaryRoutingReason.PreferredNotReady -> R.string.routing_reason_preferred_not_ready
        PrimaryRoutingReason.PreferredMissing -> R.string.routing_reason_preferred_missing
        PrimaryRoutingReason.FlagForcesFirestore -> R.string.routing_reason_flag_forces_firestore
        PrimaryRoutingReason.ExplicitFallback -> R.string.routing_reason_explicit_fallback
        PrimaryRoutingReason.Initial -> R.string.routing_reason_initial
    }
    return stringResource(labelRes)
}

@Composable
private fun ConnectorLifecycleState.toDisplayLabel(): String {
    val labelRes = when (this) {
        ConnectorLifecycleState.INITIAL -> R.string.lifecycle_initial
        ConnectorLifecycleState.AUTHENTICATING -> R.string.lifecycle_authenticating
        ConnectorLifecycleState.HANDSHAKING -> R.string.lifecycle_handshaking
        ConnectorLifecycleState.READY -> R.string.lifecycle_ready
        ConnectorLifecycleState.DEGRADED -> R.string.lifecycle_degraded
        ConnectorLifecycleState.FAILED -> R.string.lifecycle_failed
        ConnectorLifecycleState.RETIRING -> R.string.lifecycle_retiring
        ConnectorLifecycleState.STOPPED -> R.string.lifecycle_stopped
    }
    return stringResource(labelRes)
}

@Composable
private fun PrimarySelectionTrigger.toDisplayLabel(): String {
    val labelRes = when (this) {
        PrimarySelectionTrigger.INITIAL -> R.string.routing_trigger_initial
        PrimarySelectionTrigger.MODE_CHANGED -> R.string.routing_trigger_mode_changed
        PrimarySelectionTrigger.LIFECYCLE_CHANGED -> R.string.routing_trigger_lifecycle_changed
        PrimarySelectionTrigger.EXPLICIT_FALLBACK -> R.string.routing_trigger_explicit
    }
    return stringResource(labelRes)
}
