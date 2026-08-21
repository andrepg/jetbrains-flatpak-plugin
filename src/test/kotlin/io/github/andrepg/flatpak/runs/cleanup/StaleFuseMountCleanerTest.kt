package io.github.andrepg.flatpak.runs.cleanup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StaleFuseMountCleanerTest {
    private class FakeRunner : (List<String>) -> Boolean {
        val invocations = mutableListOf<List<String>>()
        var succeeds: (List<String>) -> Boolean = { false }

        override fun invoke(command: List<String>): Boolean {
            invocations += command
            return succeeds(command)
        }
    }

    private fun mounts(vararg lines: String) = lines.joinToString("\n") + "\n"

    private fun fuseLine(mountPoint: String) =
        realFormatLine("rofiles", mountPoint, "fuse.rofiles.rofiles")

    private fun realFormatLine(
        device: String,
        mountPoint: String,
        type: String,
    ) = "$device $mountPoint $type rw,nosuid,nodev 0 0"

    private fun sequenced(vararg contents: String): () -> String {
        val queue = ArrayDeque(contents.toList())
        return { queue.removeFirst() }
    }

    private fun withTempBuildDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("fuse-cleaner").toFile()
        try {
            block(File(dir, "_build").apply { mkdirs() })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `no stale mounts is a silent no-op`() {
        withTempBuildDir { buildDir ->
            val runner = FakeRunner()
            val reports = mutableListOf<String>()
            val cleaner =
                StaleFuseMountCleaner(
                    mountsSupplier = { mounts(realFormatLine("/dev/sda1", "/", "ext4")) },
                    unmountRunner = runner,
                )

            val result = cleaner.clean(buildDir) { reports += it }

            assertTrue(result)
            assertTrue(reports.isEmpty())
            assertTrue(runner.invocations.isEmpty())
        }
    }

    @Test
    fun `removes stale fuse mount under build dir`() {
        withTempBuildDir { buildDir ->
            val mountPoint = "${buildDir.path}/.flatpak-builder/rofiles/rofiles-abc"
            val runner =
                FakeRunner().apply {
                    succeeds = { it.first() == "fusermount3" }
                }
            val reports = mutableListOf<String>()
            val cleaner =
                StaleFuseMountCleaner(
                    mountsSupplier = sequenced(
                        mounts(fuseLine(mountPoint)),
                        mounts(),
                    ),
                    unmountRunner = runner,
                )

            val result = cleaner.clean(buildDir) { reports += it }

            assertTrue(result)
            assertEquals(listOf(listOf("fusermount3", "-uz", mountPoint)), runner.invocations)
            assertTrue(reports.single().contains("removed stale FUSE mount at $mountPoint"))
        }
    }

    @Test
    fun `ignores non-fuse types and mounts outside the build dir`() {
        withTempBuildDir { buildDir ->
            val runner = FakeRunner()
            val cleaner =
                StaleFuseMountCleaner(
                    mountsSupplier = {
                        mounts(
                            realFormatLine("tmpfs", "${buildDir.path}/scratch", "tmpfs"),
                            realFormatLine("gvfsd", "/run/user/1000/gvfs", "fuse.gvfsd-fuse"),
                            fuseLine("${buildDir.path}-sibling/.flatpak-builder/rofiles/x"),
                        )
                    },
                    unmountRunner = runner,
                )

            val result = cleaner.clean(buildDir) { }

            assertTrue(result)
            assertTrue(runner.invocations.isEmpty())
        }
    }

    @Test
    fun `falls back through fusermount3 fusermount and umount`() {
        withTempBuildDir { buildDir ->
            val mountPoint = "${buildDir.path}/.flatpak-builder/rofiles/rofiles-abc"
            val runner =
                FakeRunner().apply {
                    succeeds = { it.first() == "umount" }
                }
            val cleaner =
                StaleFuseMountCleaner(
                    mountsSupplier = sequenced(
                        mounts(fuseLine(mountPoint)),
                        mounts(),
                    ),
                    unmountRunner = runner,
                )

            cleaner.clean(buildDir) { }

            assertEquals(
                listOf(
                    listOf("fusermount3", "-uz", mountPoint),
                    listOf("fusermount", "-uz", mountPoint),
                    listOf("umount", "-l", mountPoint),
                ),
                runner.invocations,
            )
        }
    }

    @Test
    fun `unremovable mount warns and continues`() {
        withTempBuildDir { buildDir ->
            val mountPoint = "${buildDir.path}/.flatpak-builder/rofiles/rofiles-abc"
            val content = mounts(fuseLine(mountPoint))
            val runner = FakeRunner()
            val reports = mutableListOf<String>()
            val cleaner =
                StaleFuseMountCleaner(
                    mountsSupplier = sequenced(content, content),
                    unmountRunner = runner,
                )

            val result = cleaner.clean(buildDir) { reports += it }

            assertTrue("an unremovable mount must never abort the chain", result)
            assertEquals(3, runner.invocations.size)
            assertTrue(reports.any { it.contains("could not unmount") && it.contains(mountPoint) })
            assertTrue(reports.any { it.contains("try manually") })
        }
    }

    @Test
    fun `rofiles-fuse type is detected`() {
        withTempBuildDir { buildDir ->
            val mountPoint = "${buildDir.path}/.flatpak-builder/rofiles/rofiles-abc"
            val runner =
                FakeRunner().apply { succeeds = { true } }
            val reports = mutableListOf<String>()
            val cleaner =
                StaleFuseMountCleaner(
                    mountsSupplier = sequenced(
                        mounts(realFormatLine("rofiles", mountPoint, "rofiles-fuse")),
                        mounts(),
                    ),
                    unmountRunner = runner,
                )

            val result = cleaner.clean(buildDir) { reports += it }

            assertTrue(result)
            assertEquals(1, runner.invocations.size)
            assertTrue(reports.single().contains("removed stale FUSE mount at $mountPoint"))
        }
    }

    @Test
    fun `mount table paths through a symlinked root are matched`() {
        val base = Files.createTempDirectory("fuse-cleaner-link").toFile()
        try {
            val alias = File(base, "alias")
            Files.createSymbolicLink(alias.toPath(), File(base, "real").toPath())
            val buildDir = File(base, "real/_build").apply { mkdirs() }
            val mountPoint = alias.path + "/_build/.flatpak-builder/rofiles/r1"
            val runner =
                FakeRunner().apply { succeeds = { true } }
            val reports = mutableListOf<String>()
            val cleaner =
                StaleFuseMountCleaner(
                    mountsSupplier = sequenced(
                        mounts(fuseLine(mountPoint)),
                        mounts(),
                    ),
                    unmountRunner = runner,
                )

            val result = cleaner.clean(buildDir) { reports += it }

            assertTrue(result)
            assertEquals(listOf(listOf("fusermount3", "-uz", mountPoint)), runner.invocations)
            assertTrue(reports.single().contains(mountPoint))
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `unreadable mount table degrades silently`() {
        withTempBuildDir { buildDir ->
            val runner = FakeRunner()
            val reports = mutableListOf<String>()
            val cleaner =
                StaleFuseMountCleaner(
                    mountsSupplier = { error("no /proc on this platform") },
                    unmountRunner = runner,
                )

            val result = cleaner.clean(buildDir) { reports += it }

            assertTrue(result)
            assertTrue(reports.isEmpty())
            assertTrue(runner.invocations.isEmpty())
        }
    }

    @Test
    fun `sandboxed ide skips the sweep entirely`() {
        withTempBuildDir { buildDir ->
            val runner = FakeRunner()
            val reports = mutableListOf<String>()
            val cleaner =
                StaleFuseMountCleaner(
                    mountsSupplier = { error("must not be read inside a sandbox") },
                    unmountRunner = runner,
                    sandboxDetector = { true },
                )

            val result = cleaner.clean(buildDir) { reports += it }

            assertTrue(result)
            assertTrue(reports.isEmpty())
            assertTrue(runner.invocations.isEmpty())
        }
    }

    @Test
    fun `parses kernel format decodes escapes and rejects short lines`() {
        val entry =
            StaleFuseMountCleaner.parseMountTypeAndPoint(
                realFormatLine("rofiles", "/tmp/my\\040dir/_build/.flatpak-builder/rofiles/r1", "fuse.rofiles.rofiles"),
            )!!

        assertEquals("fuse.rofiles.rofiles", entry.type)
        assertEquals("/tmp/my dir/_build/.flatpak-builder/rofiles/r1", entry.mountPoint)

        assertNull(StaleFuseMountCleaner.parseMountTypeAndPoint(""))
        assertNull(StaleFuseMountCleaner.parseMountTypeAndPoint("only one"))
        assertNull(StaleFuseMountCleaner.parseMountTypeAndPoint("only two"))
    }
}
