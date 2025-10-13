package com.example.rise.featureflags

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber

class TelegramAuthFlagProvider(
    private val dataStore: DataStore<Preferences>
) {

    fun observeEnabled(): Flow<Boolean> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                Timber.tag(TAG).e(error, "Failed to read Telegram auth flag; defaulting to disabled.")
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences -> preferences[KEY] ?: false }
        .distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY] = enabled
        }
        Timber.tag(TAG).i("Telegram auth feature flag updated: %s", enabled)
    }

    companion object {
        private val KEY = booleanPreferencesKey("feature_telegram_auth_enabled")
        private const val TAG = "FeatureFlags"
    }
}
