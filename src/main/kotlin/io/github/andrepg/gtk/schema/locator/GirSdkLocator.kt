package io.github.andrepg.gtk.schema.locator

import io.github.andrepg.shared.log.Log
import io.github.andrepg.shared.process.FlatpakRuntimeRow
import io.github.andrepg.shared.process.ProcessRunner
import io.github.andrepg.shared.process.parseFlatpakRuntimeList
import java.io.File

/**
 * Locates the GObject Introspection (GIR) directory of a GNOME SDK installed
 * via Flatpak.
 *
 * Discovery runs through the flatpak CLI only (`flatpak list --runtime` picks
 * the branch, `flatpak info --show-location` resolves the install path); when
 * the CLI is unavailable or the SDK is not installed the caller serves no
 * schema. Only SDKs listed in [supportedSdks] are considered. JDK-only, no
 * IntelliJ imports.
 */
object GirSdkLocator {
    private val log = Log.getInstance(GirSdkLocator::class.java)

    /**
     * SDK runtimes known to ship the GIR files the schema generator needs.
     * Curated like [io.github.andrepg.gtk.schema.gir.SchemaPatches]: anything
     * outside this list is ignored up front — discovery returns null and no
     * schema is served for that project.
     */
    internal val supportedSdks: List<String> =
        listOf(
            "org.gnome.Sdk",
        )

    /**
     * Resolves the gir-1.0 directory for [sdkAppId], or null when no supported
     * SDK is found (caller then serves no schema).
     *
     * @param sdkAppId the SDK app-id to look for (e.g. `org.gnome.Sdk`); null/blank disables discovery
     * @param branchHint preferred branch (e.g. `50`); null falls back to the highest numeric branch
     * @param flatpakBinary path of the flatpak CLI binary
     */
    fun locate(
        sdkAppId: String?,
        branchHint: String?,
        flatpakBinary: String,
    ): File? {
        if (sdkAppId.isNullOrBlank()) return null
        if (sdkAppId !in supportedSdks) {
            log.debug("Ignoring SDK $sdkAppId; only $supportedSdks carry the GIR files we need")
            return null
        }

        // CLI-only: pick the installed branch (manifest pin first, ties in favor
        // of user installations), then resolve through `flatpak info`.
        val flatpakRuntimes =
            runProcess(
                listOf(flatpakBinary, "list", "--runtime", "--columns=application,branch,installation"),
            )
        if (flatpakRuntimes == null) {
            log.debug("flatpak CLI unavailable; no schema will be served")
            return null
        }

        val branch = pickBranch(parseFlatpakRuntimeList(flatpakRuntimes), sdkAppId, branchHint)
        if (branch == null) {
            log.debug("No installed runtime for $sdkAppId; no schema will be served")
            return null
        }

        return cliGirDir(sdkAppId, branch, flatpakBinary).also { girDir ->
            if (girDir != null) log.info("Resolved $sdkAppId@$branch via flatpak CLI")
        }
    }

    /**
     * Selects the branch to use for [sdkAppId]: the [branchHint] when one of the
     * installed rows matches it, otherwise the highest numeric branch. Ties are
     * broken in favor of user installations. Returns null when no row matches.
     */
    internal fun pickBranch(
        rows: List<FlatpakRuntimeRow>,
        sdkAppId: String,
        branchHint: String?,
    ): String? {
        val matching = rows.filter { it.appId == sdkAppId }
        if (matching.isEmpty()) return null
        branchHint?.let { hint ->
            matching.filter { it.branch == hint }.minByOrNull(::installRank)?.let { return it.branch }
        }
        val highest = matching.maxOfOrNull { numericBranch(it.branch) } ?: return matching.first().branch
        return matching.filter { numericBranch(it.branch) == highest }.minByOrNull(::installRank)?.branch
    }

    /** Resolves `<location>/files/share/gir-1.0` for an installed SDK, verifying `Gtk-4.0.gir`. */
    internal fun cliGirDir(
        sdkAppId: String,
        branch: String,
        flatpakBinary: String,
    ): File? {
        val location =
            runProcess(listOf(flatpakBinary, "info", "--show-location", "$sdkAppId//$branch"))
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return null
        val girDir = File(location, "files/share/gir-1.0")
        return girDir.takeIf { File(it, "Gtk-4.0.gir").isFile }
    }

    private fun installRank(row: FlatpakRuntimeRow): Int = if (row.installation == "user") 0 else 1

    private fun numericBranch(branch: String): Int = branch.toIntOrNull() ?: -1

    private fun runProcess(command: List<String>): String? = ProcessRunner.run(command, timeoutMs = TIMEOUT_MS)?.stdout

    private const val TIMEOUT_MS = 10_000L
}
