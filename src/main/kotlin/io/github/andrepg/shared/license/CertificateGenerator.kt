package io.github.andrepg.shared.license

import io.github.andrepg.shared.log.Log
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.cert.*

class CertificateGenerator {
    private val x509factory = CertificateFactory.getInstance("X.509");

    private fun generateCertificate(
        certificateInByteArray: ByteArray
    ): X509Certificate = x509factory.generateCertificate(
        ByteArrayInputStream(certificateInByteArray)
    ) as X509Certificate

    private fun generateCertificateStore(
        certificates: HashSet<Certificate>
    ) = CertStore.getInstance("Collection", CollectionCertStoreParameters(certificates))

    private fun generateCertBuilderPkix(pkixBuilderParameters: PKIXBuilderParameters) =
        CertPathBuilder.getInstance("PKIX").build(pkixBuilderParameters).certPath


    fun createCertificate(
        certificateInByteArray: ByteArray,
        intermediateCertificates: List<ByteArray>,
        checkAgainstCurrentDate: Boolean
    ): Certificate {
        val certificate = generateCertificate(certificateInByteArray)

        val allCertificates = HashSet<Certificate>().apply { this.add(certificate) }

        for (intermediate in intermediateCertificates) {
            allCertificates.add(generateCertificate(intermediate))
        }

        try {
            val certificateSelector = X509CertSelector()
            val trustAnchor = generateTrustChain()

            val pkixBuilderParameters = PKIXBuilderParameters(
                trustAnchor,
                certificateSelector
            )
            pkixBuilderParameters.isRevocationEnabled = false

            // If certificate expiration check is enabled we should add the
            // param to PKIX builder, so the validity check is expanded.
            if (!checkAgainstCurrentDate) pkixBuilderParameters.date = certificate.notBefore

            pkixBuilderParameters.addCertStore(generateCertificateStore(allCertificates))

            CertPathValidator.getInstance("PKIX").validate(
                generateCertBuilderPkix(pkixBuilderParameters),
                pkixBuilderParameters
            )

            return certificate
        } catch (exception: Exception) {
            Log.getInstance(CertificateGenerator::class.java).error(
                "Failed to generate and verify JetBrains certificate",
                exception
            )
        }

        throw UnsignedCertificateException()
    }

    private fun generateTrustChain(): HashSet<TrustAnchor> = HashSet<TrustAnchor>().apply {
        for (rootCerts in LicenseParameters.rootCertificates) {
            val byteArray = rootCerts.toByteArray(StandardCharsets.UTF_8)

            this.add(
                TrustAnchor(generateCertificate(byteArray), null)
            )
        }
    }
}