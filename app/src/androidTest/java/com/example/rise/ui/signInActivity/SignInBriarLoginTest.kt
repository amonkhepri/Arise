package com.example.rise.ui.signInActivity

import android.util.Log
import android.app.Activity
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.example.rise.data.auth.BriarAccountRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@LargeTest
@RunWith(AndroidJUnit4::class)
class SignInBriarLoginTest : KoinComponent {

    @get:Rule
    val composeRule = createAndroidComposeRule<SignInActivity>()

    private val briarRepo: BriarAccountRepository by inject()

    @Before
    fun seedAccount() {
        runBlocking {
            // Ensure the target Briar account exists before running the sign-in flow.
            runCatching { briarRepo.createAccount("hackerman", "123456") }
        }
    }

    @Test
    fun signInWithBriarCredentials_emitsLogs() {
        Log.i(TAG, "Starting Briar sign-in UI test")

        val nameNode = composeRule.onNodeWithTag(SignInTestTags.NameInput)
        nameNode.performTextClearance()
        nameNode.performTextInput("hackerman")
        Log.i(TAG, "Entered nickname")

        val emailNode = composeRule.onNodeWithTag(SignInTestTags.EmailInput)
        emailNode.performTextClearance()
        Log.i(TAG, "Cleared email (Briar-only)")

        val passwordNode = composeRule.onNodeWithTag(SignInTestTags.PasswordInput)
        passwordNode.performTextClearance()
        passwordNode.performTextInput("123456")
        Log.i(TAG, "Entered password")

        composeRule.onNodeWithTag(SignInTestTags.PrimaryActionButton)
            .performClick()
        Log.i(TAG, "Clicked primary action (sign-in)")

        var navigated = false
        runCatching {
            composeRule.waitUntil(timeoutMillis = 10_000) {
                val isMain = currentActivity() is com.example.rise.ui.mainActivity.MainActivity
                navigated = navigated || isMain
                isMain
            }
        }.onFailure {
            Log.w(TAG, "Navigation to MainActivity timed out: ${it.message}")
        }
        Log.i(TAG, "Finished Briar sign-in UI test. Navigated=$navigated")
        assert(navigated) { "Expected to navigate to MainActivity after sign-in" }
    }

    @Test
    fun signInWithWrongNickname_doesNotNavigate() {
        Log.i(TAG, "Starting Briar negative sign-in test (wrong nickname)")

        val nameNode = composeRule.onNodeWithTag(SignInTestTags.NameInput)
        nameNode.performTextClearance()
        nameNode.performTextInput("intruder")
        Log.i(TAG, "Entered wrong nickname")

        val emailNode = composeRule.onNodeWithTag(SignInTestTags.EmailInput)
        emailNode.performTextClearance()
        Log.i(TAG, "Cleared email (Briar-only)")

        val passwordNode = composeRule.onNodeWithTag(SignInTestTags.PasswordInput)
        passwordNode.performTextClearance()
        passwordNode.performTextInput("123456")
        Log.i(TAG, "Entered password")

        composeRule.onNodeWithTag(SignInTestTags.PrimaryActionButton)
            .performClick()
        Log.i(TAG, "Clicked primary action (sign-in)")

        var navigated = false
        runCatching {
            composeRule.waitUntil(timeoutMillis = 10_000) {
                val isMain = currentActivity() is com.example.rise.ui.mainActivity.MainActivity
                if (isMain) navigated = true
                isMain
            }
        }.onSuccess {
            Log.w(TAG, "Unexpectedly navigated to MainActivity with wrong nickname")
        }
        Log.i(TAG, "Finished negative sign-in test. Navigated=$navigated")
        assert(!navigated) { "Expected to stay on sign-in when nickname is wrong" }
    }

    companion object {
        private const val TAG = "SignInBriarLoginTest"
    }

    private fun currentActivity(): Activity? {
        var activity: Activity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val resumed = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
            activity = resumed.firstOrNull()
        }
        return activity
    }
}
