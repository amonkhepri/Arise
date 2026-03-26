package com.example.rise.data.people

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class IdentityBackfillStatusTracker(
    private val dataStore: DataStore<Preferences>,
) {

    suspend fun isComplete(userId: String?): Boolean {
        if (userId.isNullOrEmpty()) return false
        return dataStore.data
            .map { prefs -> prefs[KEY_COMPLETED_USERS]?.contains(userId) ?: false }
            .first()
    }

    suspend fun markComplete(userId: String) {
        dataStore.edit { prefs ->
            val existing = prefs[KEY_COMPLETED_USERS] ?: emptySet()
            prefs[KEY_COMPLETED_USERS] = existing + userId
        }
    }

    suspend fun resetForTests() {
        dataStore.edit { prefs -> prefs.remove(KEY_COMPLETED_USERS) }
    }

    companion object {
        private val KEY_COMPLETED_USERS =
            stringSetPreferencesKey("identity_backfill_completed_users")
    }
}
