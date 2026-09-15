package io.github.andrepg.gtk.preview

import io.github.andrepg.shared.process.CommandRunner
import io.github.andrepg.shared.process.ProcessResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class MutterHeadlessCompositorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `nextDisplay picks the first free wayland name`() {
        val runtime = tmp.newFolder("runtime")

        val display = compositor().nextDisplay(runtime.absolutePath)

        assertEquals("wayland-1", display)
    }

    @Test
    fun `nextDisplay skips occupied sockets`() {
        val runtime = tmp.newFolder("runtime")
        Files.createFile(runtime.toPath().resolve("wayland-1"))
        Files.createFile(runtime.toPath().resolve("wayland-2"))

        val display = compositor().nextDisplay(runtime.absolutePath)

        assertEquals("wayland-3", display)
    }

    @Test
    fun `nextDisplay fails when every slot is occupied`() {
        val runtime = tmp.newFolder("runtime")
        for (n in 1..9) {
            Files.createFile(runtime.toPath().resolve("wayland-$n"))
        }

        assertThrows(IllegalStateException::class.java) {
            compositor().nextDisplay(runtime.absolutePath)
        }
    }

    @Test
    fun `resolveRuntimeDir prefers XDG_RUNTIME_DIR`() {
        val xdg = tmp.newFolder("xdg")
        val compositor = MutterHeadlessCompositor(environmentProvider = { _ -> xdg.absolutePath })

        assertEquals(xdg.absolutePath, compositor.resolveRuntimeDir())
    }

    @Test
    fun `resolveRuntimeDir ignores a non-existent XDG_RUNTIME_DIR`() {
        val uidRunner = CommandRunner { _, _ -> ProcessResult(0, "1000\n", "") }
        val compositor = MutterHeadlessCompositor(runner = uidRunner, environmentProvider = { _ -> "/nonexistent" })

        assertEquals("/run/user/1000", compositor.resolveRuntimeDir())
    }

    @Test
    fun `resolveRuntimeDir falls back to the uid from id`() {
        val uidRunner = CommandRunner { _, _ -> ProcessResult(0, "4242\n", "") }
        val compositor = MutterHeadlessCompositor(runner = uidRunner, environmentProvider = { _ -> null })

        assertEquals("/run/user/4242", compositor.resolveRuntimeDir())
    }

    private fun compositor(): MutterHeadlessCompositor =
        MutterHeadlessCompositor(
            runner = CommandRunner { _, _ -> null },
            environmentProvider = { _ -> null },
        )
}