package io.github.andrepg.shared.sentry

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.github.andrepg.flatpak.settings.FlatpakGlobalSettingsState
import io.github.andrepg.shared.license.PremiumFeatureGate
import io.github.andrepg.shared.log.Log
import io.github.andrepg.shared.log.LogConfiguration

/**
 * Initializes the Sentry error-reporting client and attaches the
 * [SentryLogBridge].
 *
 * **Opt-in by default.** Reporting only starts when the user enables *Settings →
 * Flatpak → Diagnostics → Share anonymous error reports* or passes
 * `-Dflatpak.sentry.enabled=true`. Even then a DSN must be resolvable (see
 * [resolveDsn]); otherwise initialization is skipped with a warning.
 *
 * DSN resolution order:
 * 1. `-Dflatpak.sentry.dsn=<dsn>` system property
 * 2. `SENTRY_DSN` environment variable
 * 3. the [DSN] constant below — the production project DSN (SaaS, US region).
 *    DSNs are public by design (like a web app's), so committing it is fine.
 *
 * Privacy: the client never sends the hostname, default-PII is off, and
 * [scrub] removes any server/user identity that leaks into an event. Only
 * `io.github.andrepg` frames count as in-app.
 */
object SentryInitializer {

    private const val PLUGIN_ID = "io.github.andrepg.flatpak-support"
    private const val RELEASE_PREFIX = "flatpak-devtools@"

    /** System property that forces reporting on (`-Dflatpak.sentry.enabled=true`). */
    const val ENABLED_PROPERTY = "flatpak.sentry.enabled"

    /** System property with an explicit DSN (`-Dflatpak.sentry.dsn=...`). */
    const val DSN_PROPERTY = "flatpak.sentry.dsn"

    /** Production Sentry project DSN (SaaS, US region). Public by design. */
    private const val DSN = "https://c7a1ac249482359df5b7dd998b616857@o459069.ingest.us.sentry.io/4511909899337728"

    private val log = Log.getInstance(SentryInitializer::class.java)

    /** Whether a Sentry client is currently running (i.e. reporting works). */
    val isActive: Boolean get() = Sentry.isEnabled()

    /**
     * (Re)applies the current consent/DSN state. Safe to call repeatedly: shuts
     * down a running client, drops the [Log] bridge, then starts again if the
     * user enabled reporting. Called on application start and when the
     * Diagnostics settings are applied.
     */
    fun reconfigure() {
        if (Sentry.isEnabled()) {
            try {
                Sentry.close()
            } catch (e: Exception) {
                log.warn("Failed to shut down Sentry client", e)
            }
        }
        Log.listener = null
        initialize()
    }

    private fun initialize() {
        if (!isConsented()) {
            log.info(
                "Sentry error reporting disabled (opt-in; enable it in Settings -> Flatpak -> Diagnostics " +
                    "or with -D${ENABLED_PROPERTY}=true)"
            )
            return
        }

        val dsn = resolveDsn()
        if (dsn == null) {
            log.warn(
                "Sentry reporting requested but no DSN configured " +
                    "(set $DSN_PROPERTY, SENTRY_DSN, or fill SentryInitializer.DSN); reporting skipped"
            )
            return
        }

        try {
            Sentry.init(Sentry.OptionsConfiguration { options ->
                configure(options, dsn)
            })
        } catch (e: Exception) {
            log.error("Sentry initialization failed; error reporting disabled", e)
            return
        }

        configureScope()
        Log.listener = SentryLogBridge()
        log.info("Sentry error reporting enabled (environment=$environment, release=$release)")
    }

    private fun configure(options: SentryOptions, dsn: String) {
        options.dsn = dsn
        options.environment = environment
        options.release = release
        options.tracesSampleRate = 0.0
        options.isSendDefaultPii = false
        options.serverName = ""
        options.addInAppInclude(LogConfiguration.ROOT_CATEGORY)
        options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> scrub(event) }
    }

    private fun configureScope() {
        Sentry.configureScope { scope ->
            scope.setTag("plugin.id", PLUGIN_ID)
            scope.setTag("plugin.version", pluginVersion)
            scope.setTag("ide.version", ideVersion)
            scope.setTag("premium", PremiumFeatureGate.isPremiumAvailable().toString())
        }
    }

    /** Removes server/user identity from an event before it is sent. */
    private fun scrub(event: io.sentry.SentryEvent): io.sentry.SentryEvent {
        event.serverName = null
        event.user = null
        return event
    }

    private fun isConsented(): Boolean =
        System.getProperty(ENABLED_PROPERTY)?.toBoolean() == true ||
            service<FlatpakGlobalSettingsState>().sentryEnabled

    private fun resolveDsn(): String? =
        System.getProperty(DSN_PROPERTY)?.takeIf { it.isNotBlank() }
            ?: System.getenv("SENTRY_DSN")?.takeIf { it.isNotBlank() }
            ?: DSN.takeIf { it.isNotBlank() }

    private val environment: String
        get() = if (System.getProperty(PremiumFeatureGate.DEV_OVERRIDE_PROPERTY)?.toBoolean() == true) {
            "development"
        } else {
            "production"
        }

    private val release: String
        get() = RELEASE_PREFIX + pluginVersion

    private val pluginVersion: String
        get() = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))?.version ?: "dev"

    private val ideVersion: String
        get() = ApplicationInfo.getInstance().apiVersion
}
