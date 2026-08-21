package io.github.andrepg.shared.sentry

import org.junit.Assert.assertTrue
import org.junit.Test

class SentryGuardTest {
    @Test
    fun `swallows missing sentry sdk linkage errors`() {
        var continued = false

        SentryGuard.run("Sentry initialization") {
            throw NoClassDefFoundError("io/sentry/Sentry")
        }

        continued = true
        assertTrue("guard must not propagate the error to the caller", continued)
    }

    @Test
    fun `runs normal blocks without interference`() {
        var ran = false

        SentryGuard.run("Sentry initialization") {
            ran = true
        }

        assertTrue(ran)
    }

    @Test
    fun `block failure does not prevent subsequent code from running`() {
        var after = false

        SentryGuard.run("Sentry initialization") {
            error("simulated sdk crash")
        }
        after = true

        assertTrue("execution must resume after a guarded failure", after)
    }
}
