package com.example.rise.ui.mainActivity

import android.content.Intent
import android.os.Bundle
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.rise.R
import com.example.rise.baseclasses.BaseActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : BaseActivity<MainActivityViewModel>() {
    private val RC_SIGN_IN = 9001

    override val viewModelClass = MainActivityViewModel::class

    public override fun onStart() {
        super.onStart()
        viewModel.requestSignInIfNeeded()?.let { intent ->
            startActivityForResult(intent, RC_SIGN_IN)
        }
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
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            viewModel.requestRetrySignIn(resultCode == RESULT_OK)?.let { intent ->
                startActivityForResult(intent, RC_SIGN_IN)
            }
        }
    }
}
