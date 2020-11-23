package com.example.rise.ui.dashboardNavigation.myAccount.signInActivity

import android.app.Activity
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.rise.R
import com.example.rise.baseclasses.BaseActivity
import com.example.rise.baseclasses.BaseViewModel
import com.example.rise.services.MyFirebaseMessagingService
import com.example.rise.ui.mainActivity.MainActivity
import com.example.rise.util.FirestoreUtil
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.ErrorCodes
import com.firebase.ui.auth.IdpResponse
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.iid.FirebaseInstanceId
import kotlinx.android.synthetic.main.activity_sign_in.*
import org.koin.android.ext.android.get

class SignInActivity : BaseActivity<SignInViewModel>() {

    private val RC_SIGN_IN = 1
    private val signInProviders = listOf(
                AuthUI.IdpConfig.EmailBuilder()
                    .setAllowNewAccounts(true)
                    .setRequireName(true)
                    .build()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)

        account_sign_in.setOnClickListener {
            val intent = AuthUI.getInstance().createSignInIntentBuilder()
                    .setAvailableProviders(signInProviders)
                    .setLogo(R.drawable.ic_fire_emoji)
                    .build()

            startActivityForResult(intent, RC_SIGN_IN)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val response = IdpResponse.fromResultIntent(data)

            if (resultCode == RESULT_OK) {
                val progressBar = ProgressBar(applicationContext, null, android.R.attr.progressBarStyleSmall)
                progressBar.stateDescription = "Setting up your account"
                //("Setting up your account")
                FirestoreUtil.initCurrentUserIfFirstTime {
                    val intent = Intent(this, MainActivity::class.java).addFlags(
                        FLAG_ACTIVITY_CLEAR_TASK or FLAG_ACTIVITY_NEW_TASK
                    )
                    startActivity(intent)

                    val registrationToken = FirebaseInstanceId.getInstance().token
                    MyFirebaseMessagingService.addTokenToFirestore(registrationToken)
                    progressBar.visibility = View.GONE
                }
            }

            else if (resultCode == Activity.RESULT_CANCELED) {
                if (response == null) return

                when (response.error?.errorCode) {
                    ErrorCodes.NO_NETWORK -> Snackbar.make (
                        constraint_layout,
                        "No network",
                        Snackbar.LENGTH_LONG
                    ).show()

                    ErrorCodes.UNKNOWN_ERROR -> Snackbar.make (
                        constraint_layout,
                        "Unknown error",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun createViewModel() {
        viewModel = get()
    }
}