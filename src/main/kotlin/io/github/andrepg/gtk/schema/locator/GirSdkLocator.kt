package io.github.andrepg.gtk.schema.locator

import io.github.andrepg.shared.log.Log
import io.github.andrepg.shared.process.ProcessRunner
import java.io.File

/**
 * Locates the GObject Introspection (GIR) directory of a GNOME SDK installed
 * via Flatpak.
 *
 * Manifest-agnostic: the caller supplies the SDK app-id (e.g. `org.gnome.Sdk`)
 * and an optional branch hint (e.g. `50`); the branch is picked from
 * `flatpak list --runtime` output preferring the hint and user installations.
 * The gir-1.0 directory is resolved through `flatpak info --show-location`,
 * falling back to the standard runtime paths. JDK-only, no IntelliJ imports.
 */
object GirSdkLocator {
    private val log = Log.getInstance(GirSdkLocator::class.java)

    /** One row of `flatpak list --runtime --columns=application,branch,installation`. */
    data class RuntimeRow(
        val appId: String,
        val branch: String,
        val installation: String,
    )

    /**
     * Resolves the gir-1.0 directory for [sdkAppId], or null when no SDK is
     * found (caller should then keep the bundled schema).
     *
     * @param sdkAppId the SDK app-id to look for (e.g. `org.gnome.Sdk`); null/blank disables discovery
     * @param branchHint preferred branch (e.g. `50`); null falls back to the highest numeric branch
     * @param flatpakBinary path of the flatpak CLI binary
     * @param baseDirs fallback Flatpak install roots (default: user + system installs)
     */
    fun locate(
        sdkAppId: String?,
        branchHint: String?,
        flatpakBinary: String,
        baseDirs: List<File> = defaultBaseDirs(),
    ): File? {
        if (sdkAppId.isNullOrBlank()) return null

        // CLI-first: prefer the branch the manifest pins, ties resolved in
        // favor of user installations, then resolve through `flatpak info`.
        val stdout =
            runProcess(
                listOf(flatpakBinary, "list", "--runtime", "--columns=application,branch,installation"),
            )
        if (stdout != null) {
            val branch = pickBranch(parseRuntimeRows(stdout), sdkAppId, branchHint)
            if (branch != null) {
                log.info("Resolved $sdkAppId@$branch via flatpak CLI")
                return cliGirDir(sdkAppId, branch, flatpakBinary)
                    ?: globFallback(baseDirs, sdkAppId, branch)
            }
            log.debug("No installed runtime for $sdkAppId; probing install roots directly")
        } else {
            log.debug("flatpak CLI unavailable; probing install roots directly")
        }

        // CLI unavailable (missing binary, no runtimes) or SDK not installed:
        // probe the standard install roots directly.
        return globFallbackBest(baseDirs, sdkAppId, branchHint)
    }

    /**
     * Parses tab-separated `flatpak list --runtime` output.
     *
     * @param stdout raw stdout of the flatpak command
     * @return the parsed runtime rows, skipping blank/partial lines
     */
    internal fun parseRuntimeRows(stdout: String): List<RuntimeRow> =
        stdout.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val columns = line.split('\t')
                if (columns.size < 2) return@mapNotNull null
                RuntimeRow(columns[0], columns[1], columns.getOrNull(2).orEmpty())
            }
            .toList()

    /**
     * Selects the branch to use for [sdkAppId]: the [branchHint] when one of the
     * installed rows matches it, otherwise the highest numeric branch. Ties are
     * broken in favor of user installations. Returns null when no row matches.
     */
    internal fun pickBranch(
        rows: List<RuntimeRow>,
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

    /** Probes the standard Flatpak install roots for the SDK's gir-1.0 directory. */
    internal fun globFallback(
        baseDirs: List<File>,
        sdkAppId: String,
        branch: String,
    ): File? = baseDirs.firstNotNullOfOrNull { resolveFromBaseDir(it, sdkAppId, branch) }

    /**
     * Fallback used when the flatpak CLI is unavailable: probes the install
     * roots for the [branchHint] branch, else scans for the highest numeric
     * branch actually installed.
     */
    internal fun globFallbackBest(
        baseDirs: List<File>,
        sdkAppId: String,
        branchHint: String?,
    ): File? {
        branchHint?.let { hint ->
            globFallback(baseDirs, sdkAppId, hint)?.let { return it }
        }
        val branches =
            baseDirs
                .flatMap { base -> File(base, "runtime/$sdkAppId/x86_64").listFiles()?.toList().orEmpty() }
                .filter { it.isDirectory && it.name.toIntOrNull() != null }
                .map { it.name.toInt() }
        val best = branches.maxOrNull() ?: return null
        return globFallback(baseDirs, sdkAppId, best.toString())
    }

    /** Resolves the gir-1.0 directory under a Flatpak install root, verifying `Gtk-4.0.gir`. */
    internal fun resolveFromBaseDir(
        base: File,
        sdkAppId: String,
        branch: String,
    ): File? {
        val candidate = File(base, "runtime/$sdkAppId/x86_64/$branch/active/files/share/gir-1.0")
        return candidate.takeIf { File(it, "Gtk-4.0.gir").isFile }
    }

    internal fun defaultBaseDirs(): List<File> =
        listOf(
            File(System.getProperty("user.home"), ".local/share/flatpak"),
            File("/var/lib/flatpak"),
        )

    private fun installRank(row: RuntimeRow): Int = if (row.installation == "user") 0 else 1

    private fun numericBranch(branch: String): Int = branch.toIntOrNull() ?: -1

    private fun runProcess(command: List<String>): String? = ProcessRunner.run(command, timeoutMs = TIMEOUT_MS)?.stdout

    private const val TIMEOUT_MS = 10_000L
}
