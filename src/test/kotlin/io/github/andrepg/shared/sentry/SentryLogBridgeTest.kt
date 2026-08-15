package io.github.andrepg.shared.sentry

import io.sentry.Sentry
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.util.logging.Level

class SentryLogBridgeTest {
    @Test
    fun `bridge is a no-op when the sentry client is not initialized`() {
        assumeFalse(
            "Sentry is already running in this JVM; this test only covers the uninitialized client",
            Sentry.isEnabled(),
        )

        val bridge = SentryLogBridge()
        // None of these may throw or touch the network while the client is uninitialized.
        bridge.onLog("test.category", Level.SEVERE, "boom", RuntimeException("expected"))
        bridge.onLog("test.category", Level.SEVERE, "plain error", null)
        bridge.onLog("test.category", Level.WARNING, "careful", null)
        bridge.onLog("test.category", Level.INFO, "noise", null)
        bridge.onLog("test.category", Level.FINE, "verbose noise", null)
    }

    @Test
    fun `bridge swallows listener failure from a broken sentry state`() {
        assumeFalse("Sentry is already running in this JVM", Sentry.isEnabled())

        val bridge = SentryLogBridge()
        assertFalse(Sentry.isEnabled())
        // Calling the bridge repeatedly must stay stable and silent.
        repeat(3) { bridge.onLog("test.category", Level.SEVERE, "boom", IllegalStateException("expected")) }
    }
}
