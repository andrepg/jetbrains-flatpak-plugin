package io.github.andrepg.gtk.preview

import io.github.andrepg.shared.log.Log
import io.github.andrepg.shared.process.DefaultProcessRunner
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Manages a Mutter headless compositor for off-screen GTK rendering.
 *
 * Creates a private Wayland display backed by a virtual monitor of the
 * requested dimensions.  The display never appears on the user's desktop.
 * The renderer connects to it via [display] and renders the widget tree
 * to a PNG without any visible window.
 *
 * Prerequisites: `mutter` and `dbus-run-session` must be available on
 * the host.  These ship with every GNOME installation.
 */
class MutterHeadlessCompositor {
    private val log = Log.getInstance(MutterHeadlessCompositor::class.java)

    /** The `WAYLAND_DISPLAY` name the compositor listens on (e.g. `wayland-1`). */
    val display: String get() = checkNotNull(_display) { "Compositor not started" }

    /** The `XDG_RUNTIME_DIR` path — host side, for `--filesystem` passthrough. */
    val runtimeDir: String get() = checkNotNull(_runtimeDir) { "Compositor not started" }

    private var _display: String? = null
    private var _runtimeDir: String? = null
    private var process: Process? = null

    /**
     * Starts a headless Mutter compositor with a virtual monitor of
     * exactly [width] × [height] pixels.
     *
     * The compositor runs in its own D-Bus session so it never
     * conflicts with the user's running GNOME Shell.  The call blocks
     * until the Wayland socket is confirmed on disk (or times out).
     *
     * @throws IllegalStateException if mutter is not installed, fails to
     *   start, or the socket does not appear within the timeout
     */
    fun start(width: Int, height: Int) {
        check(process == null) { "Compositor already running" }

        val runtime = resolveRuntimeDir()
        val display = nextDisplay(runtime)

        log.info("Starting mutter headless ${width}x${height} on $display")

        val pb =
            ProcessBuilder(
                MUTTER_BIN,
                "--wayland",
                "--no-x11",
                "--headless",
                "--virtual-monitor",
                "${width}x${height}",
                "--wayland-display",
                display,
            )
        pb.environment()["DBUS_SESSION_BUS_ADDRESS"] = "" // force new session via dbus-run-session
        pb.redirectErrorStream(true)

        val proc =
            try {
                // Wrap in dbus-run-session so mutter gets its own D-Bus bus
                val wrapped =
                    ProcessBuilder(listOf("dbus-run-session", "--") + pb.command())
                        .apply {
                            environment().putAll(pb.environment())
                            redirectErrorStream(true)
                        }
                wrapped.start()
            } catch (e: java.io.IOException) {
                error(
                    "mutter not found. Install GNOME (mutter + dbus): ${e.message}",
                )
            }

        process = proc
        _display = display
        _runtimeDir = runtime

        try {
            waitForSocket(runtime, display)
        } catch (e: Exception) {
            proc.destroyForcibly()
            process = null
            _display = null
            _runtimeDir = null
            throw e
        }
    }

    /** Kills the compositor and cleans up. Safe to call multiple times. */
    fun stop() {
        val proc = process ?: return
        log.info("Stopping mutter headless")
        proc.destroyForcibly()
        try {
            proc.waitFor(2, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        process = null
        _display = null
        _runtimeDir = null
    }

    // ------------------------------------------------------------------ //
    //  Internals                                                          //
    // ------------------------------------------------------------------ //

    private fun resolveRuntimeDir(): String {
        val dir = System.getenv("XDG_RUNTIME_DIR")
        if (dir != null && File(dir).isDirectory) return dir

        // Fallback: resolve UID via `id -u` (portable across Linux distros)
        val uid =
            DefaultProcessRunner.run(listOf("id", "-u"), timeoutMs = 2_000)
                ?.stdout
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: error(
                    "Cannot determine XDG_RUNTIME_DIR. " +
                        "Set XDG_RUNTIME_DIR or ensure `id` is available.",
                )
        return "/run/user/$uid"
    }

    /**
     * Picks the next `wayland-N` name that does not already have a
     * socket file on disk.
     */
    private fun nextDisplay(runtime: String): String {
        for (n in 1..9) {
            val name = "wayland-$n"
            if (!Files.exists(Paths.get(runtime, name))) return name
        }
        error("All wayland-1..9 displays are occupied")
    }

    /**
     * Polls for the Wayland socket to appear, with a hard timeout.
     */
    private fun waitForSocket(runtime: String, display: String) {
        val socketPath = Paths.get(runtime, display)
        val deadline = System.currentTimeMillis() + SOCKET_TIMEOUT_MS

        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(socketPath)) {
                log.info("Mutter socket ready: $socketPath")
                return
            }

            // Check if mutter died early
            val proc = process
            if (proc != null && !proc.isAlive) {
                val output =
                    proc.inputStream.bufferedReader().readText().trim()
                error("Mutter exited early: $output")
            }

            Thread.sleep(SOCKET_POLL_MS)
        }

        stop()
        error(
            "Mutter Wayland socket did not appear within ${SOCKET_TIMEOUT_MS}ms: $socketPath",
        )
    }

    companion object {
        private const val MUTTER_BIN = "mutter"
        private const val SOCKET_POLL_MS = 50L
        private const val SOCKET_TIMEOUT_MS = 5_000L
    }
}
