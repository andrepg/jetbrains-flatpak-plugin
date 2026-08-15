package io.github.andrepg.shared.log

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogConfigurationTest {

    @Test
    fun `enabling debug raises the namespace level so debug records are loggable`() {
        try {
            LogConfiguration.setDebugEnabled(true)
            assertTrue(LogConfiguration.isDebugActive())
            assertTrue(Log.getInstance(LogConfigurationTest::class.java).isDebugEnabled)
        } finally {
            LogConfiguration.setDebugEnabled(false)
        }
    }

    @Test
    fun `disabling debug restores the default level so debug records are filtered`() {
        LogConfiguration.setDebugEnabled(true)
        try {
            assertTrue(Log.getInstance(LogConfigurationTest::class.java).isDebugEnabled)
        } finally {
            LogConfiguration.setDebugEnabled(false)
        }
        assertFalse(LogConfiguration.isDebugActive())
        assertFalse(Log.getInstance(LogConfigurationTest::class.java).isDebugEnabled)
    }

    @Test
    fun `debug request is read from the flatpak debug property`() {
        val previous = System.getProperty(LogConfiguration.DEBUG_PROPERTY)
        try {
            System.clearProperty(LogConfiguration.DEBUG_PROPERTY)
            assertFalse(LogConfiguration.isDebugRequested())

            System.setProperty(LogConfiguration.DEBUG_PROPERTY, "true")
            assertTrue(LogConfiguration.isDebugRequested())
        } finally {
            if (previous == null) {
                System.clearProperty(LogConfiguration.DEBUG_PROPERTY)
            } else {
                System.setProperty(LogConfiguration.DEBUG_PROPERTY, previous)
            }
        }
    }
}
