package io.github.andrepg.flatpak.runs.cleanup

import io.github.andrepg.flatpak.runs.steps.PreStepType
import io.github.andrepg.shared.log.Log
import io.github.andrepg.shared.process.DefaultProcessRunner
import java.io.File

/**
 * Removes stale FUSE mounts left inside the project's flatpak-builder state
 * dir by an uncleanly terminated build (e.g. the IDE Stop button killing the
 * process tree mid-teardown). A dead rofiles-fuse mount makes every later
 * build fail with "Transport endpoint is not connected".
 *
 * Scope: FUSE mounts under any of [clean]'s roots (the project root and/or the
 * configured build dir) that contain the `/.flatpak-builder/` path segment —
 * flatpak-builder's state territory, wherever it actually is. The state dir
 * defaults to the process working directory (the project root), NOT the build
 * dir, which is why scoping to `_build` alone misses every real mount.
 *
 * Pure JDK logic (no platform imports) so it is unit-testable; the runner wires
 * it as a quiet pre-step that stays silent unless it actually cleaned something
 * or could not.
 *
 * Fail-open by design: every environment limitation (unreadable /proc, sandboxed
 * IDE, missing unmount tools, refused unmount) degrades to a warning and never
 * aborts the chain.
 */
class StaleFuseMountCleaner(
    private val mountsSupplier: () -> String = { File(MOUNTS_FILE).readText() },
    private val unmountRunner: (List<String>) -> Boolean = ::runUnmountCommand,
    private val sandboxDetector: () -> Boolean = { File(SANDBOX_MARKER).exists() },
) {
    private val log = Log.getInstance(StaleFuseMountCleaner::class.java)

    /**
     * Unmounts every stale FUSE mount inside flatpak-builder state dirs under
     * [scopeRoots], reporting each action through [report] (console SYSTEM
     * output). Always returns true: failures are reported and logged, but must
     * not block the user's explicit run.
     */
    fun clean(
        scopeRoots: Collection<File>,
        report: (String) -> Unit,
    ): Boolean {
        if (sandboxDetector()) {
            log.info("Sandboxed IDE detected ($SANDBOX_MARKER present); skipping stale FUSE mount sweep")
            return true
        }

        val stale =
            try {
                staleFuseMounts(mountsSupplier(), scopeRoots)
            } catch (e: Exception) {
                log.warn("Could not read the mount table; skipping stale FUSE mount sweep", e)
                return true
            }
        if (stale.isEmpty()) return true

        val survivors: Set<String> =
            try {
                unmountAll(stale)
                staleFuseMounts(mountsSupplier(), scopeRoots).toSet()
            } catch (e: Exception) {
                log.warn("Could not re-read the mount table after unmounting", e)
                stale.toSet()
            }

        for (mountPoint in stale) {
            if (mountPoint !in survivors) {
                report("${PreStepType.UNMOUNT_STALE}: removed stale FUSE mount at $mountPoint\n")
            }
        }
        if (survivors.isNotEmpty()) {
            report("${PreStepType.UNMOUNT_STALE}: could not unmount ${survivors.joinToString(", ")}\n")
            report(
                "${PreStepType.UNMOUNT_STALE}: try manually, e.g. fusermount3 -uz <mount-point> or umount -l <mount-point>\n",
            )
            log.warn("Stale FUSE mounts could not be removed: $survivors")
        }
        return true
    }

    /** FUSE mounts inside flatpak-builder state dirs under any of [scopeRoots]. */
    private fun staleFuseMounts(
        mountsContent: String,
        scopeRoots: Collection<File>,
    ): List<String> {
        val bases = scopeRoots.map { it.canonicalFile.path + File.separator }
        return mountsContent
            .lineSequence()
            .mapNotNull { parseMountTypeAndPoint(it) }
            .filter { entry ->
                if (!entry.type.contains(FUSE_TYPE_MARKER)) return@filter false
                val canonical = canonicalPath(entry.mountPoint)
                STATE_DIR_SEGMENT in canonical && bases.any { canonical.startsWith(it) }
            }.map { it.mountPoint }
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
            try {
                // The leaf itself is dead (ENOTCONN on realpath), but its parent
                // chain is alive: canonicalize the parents, rejoin the leaf. This
                // keeps symlinked-root matching working for dead mounts too.
                val file = File(path)
                File(file.parentFile?.canonicalPath ?: "", file.name).path
            } catch (_: Exception) {
                path
            }
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

        /** Only ever touch mounts inside flatpak-builder's own state directory. */
        internal const val STATE_DIR_SEGMENT = "/.flatpak-builder/"

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

        private fun runUnmountCommand(command: List<String>): Boolean {
            val result =
                DefaultProcessRunner.run(
                    command,
                    timeoutMs = UNMOUNT_TIMEOUT_SECONDS * 1000L,
                )
            if (result == null) {
                Log
                    .getInstance(StaleFuseMountCleaner::class.java)
                    .debug("Unmount command failed: ${command.joinToString(" ")}")
            }
            return result?.exitCode == 0
        }

        private const val UNMOUNT_TIMEOUT_SECONDS = 10L
    }
}
