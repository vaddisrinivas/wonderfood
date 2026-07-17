package com.wonderfood.app.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class LiteLlmSettingsTest {
    private val testPrefsName = "litellm-test"
    private lateinit var context: Context
    private lateinit var settings: LiteLlmSettings

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(testPrefsName, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        settings = LiteLlmSettings(context, testPrefsName, "wonderfood_litellm_test_key")
    }

    @Test
    fun readAllRoundRobinRotatesStartingProvider() {
        settings.saveAll(
            listOf(
                LiteLlmConfig("https://one.example/v1", "key-one", "model-one"),
                LiteLlmConfig("https://two.example/v1", "key-two", "model-two"),
                LiteLlmConfig("https://three.example/v1", "key-three", "model-three"),
            ),
        )

        assertEquals(listOf("model-one", "model-two", "model-three"), settings.readAllRoundRobin().map { it.model })
        assertEquals(listOf("model-two", "model-three", "model-one"), settings.readAllRoundRobin().map { it.model })
        assertEquals(listOf("model-three", "model-one", "model-two"), settings.readAllRoundRobin().map { it.model })
        assertEquals(listOf("model-one", "model-two", "model-three"), settings.readAllRoundRobin().map { it.model })

        val rawPrefs = context.getSharedPreferences(testPrefsName, Context.MODE_PRIVATE).all.values.joinToString()
        assertFalse(rawPrefs.contains("key-one"))
        assertFalse(rawPrefs.contains("key-two"))
        assertFalse(rawPrefs.contains("key-three"))
    }
}
