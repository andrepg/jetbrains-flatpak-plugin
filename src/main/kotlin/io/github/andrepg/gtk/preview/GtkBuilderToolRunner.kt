package io.github.andrepg.gtk.preview

import io.github.andrepg.flatpak.runs.commands.FlatpakSandboxRunner
import io.github.andrepg.flatpak.settings.FlatpakSettings
import io.github.andrepg.gtk.preview.binary.GtkHeadlessRendererDownloader
import io.github.andrepg.gtk.schema.locator.GirSdkLocator
import io.github.andrepg.shared.log.Log
import io.github.andrepg.shared.process.CommandRunner
import io.github.andrepg.shared.process.DefaultProcessRunner
import io.github.andrepg.shared.process.parseFlatpakRuntimeList
import java.io.File
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.SocketChannel
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Downloads and invokes the headless GTK4 `.ui` → PNG renderer.
 *
 * The renderer is a precompiled C binary published as a GitHub release asset
 * and fetched on first use by [GtkHeadlessRendererDownloader] (verified against
 * its `.sha256`). The binary is cached under [configDir] so subsequent
 * invocations skip the download entirely; the cache marker is keyed by
 * [GtkHeadlessRendererDownloader.RENDERER_VERSION].
 *
 * @property runner process runner used to execute flatpak and shell commands
 * @property flatpakBinary path to the `flatpak` CLI binary (from plugin settings)
 * @property configDir plugin config directory used to cache the downloaded binary
 */
