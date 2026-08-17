package io.github.andrepg.shared.license

import io.github.andrepg.shared.Localization

/**
 * Thrown when a license certificate fails PKIX validation.
 *
 * Uses [Localization.message] instead of a hard-coded string because this
 * exception is only raised from [CertificateGenerator], which runs after the
 * IDE has fully initialized the message bundles. If the bundle is unavailable
 * (e.g. a headless bootstrap), the DynamicBundle falls back to the key itself,
 * which is still readable.
 */
class UnsignedCertificateException : Exception(
    Localization.message("licensing.errors.unsigned_notification"),
)
