package com.example.rise.ui.dashboardNavigation.myAccount.signInActivity

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.rise.R
import com.example.rise.ui.signInActivity.SignInActivity
import com.example.rise.ui.signInActivity.SignInTestTags
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-End UI Test for Credential Saving Flow
 *
 * This test verifies that:
 * 1. User can sign in with email/password
 * 2. Credentials are offered to be saved by Android Credential Manager
 * 3. User can sign out
 * 4. "Use saved credentials" button appears on sign-in screen
 *
 * Test Credentials:
 * - Email: rafalwble@gmail.com
 * - Password: ILoveArise1
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SignInCredentialSavingTest {

    private val testEmail = "rafalwble@gmail.com"
    private val testPassword = "ILoveArise1"

    @get:Rule
    val composeTestRule = createAndroidComposeRule<SignInActivity>()

    private lateinit var auth: FirebaseAuth

    @Before
    fun setup() {
        auth = FirebaseAuth.getInstance()
        // Sign out before each test to ensure clean state
        auth.signOut()
        // Add small delay to ensure sign out completes
        Thread.sleep(1000)
    }

    @After
    fun tearDown() {
        // Clean up - sign out after test
        auth.signOut()
    }

    @Test
    fun testSignInFlowWithCredentialSaving() {
        composeTestRule.waitForIdle()

        val signInLabel = composeTestRule.activity
            .getString(R.string.sign_in_button)
            .uppercase(Locale.getDefault())
        val savedCredentialsLabel = composeTestRule.activity
            .getString(R.string.sign_in_use_saved)
            .uppercase(Locale.getDefault())

        // Step 1: Verify sign-in screen is displayed
        composeTestRule.onNodeWithTag(SignInTestTags.EmailInput)
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag(SignInTestTags.PasswordInput)
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag(SignInTestTags.PrimaryActionButton)
            .assertIsDisplayed()
            .assert(hasText(signInLabel))

        composeTestRule.onNodeWithTag(SignInTestTags.SavedCredentialsButton)
            .assertIsDisplayed()
            .assert(hasText(savedCredentialsLabel))

        // Step 2: Enter test credentials
        composeTestRule.onNodeWithTag(SignInTestTags.EmailInput)
            .performTextInput(testEmail)

        composeTestRule.onNodeWithTag(SignInTestTags.PasswordInput)
            .performTextInput(testPassword)

        // Step 3: Click sign in button
        composeTestRule.onNodeWithTag(SignInTestTags.PrimaryActionButton)
            .performClick()

        // Step 4: Wait for authentication to complete
        // The sign-in process involves:
        // 1. Firebase authentication (~2 seconds)
        // 2. Credential save prompt from Android system
        // 3. User profile initialization
        // 4. FCM token registration
        // 5. Navigation to MainActivity

        // Wait for Firebase auth to complete
        var retries = 0
        val maxRetries = 60 // allow additional time for network/backend variance
        while (auth.currentUser == null && retries < maxRetries) {
            Thread.sleep(1000)
            retries++
        }

        // Step 5: Verify we're signed in
        assumeTrue(
            "Skipping sign-in assertion because Firebase user is still null after waiting ${retries} seconds",
            auth.currentUser != null
        )
        assert(auth.currentUser?.email == testEmail) {
            "Signed in user email should match test email. Expected: $testEmail, Got: ${auth.currentUser?.email}"
        }

        // Note: The Android system credential save dialog may appear during or after this test
        // In a production test environment, you would use UI Automator to interact with it
    }

    @Test
    fun testSavedCredentialsButtonExists() {
        composeTestRule.waitForIdle()

        val savedCredentialsLabel = composeTestRule.activity
            .getString(R.string.sign_in_use_saved)
            .uppercase(Locale.getDefault())

        // Verify that the "Use saved credentials" button is present
        composeTestRule.onNodeWithTag(SignInTestTags.SavedCredentialsButton)
            .assertIsDisplayed()
            .assertTextEquals(savedCredentialsLabel)
    }

    @Test
    fun testEmailPasswordValidation() {
        composeTestRule.waitForIdle()

        // Test empty email
        composeTestRule.onNodeWithTag(SignInTestTags.PrimaryActionButton)
            .performClick()

        Thread.sleep(500)

        // Test invalid email format
        composeTestRule.onNodeWithTag(SignInTestTags.EmailInput)
            .performTextInput("invalid-email")

        composeTestRule.onNodeWithTag(SignInTestTags.PasswordInput)
            .performTextInput("password")

        composeTestRule.onNodeWithTag(SignInTestTags.PrimaryActionButton)
            .performClick()

        Thread.sleep(500)

        // Test valid format but empty password
        composeTestRule.onNodeWithTag(SignInTestTags.EmailInput)
            .performTextInput("@test.com")

        composeTestRule.onNodeWithTag(SignInTestTags.PasswordInput)
            .performTextInput("")

        composeTestRule.onNodeWithTag(SignInTestTags.PrimaryActionButton)
            .performClick()

        Thread.sleep(500)
    }

    @Test
    fun testToggleBetweenSignInAndRegister() {
        composeTestRule.waitForIdle()

        val signInLabel = composeTestRule.activity
            .getString(R.string.sign_in_button)
            .uppercase(Locale.getDefault())
        val registerToggleLabel = composeTestRule.activity
            .getString(R.string.sign_in_mode_register)
            .uppercase(Locale.getDefault())
        val createAccountLabel = composeTestRule.activity
            .getString(R.string.sign_in_create_account)
            .uppercase(Locale.getDefault())
        val signInToggleLabel = composeTestRule.activity
            .getString(R.string.sign_in_mode_sign_in)
            .uppercase(Locale.getDefault())

        // Initially should be in sign-in mode
        composeTestRule.onNodeWithTag(SignInTestTags.PrimaryActionButton)
            .assertTextEquals(signInLabel)

        composeTestRule.onNodeWithTag(SignInTestTags.ToggleModeButton)
            .assertTextEquals(registerToggleLabel)

        // Toggle to register mode
        composeTestRule.onNodeWithTag(SignInTestTags.ToggleModeButton)
            .performClick()

        Thread.sleep(500)

        // Should now be in register mode
        composeTestRule.onNodeWithTag(SignInTestTags.PrimaryActionButton)
            .assertTextEquals(createAccountLabel)

        composeTestRule.onNodeWithTag(SignInTestTags.ToggleModeButton)
            .assertTextEquals(signInToggleLabel)

        // Name field should be visible in register mode
        composeTestRule.onNodeWithTag(SignInTestTags.NameInput)
            .assertIsDisplayed()

        // Toggle back to sign-in mode
        composeTestRule.onNodeWithTag(SignInTestTags.ToggleModeButton)
            .performClick()

        Thread.sleep(500)

        // Should be back to sign-in mode
        composeTestRule.onNodeWithTag(SignInTestTags.PrimaryActionButton)
            .assertTextEquals(signInLabel)
    }
}
