package io.github.andrepg.gtk.preview

import io.github.andrepg.gtk.schema.SdkHint
import io.github.andrepg.shared.process.CommandRunner
import io.github.andrepg.shared.process.ProcessRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hermetic tests for [GtkBuilderToolRunner] using an injected fake
 * [CommandRunner]; no flatpak or GNOME SDK required.
 */
class GtkBuilderToolRunnerHermeticTest {

    private val sampleFlatpakList = """
        org.gnome.Sdk	50	user
        org.gnome.Platform	50	system
        org.gnome.Sdk	49	system
    """.trimIndent()

    private fun fakeRunner(
        result: ProcessRunner.ProcessResult? = ProcessRunner.ProcessResult(0, "", ""),
        onCommand: (List<String>) -> Unit = {},
    ) = CommandRunner { command, _ ->
        onCommand(command)
        result
    }

    @Test
    fun `resolveBranch maps installed pinned branch from fake flatpak list`() {
        val runner = fakeRunner(ProcessRunner.ProcessResult(0, sampleFlatpakList, ""))
        val resolution = GtkBuilderToolRunner.resolveBranch(SdkHint("org.gnome.Sdk", "50"), "/usr/bin/flatpak", runner)
        assertEquals(GtkBuilderToolRunner.BranchResolution.Installed("50"), resolution)
    }

    @Test
    fun `resolveBranch reports pinned branch not installed`() {
        val runner = fakeRunner(ProcessRunner.ProcessResult(0, sampleFlatpakList, ""))
        val resolution = GtkBuilderToolRunner.resolveBranch(SdkHint("org.gnome.Sdk", "99"), "/usr/bin/flatpak", runner)
        assertEquals(GtkBuilderToolRunner.BranchResolution.BranchNotInstalled("99"), resolution)
    }

    @Test
    fun `resolveBranch returns NotFound when flatpak list fails`() {
        val runner = fakeRunner(null)
        val resolution = GtkBuilderToolRunner.resolveBranch(SdkHint("org.gnome.Sdk", "50"), "/usr/bin/flatpak", runner)
        assertEquals(GtkBuilderToolRunner.BranchResolution.NotFound, resolution)
    }

    @Test
    fun `validate injects LD_PRELOAD env and passes the gate on exit 0`() {
        var capturedCommand: List<String>? = null
        val runner = fakeRunner(
            result = ProcessRunner.ProcessResult(0, "", ""),
            onCommand = { capturedCommand = it },
        )
        val uiFile = File.createTempFile("window", ".ui")

        val result = GtkBuilderToolRunner.validate(uiFile, "org.gnome.Sdk", "50", "/usr/bin/flatpak", "/tmp/adw-shim.so", runner = runner)

        assertNotNull(capturedCommand)
        assertEquals("--env=LD_PRELOAD=/tmp/adw-shim.so", capturedCommand!![1])
        assertTrue(capturedCommand!!.contains("validate"))
        assertTrue(result.passesGate)
        uiFile.delete()
    }

    @Test
    fun `validate fails the gate when the validator reports diagnostics`() {
        val runner = fakeRunner(ProcessRunner.ProcessResult(1, "", "Invalid object type 'AdwApplicationWindow'"))
        val uiFile = File.createTempFile("window", ".ui")

        val result = GtkBuilderToolRunner.validate(uiFile, "org.gnome.Sdk", "50", "/usr/bin/flatpak", runner = runner)

        assertFalse(result.passesGate)
        assertTrue(result.stderr.contains("Invalid object type"))
        uiFile.delete()
    }

    @Test
    fun `validate fails the gate when process cannot start`() {
        val runner = fakeRunner(null)
        val uiFile = File.createTempFile("window", ".ui")

        val result = GtkBuilderToolRunner.validate(uiFile, "org.gnome.Sdk", "50", "/usr/bin/flatpak", runner = runner)

        assertFalse(result.passesGate)
        uiFile.delete()
    }

    @Test
    fun `render builds the command line and maps exit 0 to the png file`() {
        var capturedCommand: List<String>? = null
        val runner = fakeRunner(
            result = ProcessRunner.ProcessResult(0, "", ""),
            onCommand = { capturedCommand = it },
        )
        val uiFile = File.createTempFile("window", ".ui")
        val outPng = File.createTempFile("out", ".png")

        val result = GtkBuilderToolRunner.render(uiFile, outPng, "org.gnome.Sdk", "50", "/usr/bin/flatpak", runner = runner)

        assertNotNull(capturedCommand)
        assertTrue(capturedCommand!!.contains("render"))
        assertTrue(capturedCommand!!.contains(outPng.absolutePath))
        assertTrue(result.ok)
        assertEquals(outPng, result.pngFile)
        uiFile.delete()
        outPng.delete()
    }

    @Test
    fun `render fails when the process exits non-zero`() {
        val runner = fakeRunner(ProcessRunner.ProcessResult(1, "", "boom"))
        val uiFile = File.createTempFile("window", ".ui")
        val outPng = File.createTempFile("out", ".png")

        val result = GtkBuilderToolRunner.render(uiFile, outPng, "org.gnome.Sdk", "50", "/usr/bin/flatpak", runner = runner)

        assertFalse(result.ok)
        assertNull(result.pngFile)
        uiFile.delete()
        outPng.delete()
    }
}
