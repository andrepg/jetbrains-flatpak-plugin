package io.github.andrepg.shared.license

import io.github.andrepg.shared.Localization

class UnsignedCertificateException : Exception(
    Localization.message("licensing.errors.unsigned_notification")
) {
}