class GtkBuilderToolRunner(
    private val runner: CommandRunner = DefaultProcessRunner,
    private val flatpakBinary: String = FlatpakSettings.flatpakBinary,
    private val configDir: File = GtkPreviewConfig.configDir,
) {
    private val log = Log.getInstance(GtkBuilderToolRunner::class.java)

    /** Directory that holds the downloaded renderer binary and its cache marker. */
    private val binDir: File
        get() = configDir.resolve("bin")

    /**
     * Returns `true` when the renderer must be downloaded/updated.
     */
    fun needsCompilation(): Boolean = !GtkHeadlessRendererDownloader.isCurrent(binDir)

    /**
     * Ensures the renderer binary is available and returns its path.
     *
     * The precompiled renderer is downloaded via [GtkHeadlessRendererDownloader].
     *
     * @return path to the renderer binary
     * @throws IllegalStateException if download or validation fails
     */
    fun compile(): File {
        log.info("Ensuring GTK preview renderer")
        val binary = GtkHeadlessRendererDownloader.ensureBinary(binDir)
        if (!binary.canExecute()) {
            error("Renderer binary is not executable: ${binary.absolutePath}")
        }
        return binary
    }

    /**
     * Renders a `.ui` file to a PNG image.
     *
     * A headless Mutter compositor is started on the host with a virtual
     * monitor of exactly [width] × [height].  The compiled renderer runs
     * inside the GNOME SDK Flatpak and connects to the private Wayland
     * display — no window appears on the user's desktop.  The sandbox env is
     * pinned (`GDK_BACKEND=wayland`, `WAYLAND_DISPLAY`, `XDG_RUNTIME_DIR`) and
     * the socket is verified before launch so GTK can never fall back to the
     * user's session display (which would flash a window on screen).
     *
     * @param binary  path to the compiled renderer (from [compile])
     * @param uiFile  the `.ui` file to render
     * @param outputPng destination path for the resulting PNG
     * @param width   render width in pixels (default 800)
     * @param height  render height in pixels (default 600)
     * @param searchDir directory the C compiler scans for sibling `.ui`
     *   files that may define custom classes referenced by [uiFile]
     *   (`src_dir` positional arg; `null` makes the compiler use the
     *   input file's own directory)
     * @return [outputPng] on success
     * @throws IllegalStateException if mutter or the renderer fails
     */
    fun render(
        binary: File,
        uiFile: Path,
        outputPng: Path,
        width: Int = 800,
        height: Int = 600,
        searchDir: Path? = null,
    ): Path {
        val branch =
            detectSdkBranch()
                ?: error("No org.gnome.Sdk installation found")

        val compositor = MutterHeadlessCompositor()
        try {
            compositor.start(width, height)
            // Fail fast if the socket the renderer will resolve inside the
            // sandbox (`$XDG_RUNTIME_DIR/$WAYLAND_DISPLAY`) is not visible.
            checkRenderSocket(compositor)

            log.info("Rendering ${uiFile.fileName} → ${outputPng.fileName}")

            val cmd =
                renderCommand(
                    flatpakBinary = flatpakBinary,
                    branch = branch,
                    binary = binary,
                    uiFile = uiFile,
                    outputPng = outputPng,
                    width = width,
                    height = height,
                    searchDir = searchDir,
                    display = compositor.display,
                    runtimeDir = compositor.runtimeDir,
                )

            val result =
                runner.run(cmd, timeoutMs = TIMEOUT_MS) ?: error("Renderer process timed out or could not be started")

            if (!result.succeeded) {
                error("Renderer failed (exit ${result.exitCode}): ${result.stderr.ifBlank { result.stdout }}")
            }

            if (!outputPng.toFile().exists()) {
                error("Renderer exited successfully but output file was not created: $outputPng")
            }

            return outputPng
        } finally {
            compositor.stop()
        }
    }

    /**
     * Fail-fast guard for the sandboxed renderer: verifies the compositor
     * socket at the exact path the renderer will resolve inside the SDK
     * (`$XDG_RUNTIME_DIR/$WAYLAND_DISPLAY`). A real `connect()` proves a live
     * listening mutter behind the socket — a stale leftover socket file no
     * longer passes the guard, and GTK can never fall back to the user's
     * session display (which would flash a window before rendering).
     */
    private fun checkRenderSocket(compositor: MutterHeadlessCompositor) {
        val socket = Paths.get(compositor.runtimeDir, compositor.display)
        if (!isSocketListening(socket)) {
            error(
                "Headless compositor socket $socket is not reachable from the sandbox; " +
                    "refusing to render so GTK cannot fall back to the session display",
            )
        }
        log.info("Sandbox renderer socket verified: $socket")
    }

    /** True when a process is actually listening on the given AF_UNIX socket path. */
    private fun isSocketListening(socket: Path): Boolean =
        try {
            SocketChannel
                .open(StandardProtocolFamily.UNIX)
                .use { it.connect(UnixDomainSocketAddress.of(socket)) }
            true
        } catch (e: IOException) {
            log.info("Socket liveness check failed for $socket: ${e.message}")
            false
        }



    /**
     * Builds the sandboxed renderer invocation: a `flatpak run` of the compiled
     * binary inside the GNOME SDK, pinned to the headless compositor's private
     * Wayland display (no fallback to the user's session display). The
     * `--socket=wayland` is required so flatpak keeps `--env=WAYLAND_DISPLAY`
     * intact (without a granted socket flatpak strips it from the sandbox env
     * and GDK falls back to the session socket). Pure command construction,
     * unit-testable without a flatpak install.
     */
    internal fun renderCommand(
        flatpakBinary: String,
        branch: String,
        binary: File,
        uiFile: Path,
        outputPng: Path,
        width: Int,
        height: Int,
        searchDir: Path?,
        display: String,
        runtimeDir: String,
    ): List<String> =
        FlatpakSandboxRunner.run(
            flatpakBinary = flatpakBinary,
            appRef = "${GirSdkLocator.SDK_APP_ID}//$branch",
            flatpakOptions =
                buildList {
                    add("--socket=wayland")
                    add("--filesystem=host")
                    add("--filesystem=$runtimeDir")
                    add("--env=GDK_BACKEND=wayland")
                    add("--env=WAYLAND_DISPLAY=$display")
                    add("--env=XDG_RUNTIME_DIR=$runtimeDir")
                    add("--command=${binary.absolutePath}")
                },
            refArguments =
                buildList {
                    add(uiFile.toAbsolutePath().toString())
                    add(outputPng.toAbsolutePath().toString())
                    add(width.toString())
                    add(height.toString())
                    searchDir?.let { add(it.toAbsolutePath().toString()) }
                },
        )

    /**
     * Detects the installed [GirSdkLocator.SDK_APP_ID] branch by querying the
     * flatpak CLI, reusing [GirSdkLocator.pickBranch] so the schema locator
     * and the preview tool select the same branch. The result is cached across
     * runner instances (a fresh runner is created per render) so a session pays
     * one `flatpak list` instead of one per render.
     */
    internal fun detectSdkBranch(): String? {
        cachedBranch?.let { return it }

        val result =
            runner.run(
                listOf(flatpakBinary, "list", "--runtime", "--columns=application,branch,installation"),
                timeoutMs = 5_000,
            ) ?: return null

        val branch =
            GirSdkLocator.pickBranch(
                parseFlatpakRuntimeList(result.stdout),
                GirSdkLocator.SDK_APP_ID,
                null,
            )

        cachedBranch = branch
        return branch
    }

    companion object {
        private const val TIMEOUT_MS = 30_000L

        /** SDK branch shared across runner instances; reset is test-only. */
        @Volatile
        private var cachedBranch: String? = null

        internal fun clearCachedBranchForTests() {
            cachedBranch = null
        }
    }
}
