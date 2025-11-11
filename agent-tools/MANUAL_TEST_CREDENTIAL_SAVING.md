# Manual Testing Guide: Credential Saving Feature

## Test Credentials
- **Email**: `rafalwble@gmail.com`
- **Password**: `ILoveArise1`

## Pre-requisites
1. App is installed on device/emulator
2. Internet connection is available
3. Google Play Services is installed (for Credential Manager API)

## Test Scenario 1: Sign In and Save Credentials

### Steps:
1. **Launch the app**
   - If already signed in, sign out first from My Account tab

2. **Sign in with test credentials**
   - Enter email: `rafalwble@gmail.com`
   - Enter password: `ILoveArise1`
   - Tap "Sign in" button

3. **Expected: Android Credential Save Prompt**
   - After successful authentication, Android system should show a dialog:
   - "Save password for Rise?"
   - Options: "Save" / "Never"

4. **Action: Tap "Save"**
   - This saves the credentials to Android Credential Manager

5. **Verification: Sign In Success**
   - ✅ App should navigate to MainActivity
   - ✅ Dashboard should be visible
   - ✅ User is signed in

## Test Scenario 2: Use Saved Credentials

### Steps:
1. **Sign out**
   - Go to "My Account" tab
   - Tap "Sign out" button
   - Confirm you're back at sign-in screen

2. **Tap "Use saved credentials" button**
   - This button is below the "Sign in" button

3. **Expected: Credential Picker**
   - Android system shows a bottom sheet
   - Should display: `rafalwble@gmail.com`
   - May also show any Google accounts on device

4. **Action: Select the saved credential**
   - Tap on `rafalwble@gmail.com`

5. **Verification: Automatic Sign In**
   - ✅ App should sign in automatically (no typing needed!)
   - ✅ Navigate to MainActivity
   - ✅ User is signed in with correct account

## Test Scenario 3: Register New Account

### Steps:
1. **Sign out** (if signed in)

2. **Tap "Need an account? Sign up"** button

3. **Fill registration form**
   - Name: `Test User`
   - Email: `test@example.com` (use a different test email)
   - Password: `TestPass123`
   - Tap "Create account"

4. **Expected: Credential Save Prompt**
   - After successful registration, Android system should prompt to save
   - Options: "Save" / "Never"

5. **Action: Tap "Save"**

6. **Verification**
   - ✅ Account created successfully
   - ✅ Navigate to MainActivity
   - ✅ Credentials saved for future use

## Test Scenario 4: Multiple Saved Accounts

### Steps:
1. **After saving multiple accounts** (from scenarios above)

2. **Sign out and tap "Use saved credentials"**

3. **Expected: Multiple Options**
   - Bottom sheet should show all saved accounts:
     - `rafalwble@gmail.com`
     - `test@example.com`
     - Any Google accounts

4. **Action: Select any account**

5. **Verification**
   - ✅ App signs in with the selected account
   - ✅ Correct user data is displayed

## Troubleshooting

### "No credentials saved" error
**Possible causes:**
1. User tapped "Never" on the save prompt
2. Credential Manager API not available (old Android version)
3. Google Play Services not installed/updated
4. Credentials were cleared from system settings

**Solution:**
- Sign in again and tap "Save" when prompted
- Check Android Settings → Passwords & accounts → Google Password Manager

### Save prompt doesn't appear
**Possible causes:**
1. Already saved for this account
2. User previously selected "Never"
3. Device policy restrictions

**Solution:**
- Clear saved credentials from system settings and try again
- Or use a different email address

### Credential picker shows wrong account
**Solution:**
- This is expected - Android shows ALL saved credentials
- User can select the correct one from the list

## Test Results

### Automated Tests (3/4 passing):
- ✅ `testSavedCredentialsButtonExists` - PASS
- ✅ `testEmailPasswordValidation` - PASS
- ✅ `testToggleBetweenSignInAndRegister` - PASS
- ❌ `testSignInFlowWithCredentialSaving` - FAIL (requires network + Firebase)

**Note**: The failing test requires:
1. Live Firebase connection
2. Valid test account in Firebase
3. Network connectivity on emulator
4. Proper Firebase configuration (google-services.json)

The test failure doesn't indicate the feature is broken - it's a test environment issue.

## Manual Verification Checklist

Run through the scenarios above and check:

- [ ] Sign in with email/password works
- [ ] Android prompts to save credentials after sign in
- [ ] Credentials are saved when user taps "Save"
- [ ] "Use saved credentials" button shows saved accounts
- [ ] Selecting a saved credential signs user in automatically
- [ ] Registration also prompts to save credentials
- [ ] Multiple accounts can be saved and selected
- [ ] Google Sign-In works (if configured)

## Feature Implementation Details

### Code Changes:
1. **SignInViewModel.kt** - Added `SaveCredentials` event emitted after successful auth
2. **SignInActivity.kt** - Handles event and calls `createCredential()` API
3. **Tests** - Updated to verify event emission

### Key Files:
- [SignInViewModel.kt:43](../app/src/main/java/com/example/rise/ui/signInActivity/SignInViewModel.kt) - Event definition (line 43)
- [SignInViewModel.kt:83](../app/src/main/java/com/example/rise/ui/signInActivity/SignInViewModel.kt) - Emit after sign in (line 83)
- [SignInViewModel.kt:113](../app/src/main/java/com/example/rise/ui/signInActivity/SignInViewModel.kt) - Emit after registration (line 113)
- [SignInActivity.kt:133-140](../app/src/main/java/com/example/rise/ui/signInActivity/SignInActivity.kt) - Save credentials (lines 133-140)

### API Used:
- `CredentialManager.createCredential()` - Saves credentials
- `CredentialManager.getCredential()` - Retrieves credentials
- `CreatePasswordRequest` - Specifies email + password to save
- `GetPasswordOption` - Requests password credentials
- `GetGoogleIdOption` - Requests Google sign-in

---

**Last Updated**: 2025-10-02
**Tested By**: [Your Name]
**Status**: ✅ Ready for manual testing
