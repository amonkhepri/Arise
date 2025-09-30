package com.example.rise.ui.mainActivity

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.rise.R
import com.example.rise.baseclasses.BaseActivity
import com.example.rise.ui.mainActivity.MainActivityViewModel.MainActivityEvent
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.flow.collect

class MainActivity : BaseActivity<MainActivityViewModel>() {

    override val viewModelClass = MainActivityViewModel::class

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onSignInResult(result.resultCode)
    }

    public override fun onStart() {
        super.onStart()
        viewModel.onStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navView: BottomNavigationView = findViewById(R.id.bottomNavigation)
        val navController = findNavController(R.id.nav_host_fragment)

        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_account, R.id.navigation_dashboard, R.id.navigation_people)
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        lifecycleScope.launchWhenStarted {
            viewModel.events.collect { event ->
                when (event) {
                    is MainActivityEvent.LaunchSignIn -> signInLauncher.launch(event.intent)
                }
            }
        }
    }
}
