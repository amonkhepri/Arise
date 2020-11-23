package com.example.rise.ui.mainActivity


import android.content.Intent
import android.os.Bundle
import android.view.ActionMode
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation.findNavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.rise.R
import com.example.rise.baseclasses.BaseActivity
import com.example.rise.ui.dashboardNavigation.dashboard.DashboardFragment
import com.example.rise.ui.dashboardNavigation.myAccount.MyAccountFragment
import com.example.rise.ui.dashboardNavigation.people.peopleFragment.PeopleFragment
import com.firebase.ui.auth.AuthUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.android.synthetic.main.activity_main.*
import org.koin.android.ext.android.get
import org.koin.dsl.koinApplication


class MainActivity: BaseActivity<MainActivityViewModel>() {
    private val RC_SIGN_IN = 9001

    public override fun onStart() {
        super.onStart()
        // Start sign in if necessary
        if (shouldStartSignIn()) {
            startSignIn()
            return
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        viewModel = ViewModelProviders.of(this).get(MainActivityViewModel::class.java)

        val navView: BottomNavigationView = findViewById(R.id.bottomNavigation)
        val navController = findNavController(R.id.nav_host_fragment)

        val appBarConfiguration = AppBarConfiguration(setOf(R.id.navigation_account, R.id.navigation_dashboard, R.id.navigation_people))
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
            viewModel.mSignIn = false

            if (resultCode != RESULT_OK && shouldStartSignIn()) {
                startSignIn()
            }
        }
    }

    private fun startSignIn() {
        // Sign in with FirebaseUI
        val intent = AuthUI.getInstance().createSignInIntentBuilder()
            .setAvailableProviders(
                listOf<AuthUI.IdpConfig>(
                    AuthUI.IdpConfig.EmailBuilder().build()
                )
            )
            .setIsSmartLockEnabled(false)
            .build()

        startActivityForResult(intent, RC_SIGN_IN)
        viewModel.mSignIn = true
    }

    private fun shouldStartSignIn(): Boolean {
        return !viewModel.mSignIn && FirebaseAuth.getInstance().currentUser == null
    }

    override fun createViewModel() {
        viewModel = get()
        viewModel.mSignIn = true
    }
}