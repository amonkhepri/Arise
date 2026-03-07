package com.example.rise.ui.mainActivity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
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
import com.example.rise.R
import com.example.rise.baseclasses.BaseActivity
import com.example.rise.baseclasses.koinViewModelFactory
import com.example.rise.debug.FeatureFlagsActivity
import com.example.rise.transport.briar.invite.BriarInvitationAcceptanceUseCase
import com.example.rise.ui.signInActivity.SignInActivity
import com.example.rise.ui.mainActivity.MainActivityViewModel.MainActivityEvent
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import com.example.rise.BuildConfig
import org.koin.android.ext.android.get

class MainActivity : BaseActivity() {

    private val viewModel: MainActivityViewModel by viewModels {
        koinViewModelFactory(MainActivityViewModel::class)
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private val briarInvitationOnboardingCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        BriarInvitationOnboardingCoordinator(get<BriarInvitationAcceptanceUseCase>())
    }

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
        // Handle scheduled message intent - navigate to dashboard if message data is present
        if (intent.hasExtra("UsrID")) {
            val navView: BottomNavigationView = findViewById(R.id.bottomNavigation)
            navView.selectedItemId = R.id.navigation_dashboard
        }
        handleBriarInvitationOnboardingIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        MainActivityEvent.LaunchSignIn -> {
                            val intent = Intent(this@MainActivity, SignInActivity::class.java)
                            signInLauncher.launch(intent)
                        }
                    }
                }
            }
        }

        // Handle scheduled message intent - navigate to dashboard if message data is present
        if (intent?.hasExtra("UsrID") == true) {
            val navView: BottomNavigationView = findViewById(R.id.bottomNavigation)
            navView.selectedItemId = R.id.navigation_dashboard
        }
        handleBriarInvitationOnboardingIntent(intent)
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

    private fun handleBriarInvitationOnboardingIntent(intent: Intent?) {
        val action = BriarInvitationOnboardingCoordinator.consumePendingAction(intent) ?: return
        lifecycleScope.launch {
            when (val result = briarInvitationOnboardingCoordinator.accept(this@MainActivity, action)) {
                is BriarInvitationOnboardingCoordinator.Result.LaunchChat -> {
                    startActivity(result.intent)
                }
                is BriarInvitationOnboardingCoordinator.Result.InvalidInvitation -> {
                    Log.w(TAG, "Invalid Briar invitation onboarding action: ${result.reason}")
                    Toast.makeText(
                        this@MainActivity,
                        "Invalid Briar invitation link",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                is BriarInvitationOnboardingCoordinator.Result.InvitationFailed -> {
                    Log.e(TAG, "Failed to accept Briar invitation", result.error)
                    Toast.makeText(
                        this@MainActivity,
                        "Unable to add Briar contact",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private companion object {
        private const val FEATURE_FLAGS_MENU_ID = 1001
        private const val TAG = "MainActivity"
    }
}
