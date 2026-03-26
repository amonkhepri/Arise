package com.example.rise.ui

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.rise.R
import com.example.rise.ui.mainActivity.MainActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test that opens the People tab and clicks the first conversation if one exists.
 * Useful for catching crashes that occur when entering a chat thread.
 */
@RunWith(AndroidJUnit4::class)
class ConversationClickSmokeTest {

    @Test
    fun clickFirstConversationIfPresent() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Switch to the People tab.
            onView(withId(R.id.navigation_people)).perform(click())

            // If there are items, click the first one to open a conversation.
            scenario.onActivity { activity ->
                val recycler = activity.findViewById<RecyclerView>(R.id.recycler_view_people)
                val itemCount = recycler.adapter?.itemCount ?: 0
                if (itemCount > 0) {
                    onView(withId(R.id.recycler_view_people))
                        .perform(
                            RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(
                                0,
                                click()
                            )
                        )
                }
            }
        }
    }
}
