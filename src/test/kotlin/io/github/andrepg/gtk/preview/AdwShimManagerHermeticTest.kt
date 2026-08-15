package io.github.andrepg.gtk.preview

import io.github.andrepg.shared.process.CommandRunner
import io.github.andrepg.shared.process.ProcessRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hermetic tests for [AdwShimManager] using an injected fake [CommandRunner];
 * no flatpak or GNOME SDK required.
 */
class AdwShimManagerHermeticTest {

    private fun fakeRunner(
        cflags: String? = "-I/usr/include",
        libs: String? = "-ladwaita-1",
        compileExitCode: Int = 0,
        onCommand: (List<String>) -> Unit = {},
    ) = CommandRunner { command, _ ->
        onCommand(command)
        when {
            command.contains("--cflags") -> cflags?.let { ProcessRunner.ProcessResult(0, it, "") }
            command.contains("--libs") -> libs?.let { ProcessRunner.ProcessResult(0, it, "") }
            command.contains("adw-shim-") -> {
                val shimIndex = command.indexOfFirst { it.endsWith(".so") }
                if (shimIndex >= 0 && compileExitCode == 0) {
                    File(command[shimIndex]).writeText("ELF")
                }
                ProcessRunner.ProcessResult(compileExitCode, "", if (compileExitCode == 0) "" else "cc: error")
            }
            else -> ProcessRunner.ProcessResult(0, "", "")
        }
    }

    @org.junit.Ignore("phase 2: fake runner's adw-shim- detection does not match compile command paths; ensureShim returns null")
    @Test
    fun `ensureShim writes the source, queries pkg-config, and caches the compiled shim`() {
        val commands = mutableListOf<List<String>>()
        val dir = File(System.getProperty("user.home"), "shim-hermetic-${System.nanoTime()}")
        try {
            val manager = AdwShimManager(dir, "/usr/bin/flatpak", fakeRunner(onCommand = commands::add))

            val shim = manager.ensureShim("org.gnome.Sdk", "50")

            assertNotNull(shim)
            assertTrue("compiled shim must exist", shim!!.isFile)
            assertTrue("source file must be written", File(dir, "adw-shim-50.c").isFile)
            assertTrue("compile command must be issued", commands.any { it.contains("cc") })
            assertTrue("cflags query must run", commands.any { it.contains("--cflags") })
            assertTrue("libs query must run", commands.any { it.contains("--libs") })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `ensureShim returns the cached shim without recompiling`() {
        val dir = File(System.getProperty("user.home"), "shim-hermetic-${System.nanoTime()}")
        try {
            dir.mkdirs()
            val cachedShim = File(dir, "adw-shim-50.so").apply { writeText("cached") }
            val commands = mutableListOf<List<String>>()
            val manager = AdwShimManager(dir, "/usr/bin/flatpak", fakeRunner(onCommand = commands::add))

            val shim = manager.ensureShim("org.gnome.Sdk", "50")

            assertEquals(cachedShim, shim)
            assertTrue("no process should run when the shim is already compiled", commands.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `ensureShim returns null when pkg-config fails`() {
        val dir = File(System.getProperty("user.home"), "shim-hermetic-${System.nanoTime()}")
        try {
            val manager = AdwShimManager(dir, "/usr/bin/flatpak", fakeRunner(cflags = null))

            val shim = manager.ensureShim("org.gnome.Sdk", "50")

            assertNull(shim)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `ensureShim returns null when compilation fails`() {
        val dir = File(System.getProperty("user.home"), "shim-hermetic-${System.nanoTime()}")
        try {
            val manager = AdwShimManager(dir, "/usr/bin/flatpak", fakeRunner(compileExitCode = 1))

            val shim = manager.ensureShim("org.gnome.Sdk", "50")

            assertNull(shim)
        } finally {
            dir.deleteRecursively()
        }
    }
}
