package com.example.nearbychater

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nearbychater.core.logging.LogManager
import com.example.nearbychater.core.model.DiagnosticsEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogManagerInstrumentedTest {
    private lateinit var logManager: LogManager

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        logManager = LogManager(context)
        runBlocking { logManager.clearLogs() }
    }

    @Test
    fun logManagerWritesAndClears() = runBlocking {
        val event = DiagnosticsEvent(code = "test", message = "sample message")
        logManager.log(event)
        val logs = logManager.readLogs()
        assertTrue(logs.any { it.contains("sample message") })

        logManager.clearLogs()
        val empty = logManager.readLogs()
        assertEquals(0, empty.size)
    }
}
