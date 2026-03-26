package com.example.rise.ui.mainActivity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.rise.BuildConfig
import com.example.rise.R
import com.example.rise.baseclasses.BaseActivity
import com.example.rise.baseclasses.koinViewModelFactory
import com.example.rise.debug.FeatureFlagsActivity
import com.example.rise.transport.briar.invite.BriarInvitationAcceptanceUseCase
import com.example.rise.ui.mainActivity.MainActivityViewModel.MainActivityEvent
import com.example.rise.ui.signInActivity.SignInActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get

class MainActivity : BaseActivity() {

    private val viewModel: MainActivityViewModel by viewModels {
        koinViewModelFactory(MainActivityViewModel::class)
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private val briarInvitationOnboardingCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        BriarInvitationOnboardingCoordinator(get<BriarInvitationAcceptanceUseCase>())
    }
    private val briarInvitationOnboardingResultHandler by lazy(LazyThreadSafetyMode.NONE) {
        BriarInvitationOnboardingResultHandler(
            launchChat = ::startActivity,
            showMessage = { message ->
                Toast.makeText(
                    this,
                    message,
                    Toast.LENGTH_LONG,
                ).show()
            },
            markHandled = viewModel::onPendingInvitationOnboardingHandled,
            logInvalidInvitation = { reason ->
                Log.w(TAG, "Invalid Briar invitation onboarding action: $reason")
            },
            logInvitationFailure = { error ->
                Log.e(TAG, "Failed to accept Briar invitation", error)
            },
        )
    }
    private var pendingInvitationOnboardingJob: Job? = null
    private var pendingInvitationOnboardingActionInFlight: BriarInvitationOnboardingAction? = null
    private var authenticatedUiInflated = false

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onSignInResult(result.resultCode)
    }

    public override fun onStart() {
        super.onStart()
        viewModel.onStart()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        queueBriarInvitationOnboardingIntent(intent)
        maybeSelectDashboardFromIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        queueBriarInvitationOnboardingIntent(intent)
        renderUiForState(viewModel.uiState.value)

        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.events.collect { event ->
                when (event) {
                    MainActivityEvent.LaunchSignIn -> {
                        val intent = Intent(this@MainActivity, SignInActivity::class.java)
                        signInLauncher.launch(intent)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderUiForState(state)
                        maybeHandlePendingInvitationOnboarding(state)
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (BuildConfig.DEBUG) {
            menu.add(Menu.NONE, FEATURE_FLAGS_MENU_ID, Menu.NONE, getString(R.string.menu_feature_flags))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            return true
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (BuildConfig.DEBUG && item.itemId == FEATURE_FLAGS_MENU_ID) {
            startActivity(Intent(this, FeatureFlagsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment)
            ?.navController
        return if (navController != null) {
            NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp()
        } else {
            super.onSupportNavigateUp()
        }
    }

    private fun queueBriarInvitationOnboardingIntent(intent: Intent?) {
        val action = BriarInvitationOnboardingCoordinator.consumePendingAction(intent) ?: return
        viewModel.onPendingInvitationOnboarding(action)
    }

    private fun renderUiForState(state: MainActivityViewModel.MainActivityUiState) {
        if (state.shouldRenderAuthenticatedUi) {
            ensureAuthenticatedUiInflated()
        } else if (!authenticatedUiInflated) {
            setContentView(
                FrameLayout(this).apply {
                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                },
            )
        }
    }

    private fun ensureAuthenticatedUiInflated() {
        if (authenticatedUiInflated) {
            maybeSelectDashboardFromIntent(intent)
            return
        }

        authenticatedUiInflated = true
        setContentView(R.layout.activity_main)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val navView: BottomNavigationView = findViewById(R.id.bottomNavigation)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            as? NavHostFragment ?: NavHostFragment.create(R.navigation.mobile_navigation).also { host ->
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, host)
                .setPrimaryNavigationFragment(host)
                .commitNow()
        }

        val navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_account, R.id.navigation_dashboard, R.id.navigation_people)
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        maybeSelectDashboardFromIntent(intent)
    }

    private fun maybeSelectDashboardFromIntent(intent: Intent?) {
        if (!authenticatedUiInflated || intent?.hasExtra("UsrID") != true) return
        val navView: BottomNavigationView = findViewById(R.id.bottomNavigation)
        navView.selectedItemId = R.id.navigation_dashboard
    }

    private fun maybeHandlePendingInvitationOnboarding(
        state: MainActivityViewModel.MainActivityUiState,
    ) {
        val action = state.pendingInvitationOnboardingAction ?: return
        if (!state.isUserSignedIn) return
        if (pendingInvitationOnboardingJob?.isActive == true &&
            pendingInvitationOnboardingActionInFlight == action
        ) {
            return
        }

        pendingInvitationOnboardingJob?.cancel()
        pendingInvitationOnboardingActionInFlight = action
        pendingInvitationOnboardingJob = lifecycleScope.launch {
            try {
                val result = briarInvitationOnboardingCoordinator.accept(this@MainActivity, action)
                if (pendingInvitationOnboardingActionInFlight != action) return@launch
                pendingInvitationOnboardingActionInFlight = null
                briarInvitationOnboardingResultHandler.handle(result)
            } finally {
                if (pendingInvitationOnboardingActionInFlight == action) {
                    pendingInvitationOnboardingActionInFlight = null
                }
            }
        }
    }

    private companion object {
        private const val FEATURE_FLAGS_MENU_ID = 1001
        private const val TAG = "MainActivity"
    }
}
