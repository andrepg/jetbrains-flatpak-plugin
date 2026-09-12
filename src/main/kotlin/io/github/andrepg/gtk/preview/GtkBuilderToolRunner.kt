package io.github.andrepg.gtk.preview

import io.github.andrepg.flatpak.runs.commands.FlatpakSandboxRunner
import io.github.andrepg.flatpak.settings.FlatpakSettings
import io.github.andrepg.gtk.schema.locator.GirSdkLocator
import io.github.andrepg.shared.log.Log
import io.github.andrepg.shared.process.CommandRunner
import io.github.andrepg.shared.process.DefaultProcessRunner
import io.github.andrepg.shared.process.parseFlatpakRuntimeList
import java.io.File
import java.nio.file.Path

/**
 * Compiles and invokes the headless GTK4 `.ui` → PNG renderer.
 *
 * The renderer is a small C program split across three source modules plus a
 * Makefile:
 *   - `gtk-preview-render.c` — CLI entry point
 *   - `preview-render.c` — GTK4 load/present/snapshot pipeline
 *   - `ui-xml.c` — pure-GLib XML preprocessing (template rewrite, drop rules)
 *   - `Makefile` — compiles the three sources inside the SDK via `make`
 *
 * All sources are shipped as classpath resources.  On first use they are
 * extracted and compiled together inside the GNOME Flatpak SDK via
 * `flatpak run --command=make`, which provides the GTK4/libadwaita headers
 * and libraries without requiring `-devel` packages on the host.  The
 * resulting binary is cached in [configDir] so subsequent invocations skip
 * compilation entirely.  A SHA-256 over all source files detects stale
 * caches after a plugin update.
 *
 * @property runner process runner used to execute flatpak and shell commands
 * @property flatpakBinary path to the `flatpak` CLI binary (from plugin settings)
 * @property configDir plugin config directory used to cache the compiled binary
 */
