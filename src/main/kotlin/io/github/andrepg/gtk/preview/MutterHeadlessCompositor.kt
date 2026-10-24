package io.github.andrepg.gtk.preview

import io.github.andrepg.shared.log.Log
import io.github.andrepg.shared.process.CommandRunner
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
 *
 * @param runner process runner used for the `id -u` fallback when
 *   `XDG_RUNTIME_DIR` is not set (injectable for tests)
 * @param environmentProvider reads environment variables (injectable for tests)
 */
class MutterHeadlessCompositor(
    private val runner: CommandRunner = DefaultProcessRunner,
    private val environmentProvider: (String) -> String? = { name -> System.getenv(name) },
) {
    private val log = Log.getInstance(MutterHeadlessCompositor::class.java)

    /** The `WAYLAND_DISPLAY` name the compositor listens on (e.g. `wayland-1`). */
    val display: String get() = checkNotNull(_display) { "Compositor not started" }

    /** The `XDG_RUNTIME_DIR` path — host side, for `--filesystem` passthrough. */
    val runtimeDir: String get() = checkNotNull(_runtimeDir) { "Compositor not started" }

    private var _display: String? = null
    private var _runtimeDir: String? = null
    private var process: Process? = null
    @Volatile
    private var compositorOutput: StringBuilder = StringBuilder()

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
    fun start(
        width: Int,
        height: Int,
    ) {
        check(process == null) { "Compositor already running" }

        val runtime = resolveRuntimeDir()
        val displayName = nextDisplay(runtime)

        log.info("Starting mutter headless ${width}x$height on $displayName")

        val pb =
            ProcessBuilder(
                MUTTER_BIN,
                "--wayland",
                "--no-x11",
                "--headless",
                "--virtual-monitor",
                "${width}x$height",
                "--wayland-display",
                displayName,
            )
        pb.environment()["DBUS_SESSION_BUS_ADDRESS"] = "" // force new session via dbus-run-session
        pb.environment()["XDG_RUNTIME_DIR"] = runtime
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
                    "mutter not found. Install GNOME (mutter + dbus-run-session): ${e.message}",
                )
            }

        process = proc
        _display = displayName
        _runtimeDir = runtime
        compositorOutput = StringBuilder()

        startOutputDrainer(proc)

        try {
            waitForSocket(runtime, displayName)
        } catch (e: Exception) {
            killProcessTree(proc)
            process = null
            _display = null
            _runtimeDir = null
            val details = compositorOutput.toString().trim()
            if (details.isNotEmpty()) {
                throw IllegalStateException("$e\n$details", e)
            }
            throw e
        }
    }

    /** Kills the compositor and cleans up. Safe to call multiple times. */
    fun stop() {
        val proc = process ?: return
        log.info("Stopping mutter headless")
        killProcessTree(proc)
        try {
            proc.waitFor(2, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        // Remove any leftover socket so a stale `wayland-N` name is never
        // mistaken for a running compositor on the next render.
        val runtime = _runtimeDir
        val displayName = _display
        if (runtime != null && displayName != null) {
            runCatching { Files.deleteIfExists(Paths.get(runtime, displayName)) }
                .onFailure { log.info("Could not remove leftover socket: ${it.message}") }
            runCatching { Files.deleteIfExists(Paths.get(runtime, "$displayName.lock")) }
                .onFailure { log.info("Could not remove leftover socket lock: ${it.message}") }
        }

        process = null
        _display = null
        _runtimeDir = null
    }

    // ------------------------------------------------------------------ //
    //  Internals                                                          //
    // ------------------------------------------------------------------ //

    /**
     * Force-kills the whole compositor process chain. `flatpak`-style parent
     * processes (here `dbus-run-session`) do not propagate termination to
     * their children, so a plain destroy could orphan the actual `mutter`
     * compositor — leaving it running in the user's session and holding its
     * Wayland socket.
     */
    private fun killProcessTree(proc: Process) {
        runCatching { proc.descendants().forEach { it.destroyForcibly() } }
            .onFailure { log.info("Compositor process tree kill failed: ${it.message}") }
        proc.destroyForcibly()
    }

    /**
     * Drains the compositor's merged stdout/stderr on a daemon thread so a
     * full pipe cannot block `mutter`/`dbus-run-session` and prevent the
     * Wayland socket from being created.
     */
    private fun startOutputDrainer(proc: Process) {
        val thread =
            Thread({
                try {
                    proc.inputStream.bufferedReader().use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val text = line ?: continue
                            synchronized(compositorOutput) {
                                if (compositorOutput.length < MAX_OUTPUT_CHARS) {
                                    compositorOutput.append(text).append('\n')
                                }
                            }
                            log.debug("mutter: $text")
                        }
                    }
                } catch (_: Exception) {
                }
            }, "mutter-headless-drainer")
        thread.isDaemon = true
        thread.start()
    }

    internal fun resolveRuntimeDir(): String {
        val dir = environmentProvider("XDG_RUNTIME_DIR")
        if (dir != null && File(dir).isDirectory) return dir

        // Fallback: resolve UID via `id -u` (portable across Linux distros)
        val uid =
            runner
                .run(listOf("id", "-u"), timeoutMs = 2_000)
                ?.stdout
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: error(
                    "Cannot determine XDG_RUNTIME_DIR. " +
                        "Set XDG_RUNTIME_DIR or ensure `id` is available.",
                )
        val fallback = "/run/user/$uid"
        if (!File(fallback).isDirectory) {
            error("XDG_RUNTIME_DIR is not set and $fallback is not available")
        }
        return fallback
    }

    /**
     * Picks the next `wayland-N` name that does not already have a
     * socket file on disk.
     */
    internal fun nextDisplay(runtime: String): String {
        for (n in 1..99) {
            val name = "wayland-$n"
            if (!Files.exists(Paths.get(runtime, name))) return name
        }
        error("All wayland-1..99 displays are occupied")
    }

    /**
     * Polls for the Wayland socket to appear, with a hard timeout.
     */
    private fun waitForSocket(
        runtime: String,
        displayName: String,
    ) {
        val socketPath = Paths.get(runtime, displayName)
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
                    synchronized(compositorOutput) { compositorOutput.toString() }.trim()
                error("Mutter exited early: ${if (output.isNotEmpty()) output else "(no output captured)"}")
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
        private const val MAX_OUTPUT_CHARS = 4096
    }
}
