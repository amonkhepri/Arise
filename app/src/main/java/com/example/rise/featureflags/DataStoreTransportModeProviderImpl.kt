package com.example.rise.featureflags

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

class DataStoreTransportModeProviderImpl(
    private val dataStore: DataStore<Preferences>
) : TransportModeProvider {

    override fun observeMode(): Flow<BriarTransportMode> {
        return dataStore.data
            .catch { error ->
                if (error is IOException) {
                    Timber.tag(TAG).e(error, "Failed to read transport mode; defaulting to Firestore.")
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .map { preferences ->
                val storedValue = preferences[TRANSPORT_MODE_KEY]
                BriarTransportMode.fromValue(storedValue)
            }
            .distinctUntilChanged()
    }

    override suspend fun setMode(mode: BriarTransportMode) {
        dataStore.edit { preferences ->
            preferences[TRANSPORT_MODE_KEY] = mode.ordinal
        }
        Timber.tag(TAG).i("Transport mode switched to %s", mode)
    }

    override suspend fun getMode(): BriarTransportMode {
        return observeMode().first()
    }

    private companion object {
        private val TRANSPORT_MODE_KEY = intPreferencesKey("briar_transport_mode")
        private const val TAG = "FeatureFlags"
    }
}