class GtkBuilderToolRunner(
    private val runner: CommandRunner = DefaultProcessRunner,
    private val flatpakBinary: String = FlatpakSettings.flatpakBinary,
    private val configDir: File = GtkPreviewConfig.configDir,
) {
    private val log = Log.getInstance(GtkBuilderToolRunner::class.java)

    /**
     * Returns `true` when a binary matching the *current* C source is already
     * cached: the binary exists, is executable, and its recorded source hash
     * equals the hash of the classpath resource.  If the shipped source
     * changes (e.g. a plugin update), the stale binary is recompiled.
     */
    private fun isCurrentBinary(binary: File): Boolean {
        if (!binary.canExecute()) return false
        val hashFile = configDir.resolve(HASH_NAME)
        if (!hashFile.exists()) return false
        val recorded = runCatching { hashFile.readText().trim() }.getOrNull() ?: return false
        return recorded == sourceHash()
    }

    /**
     * Returns `true` when the renderer must be (re)compiled: the binary is
     * missing, not executable, or was built from a different source version.
     */
    fun needsCompilation(): Boolean = !isCurrentBinary(configDir.resolve(BINARY_NAME))

    /**
     * Ensures the renderer binary is compiled and returns its path.
     *
     * If the binary already exists in the cache directory and was built from
     * the current source it is returned immediately.  Otherwise the C source
     * is extracted from the classpath resources and compiled inside the GNOME
     * SDK.
     *
     * @return path to the compiled binary
     * @throws IllegalStateException if the SDK cannot be found or compilation fails
     */
    fun compile(): File {
        val binary = configDir.resolve(BINARY_NAME)
        if (isCurrentBinary(binary)) return binary

        log.info("Compiling GTK preview renderer")
        extractSources()

        val branch =
            detectSdkBranch()
                ?: error(
                    "No org.gnome.Sdk installation found. " +
                        "Install the GNOME SDK via Flatpak: flatpak install org.gnome.Sdk",
                )

        compileInSdk(binary, branch)

        if (!binary.canExecute()) {
            error("Compilation succeeded but binary is not executable: ${binary.absolutePath}")
        }
        configDir.resolve(HASH_NAME).writeText(sourceHash())
        log.info("GTK preview renderer compiled: ${binary.absolutePath}")
        return binary
    }

    /**
     * Renders a `.ui` file to a PNG image.
     *
     * A headless Mutter compositor is started on the host with a virtual
     * monitor of exactly [width] × [height].  The compiled renderer runs
     * inside the GNOME SDK Flatpak and connects to the private Wayland
     * display — no window appears on the user's desktop.
     *
     * @param binary  path to the compiled renderer (from [compile])
     * @param uiFile  the `.ui` file to render
     * @param outputPng destination path for the resulting PNG
     * @param width   render width in pixels (default 800)
     * @param height  render height in pixels (default 600)
     * @return [outputPng] on success
     * @throws IllegalStateException if mutter or the renderer fails
     */
    fun render(
        binary: File,
        uiFile: Path,
        outputPng: Path,
        width: Int = 800,
        height: Int = 600,
    ): Path {
        val branch =
            detectSdkBranch()
                ?: error("No org.gnome.Sdk installation found")

        val compositor = MutterHeadlessCompositor()
        try {
            compositor.start(width, height)

            log.info("Rendering ${uiFile.fileName} → ${outputPng.fileName}")

            val cmd =
                flatpakRun(
                    env =
                        mapOf(
                            "WAYLAND_DISPLAY" to compositor.display,
                        ),
                    extraFilesystems = listOf(compositor.runtimeDir),
                    command = binary.absolutePath,
                    branch = branch,
                    args =
                        arrayOf(
                            uiFile.toAbsolutePath().toString(),
                            outputPng.toAbsolutePath().toString(),
                            width.toString(),
                            height.toString(),
                        ),
                )

            val result = runner.run(cmd, timeoutMs = TIMEOUT_MS)
            if (result == null) {
                error("Renderer process timed out or could not be started")
            }
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

    private fun extractSources() {
        for (resource in SOURCE_RESOURCES) {
            val fileName = resource.removePrefix("/")
            val target = configDir.resolve(fileName)
            val stream =
                javaClass.getResourceAsStream(resource)
                    ?: error("Renderer source not found on classpath: $resource")
            stream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    /** SHA-256 of all shipped source files, used to detect stale cache binaries. */
    private fun sourceHash(): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8 * 1024)
        for (resource in SOURCE_RESOURCES) {
            val stream =
                javaClass.getResourceAsStream(resource)
                    ?: error("Renderer source not found on classpath: $resource")
            stream.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) digest.update(buffer, 0, read)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun compileInSdk(
        binary: File,
        branch: String,
    ) {
        val makeArgs =
            arrayOf(
                "-C",
                configDir.absolutePath,
                "TARGET=${binary.absolutePath}",
            )

        val cmd =
            flatpakRun(
                env = mapOf("PKG_CONFIG_PATH" to PKG_CONFIG_PATH),
                command = "/usr/bin/make",
                branch = branch,
                args = makeArgs,
            )

        val result = runner.run(cmd, timeoutMs = TIMEOUT_MS)
        if (result == null || !result.succeeded) {
            val stderr = result?.stderr.orEmpty()
            val stdout = result?.stdout.orEmpty()
            val output = stderr.ifBlank { stdout }
            error("GTK renderer compilation failed inside the GNOME SDK: $output")
        }
    }

    /**
     * Detects the installed [GirSdkLocator.SDK_APP_ID] branch by querying the
     * flatpak CLI, reusing [GirSdkLocator.pickBranch] so the schema locator
     * and the preview tool select the same branch.
     */
    private fun detectSdkBranch(): String? {
        val cached = detectedBranch
        if (cached != null) return cached

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

        detectedBranch = branch
        return branch
    }

    private var detectedBranch: String? = null

    /**
     * Builds a `flatpak run` command targeting the GNOME SDK via the shared
     * [FlatpakSandboxRunner] so every sandboxed invocation stays consistent.
     */
    private fun flatpakRun(
        env: Map<String, String> = emptyMap(),
        extraFilesystems: List<String> = emptyList(),
        command: String,
        branch: String,
        vararg args: String,
    ): List<String> =
        FlatpakSandboxRunner.run(
            flatpakBinary = flatpakBinary,
            appRef = "${GirSdkLocator.SDK_APP_ID}//$branch",
            flatpakOptions =
                buildList {
                    add("--filesystem=home")
                    extraFilesystems.forEach { fs -> add("--filesystem=$fs") }
                    env.forEach { (k, v) -> add("--env=$k=$v") }
                    add("--command=$command")
                },
            refArguments = args.toList(),
        )

    companion object {
        /** All shipped source files, in a fixed order (used for hashing). */
        private val SOURCE_RESOURCES =
            listOf(
                "/compiler/Makefile",
                "/compiler/ui-xml.h",
                "/compiler/ui-xml.c",
                "/compiler/preview-render.h",
                "/compiler/preview-render.c",
                "/compiler/gtk-preview-render.c",
            )
        private const val BINARY_NAME = "gtk-preview-render"
        private const val HASH_NAME = "gtk-preview-render.hash"
        private const val TIMEOUT_MS = 30_000L
        private const val PKG_CONFIG_PATH =
            "/usr/lib/x86_64-linux-gnu/pkgconfig:/usr/share/pkgconfig"
    }
}
