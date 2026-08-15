package io.github.andrepg.shared.license

import io.github.andrepg.shared.log.Log

/**
 * Single point that decides whether premium (paid) features are available and
 * triggers the JetBrains Marketplace license/registration dialog when they are
 * not.
 *
 * A `null` license state (the licensing facade is not initialized yet) is
 * treated as locked: the safe default is to show the upgrade UI rather than
 * grant access to paid functionality.
 *
 * **Development is never blocked.** When the [DEV_OVERRIDE_PROPERTY] system
 * property is set (the `runIde` task does this automatically for the sandbox
 * IDE), premium features are unlocked without a license. Release builds never
 * set it; the property is a documented developer affordance only — and since
 * the source is open, building from source already grants premium access
 * anyway (an accepted trade-off of the open-source model, see BILLING.md).
 */
object PremiumFeatureGate {
    private val log = Log.getInstance(PremiumFeatureGate::class.java)

    const val DEV_OVERRIDE_PROPERTY = "flatpak.devtools.development"

    fun isPremiumAvailable(): Boolean {
        if (isDevelopmentBypass()) return true
        val licensed = LicenseCheck.isLicensed() == true
        log.debug("Premium gate (no development override): licensed=$licensed")
        return licensed
    }

    fun requestAccess(message: String) = LicenseCheck.requestLicense(message)

    private fun isDevelopmentBypass(): Boolean = System.getProperty(DEV_OVERRIDE_PROPERTY)?.toBoolean() == true
}
