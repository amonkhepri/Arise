package com.example.rise.featureflags

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreTransportModeProviderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>

    private fun setUpDataStore(): DataStoreTransportModeProvider {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val file = File(temporaryFolder.newFolder(), "transport_mode.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        return DataStoreTransportModeProvider(dataStore)
    }

    @After
    fun tearDown() {
        if (this::scope.isInitialized) {
            scope.cancel()
        }
    }

    @Test
    fun `defaults to Firestore when no value stored`() = runBlocking {
        val provider = setUpDataStore()
        try {
            val mode = provider.getMode()
            assertEquals(BriarTransportMode.FIRESTORE, mode)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `setMode persists new value`() = runBlocking {
        val provider = setUpDataStore()
        try {
            provider.setMode(BriarTransportMode.HYBRID)

            val persisted = provider.getMode()
            assertEquals(BriarTransportMode.HYBRID, persisted)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `observeMode emits distinct changes`() = runBlocking {
        val provider = setUpDataStore()
        val observed = mutableListOf<BriarTransportMode>()
        try {
            val job = launch {
                provider.observeMode().take(2).collect { mode ->
                    observed.add(mode)
                }
            }

            // ensure the collector is active before we update the flag
            yield()
            provider.setMode(BriarTransportMode.BRIAR_ONLY)
            yield()
            job.join()

            assertEquals(
                listOf(BriarTransportMode.FIRESTORE, BriarTransportMode.BRIAR_ONLY),
                observed
            )
        } finally {
            scope.cancel()
        }
    }
}
