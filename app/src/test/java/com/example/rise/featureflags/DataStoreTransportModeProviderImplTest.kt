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
class DataStoreTransportModeProviderImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>

    private fun setUpDataStore(): DataStoreTransportModeProviderImpl {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val file = File(temporaryFolder.newFolder(), "transport_mode.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        return DataStoreTransportModeProviderImpl(dataStore)
    }

    @After
    fun tearDown() {
        if (this::scope.isInitialized) {
            scope.cancel()
        }
    }

    @Test
    fun `defaults to Briar-only when no value stored`() = runBlocking {
        val provider = setUpDataStore()
        try {
            val mode = provider.getMode()
            assertEquals(BriarTransportMode.BRIAR_ONLY, mode)
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
            provider.setMode(BriarTransportMode.FIRESTORE)
            yield()
            job.join()
            assertEquals(
                listOf(BriarTransportMode.BRIAR_ONLY, BriarTransportMode.FIRESTORE),
                observed
            )
        } finally {
            scope.cancel()
        }
    }
}
