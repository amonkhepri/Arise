package com.example.rise.data.people

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class IdentityBackfillStatusTrackerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>

    private fun createTracker(): IdentityBackfillStatusTracker {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val file = File(temporaryFolder.newFolder(), "identity_backfill.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        return IdentityBackfillStatusTracker(dataStore)
    }

    @After
    fun tearDown() {
        if (this::scope.isInitialized) {
            scope.cancel()
        }
    }

    @Test
    fun `markComplete only affects that user`() = runBlocking {
        val tracker = createTracker()

        assertFalse(tracker.isComplete("userA"))
        tracker.markComplete("userA")

        assertTrue(tracker.isComplete("userA"))
        assertFalse(tracker.isComplete("userB"))
    }

    @Test
    fun `resetForTests clears tracked users`() = runBlocking {
        val tracker = createTracker()

        tracker.markComplete("userA")
        assertTrue(tracker.isComplete("userA"))

        tracker.resetForTests()
        assertFalse(tracker.isComplete("userA"))
    }
}
