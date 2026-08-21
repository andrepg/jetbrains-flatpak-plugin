package io.github.andrepg.flatpak.runs.execution

import io.github.andrepg.shared.log.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Removes stale FUSE mounts left inside the build directory by an uncleanly
 * terminated flatpak-builder run (e.g. the IDE Stop button killing the process
 * tree mid-teardown). A dead rofiles-fuse mount makes every later build fail
 * with "Transport endpoint is not connected".
 *
 * Pure JDK logic (no platform imports) so it is unit-testable; the runner wires
 * it as a quiet pre-step that stays silent unless it actually cleaned something
 * or could not.
 *
 * Fail-open by design: every environment limitation (unreadable /proc, sandboxed
 * IDE, missing unmount tools, refused unmount) degrades to a warning and never
 * aborts the chain. Only FUSE-type mounts (incl. flatpak's `rofiles-fuse`)
 * strictly under [buildDir] are ever touched.
 */
class StaleFuseMountCleaner(
    private val mountsSupplier: () -> String = { File(MOUNTS_FILE).readText() },
    private val unmountRunner: (List<String>) -> Boolean = ::runUnmountCommand,
    private val sandboxDetector: () -> Boolean = { File(SANDBOX_MARKER).exists() },
) {
    private val log = Log.getInstance(StaleFuseMountCleaner::class.java)

    /**
     * Unmounts every stale FUSE mount under [buildDir], reporting each action
     * through [report] (console SYSTEM output). Always returns true: failures
     * are reported and logged, but must not block the user's explicit run.
     */
    fun clean(
        buildDir: File,
        report: (String) -> Unit,
    ): Boolean {
        if (sandboxDetector()) {
            log.info("Sandboxed IDE detected ($SANDBOX_MARKER present); skipping stale FUSE mount sweep")
            return true
        }

        val stale =
            try {
                staleFuseMounts(mountsSupplier(), buildDir)
            } catch (e: Exception) {
                log.warn("Could not read the mount table; skipping stale FUSE mount sweep", e)
                return true
            }
        if (stale.isEmpty()) return true

        val survivors: Set<String> =
            try {
                unmountAll(stale)
                staleFuseMounts(mountsSupplier(), buildDir).toSet()
            } catch (e: Exception) {
                log.warn("Could not re-read the mount table after unmounting", e)
                stale.toSet()
            }

        for (mountPoint in stale) {
            if (mountPoint !in survivors) {
                report("UNMOUNT_STALE: removed stale FUSE mount at $mountPoint\n")
            }
        }
        if (survivors.isNotEmpty()) {
            report("UNMOUNT_STALE: could not unmount ${survivors.joinToString(", ")}\n")
            report("UNMOUNT_STALE: try manually, e.g. fusermount3 -uz <mount-point> or umount -l <mount-point>\n")
            log.warn("Stale FUSE mounts could not be removed: $survivors")
        }
        return true
    }

    /** FUSE mount points strictly under [buildDir], in mount-table order. */
    private fun staleFuseMounts(
        mountsContent: String,
        buildDir: File,
    ): List<String> {
        val base = buildDir.canonicalFile.path + File.separator
        return mountsContent
            .lineSequence()
            .mapNotNull { parseMountTypeAndPoint(it) }
            .filter { entry ->
                entry.type.contains(FUSE_TYPE_MARKER) &&
                    canonicalPath(entry.mountPoint).startsWith(base)
            }
            .map { it.mountPoint }
            .toList()
    }

    /**
     * Canonicalizes [path] so matching survives symlinked project roots
     * (`/home` → `/var/home` on Fedora Atomic); falls back to the raw path when
     * the kernel cannot resolve it — typical for a dead mount point.
     */
    private fun canonicalPath(path: String): String =
        try {
            File(path).canonicalPath
        } catch (_: Exception) {
            path
        }

    private fun unmountAll(mountPoints: Collection<String>) {
        for (mountPoint in mountPoints) {
            for (commandFactory in UNMOUNT_COMMANDS) {
                if (unmountRunner(commandFactory(mountPoint))) break
            }
        }
    }

    data class MountEntry(
        val type: String,
        val mountPoint: String,
    )

    companion object {
        private const val MOUNTS_FILE = "/proc/self/mounts"
        private const val SANDBOX_MARKER = "/.flatpak-info"

        /** Matches `fuse`, `fuse.sshfs` and flatpak's own `rofiles-fuse`. */
        private const val FUSE_TYPE_MARKER = "fuse"

        private val UNMOUNT_COMMANDS: List<(String) -> List<String>> =
            listOf(
                { mountPoint -> listOf("fusermount3", "-uz", mountPoint) },
                { mountPoint -> listOf("fusermount", "-uz", mountPoint) },
                { mountPoint -> listOf("umount", "-l", mountPoint) },
            )

        /**
         * Parses `device mountpoint fstype options...`; the mountpoint is
         * octal-escaped by the kernel (`\040` space, `\011` tab, `\012`
         * newline, `\134` backslash — decoded last).
         */
        internal fun parseMountTypeAndPoint(line: String): MountEntry? {
            val fields = line.trim().split(Regex("\\s+"))
            if (fields.size < 3) return null
            return MountEntry(fields[2], decodeEscapes(fields[1]))
        }

        private fun decodeEscapes(raw: String): String =
            raw
                .replace("\\040", " ")
                .replace("\\011", "\t")
                .replace("\\012", "\n")
                .replace("\\134", "\\")

        private fun runUnmountCommand(command: List<String>): Boolean =
            try {
                val process =
                    ProcessBuilder(command)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start()
                val finished = process.waitFor(UNMOUNT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (!finished) process.destroyForcibly()
                finished && process.exitValue() == 0
            } catch (e: Exception) {
                Log.getInstance(StaleFuseMountCleaner::class.java)
                    .debug("Unmount command failed: ${command.joinToString(" ")}", e)
                false
            }

        private const val UNMOUNT_TIMEOUT_SECONDS = 10L
    }
}
