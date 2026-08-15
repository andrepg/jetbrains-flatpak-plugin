package io.github.andrepg.shared.license

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.ui.LicensingFacade
import io.github.andrepg.shared.log.Log
import java.nio.charset.StandardCharsets
import java.security.Signature
import java.util.Base64

/**
 * Verifies the JetBrains Marketplace license for this plugin.
 *
 * Kotlin port of JetBrains' reference `CheckLicense` implementation:
 * https://github.com/JetBrains/marketplace-makemecoffee-plugin
 *
 * All licensing communication happens on the IntelliJ Platform side (the IDE
 * checks the license on startup and at least once a day); this class only
 * verifies the signed confirmation stamp exposed through [LicensingFacade].
 *
 * [PRODUCT_CODE] must match the `<product-descriptor code="...">` attribute in
 * `plugin.xml`.
 */
object LicenseCheck {
    val certificateGenerator = CertificateGenerator()

    const val PRODUCT_CODE = "PFLATPAKDEV"

    private val log = Log.getInstance(LicenseCheck::class.java)

    private const val KEY_PREFIX = "key:"
    private const val STAMP_PREFIX = "stamp:"

    /**
     * @return `true` if licensed, `false` if not. `null` means the
     * [LicensingFacade] is not initialized yet, so a definitive answer is not
     * possible — the caller decides how to interpret it.
     */
    fun isLicensed(): Boolean? {
        val facade = LicensingFacade.getInstance() ?: return null
        val stamp = facade.getConfirmationStamp(PRODUCT_CODE) ?: return false
        val licensed =
            when {
                stamp.startsWith(KEY_PREFIX) -> isKeyValid(stamp.removePrefix(KEY_PREFIX))
                stamp.startsWith(STAMP_PREFIX) -> isLicenseServerStampValid(stamp.removePrefix(STAMP_PREFIX))
                else -> false
            }
        log.debug("License check for $PRODUCT_CODE: ${if (licensed) "licensed" else "not licensed"}")
        return licensed
    }

    /**
     * Opens the JetBrains license registration dialog with the plugin product
     * pre-selected and [message] explaining why it was shown.
     */
    fun requestLicense(message: String) {
        ApplicationManager.getApplication().invokeLater(
            { showRegisterDialog(message) },
            ModalityState.nonModal(),
        )
    }

    private fun showRegisterDialog(message: String) {
        val actionManager = ActionManager.getInstance()
        // first, assume we are running inside the OpenSource version
        val registerAction =
            actionManager.getAction("RegisterPlugins")
                // assume running inside commercial IDE distribution
                ?: actionManager.getAction("Register")
        if (registerAction != null) {
            ActionUtil.performAction(
                registerAction,
                AnActionEvent.createEvent(asDataContext(message), Presentation(), "", ActionUiKind.NONE, null),
            )
        }
    }

    /**
     * Provides the additional data the "Register*" actions expect in the
     * [DataContext]: the product code to pre-select in the registration dialog
     * and an optional message explaining why the dialog has been shown.
     */
    private fun asDataContext(message: String): DataContext =
        SimpleDataContext
            .builder()
            .add(DataKey.create("register.product-descriptor.code"), PRODUCT_CODE)
            .add(DataKey.create("register.message"), message)
            .build()

    private fun isKeyValid(key: String): Boolean {
        val licenseParts = key.split("-")
        if (licenseParts.size != 4) {
            return false // invalid format
        }
        val licenseId = licenseParts[0]
        val licensePartBase64 = licenseParts[1]
        val signatureBase64 = licenseParts[2]
        val certBase64 = licenseParts[3]
        return try {
            val signature = Signature.getInstance("SHA1withRSA")
            // The last parameter switches off certificate expiration checks. This might be the
            // case if the key is a perpetual fallback license for older IDE versions. Here it is
            // only important that the key was signed with an authentic JetBrains certificate.
            signature.initVerify(
                certificateGenerator.createCertificate(
                    Base64.getMimeDecoder().decode(certBase64.toByteArray(StandardCharsets.UTF_8)),
                    emptyList<ByteArray>(),
                    false,
                ),
            )
            val licenseBytes = Base64.getMimeDecoder().decode(licensePartBase64.toByteArray(StandardCharsets.UTF_8))
            signature.update(licenseBytes)
            if (!signature.verify(Base64.getMimeDecoder().decode(signatureBase64.toByteArray(StandardCharsets.UTF_8)))) {
                return false
            }
            // Optional additional check: the licenseId corresponds to the licenseId encoded in the
            // signed license data. A 'least-effort' check; a stricter one would parse the JSON.
            val licenseData = String(licenseBytes, StandardCharsets.UTF_8)
            licenseData.contains("\"licenseId\":\"$licenseId\"")
        } catch (e: Throwable) {
            e.printStackTrace() // For debug purposes only
            false
        }
    }

    private fun isLicenseServerStampValid(serverStamp: String): Boolean =
        try {
            val parts = serverStamp.split(":")
            val decoder = Base64.getMimeDecoder()

            val expectedMachineId = parts[0]
            val timeStamp = parts[1].toLong()
            val machineId = parts[2]
            val signatureType = parts[3]
            val signatureBytes = decoder.decode(parts[4].toByteArray(StandardCharsets.UTF_8))
            val certBytes = decoder.decode(parts[5].toByteArray(StandardCharsets.UTF_8))
            val intermediate = ArrayList<ByteArray>()
            for (idx in 6 until parts.size) {
                intermediate.add(decoder.decode(parts[idx].toByteArray(StandardCharsets.UTF_8)))
            }

            val signature = Signature.getInstance(signatureType)
            // The last parameter set to 'true' causes the certificate to be checked for
            // expiration. Expired certificates from a license server cannot be trusted.
            signature.initVerify(certificateGenerator.createCertificate(certBytes, intermediate, true))

            signature.update("$timeStamp:$machineId".toByteArray(StandardCharsets.UTF_8))
            if (signature.verify(signatureBytes)) {
                // machineId must match the machineId from the server reply and
                // the server reply should be relatively 'fresh'
                expectedMachineId == machineId &&
                    kotlin.math.abs(System.currentTimeMillis() - timeStamp) < LicenseParameters.TIMESTAMP_VALIDITY_PERIOD
            } else {
                false
            }
        } catch (_: Throwable) {
            // consider the server stamp invalid
            false
        }
}
