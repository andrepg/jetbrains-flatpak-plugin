package io.github.andrepg.shared.sentry

import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.github.andrepg.shared.log.LogListener
import java.util.logging.Level

/**
 * Bridges the plugin's JDK-only [Log] facade into Sentry as breadcrumbs and
 * error events.
 *
 * Attached as [io.github.andrepg.shared.log.Log.listener] by
 * [SentryInitializer] when reporting is consented. The bridge is deliberately
 * small and defensive: Sentry's own SDK is a no-op before `Sentry.init`, so this
 * must never throw or block the logging caller.
 *
 * Mapping:
 * - `SEVERE` + throwable -> `captureException` (with a breadcrumb for context)
 * - `SEVERE` without throwable -> `captureMessage(ERROR)`
 * - `WARNING` -> warning breadcrumb (context for later failures)
 * - INFO/DEBUG -> ignored (would flood the quota)
 */
class SentryLogBridge : LogListener {

    override fun onLog(category: String, level: Level, message: String, throwable: Throwable?) {
        if (!Sentry.isEnabled()) return
        when (level) {
            Level.SEVERE -> {
                addBreadcrumb(SentryLevel.ERROR, category, message)
                if (throwable != null) {
                    Sentry.captureException(throwable)
                } else {
                    Sentry.captureMessage(message, SentryLevel.ERROR)
                }
            }

            Level.WARNING -> addBreadcrumb(SentryLevel.WARNING, category, message)
            else -> Unit
        }
    }

    private fun addBreadcrumb(level: SentryLevel, category: String, message: String) {
        val breadcrumb = Breadcrumb().apply {
            this.level = level
            this.category = category
            this.message = message
        }
        Sentry.addBreadcrumb(breadcrumb)
    }
}
