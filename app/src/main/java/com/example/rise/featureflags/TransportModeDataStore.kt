package com.example.rise.featureflags

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

private const val TRANSPORT_MODE_DATA_STORE = "transport_mode.preferences_pb"

val Context.transportModeDataStore by preferencesDataStore(
    name = TRANSPORT_MODE_DATA_STORE
)
