package io.github.andrepg.gtk.preview

import io.github.andrepg.flatpak.settings.FlatpakSettings
import io.github.andrepg.shared.log.Log
import io.github.andrepg.shared.process.CommandRunner
import io.github.andrepg.shared.process.DefaultProcessRunner
import io.github.andrepg.shared.process.parseFlatpakRuntimeList
import java.io.File
import java.nio.file.Path

/**
 * Compiles and invokes the headless GTK4 `.ui` → PNG renderer.
 *
 * The renderer is a small C program (`gtk-preview-render.c`) shipped as a
 * classpath resource.  On first use it is extracted and compiled inside the
 * GNOME Flatpak SDK via `flatpak run`, which provides the GTK4/libadwaita
 * headers and libraries without requiring `-devel` packages on the host.
 * The resulting binary is cached in [configDir] so subsequent invocations
 * skip compilation entirely.
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
        extractSource()

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

    private fun extractSource() {
        val target = configDir.resolve("gtk-preview-render.c")
        val stream =
            javaClass.getResourceAsStream(SOURCE_RESOURCE)
                ?: error("Renderer source not found on classpath: $SOURCE_RESOURCE")
        stream.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    /** SHA-256 of the shipped C source, used to detect stale cache binaries. */
    private fun sourceHash(): String {
        val stream =
            javaClass.getResourceAsStream(SOURCE_RESOURCE)
                ?: error("Renderer source not found on classpath: $SOURCE_RESOURCE")
        return stream.use { input ->
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    private fun compileInSdk(
        binary: File,
        branch: String,
    ) {
        val source = configDir.resolve("gtk-preview-render.c")
        val compileScript =
            buildString {
                append("gcc -o ")
                append(binary.absolutePath)
                append(" ")
                append(source.absolutePath)
                append(" \$(pkg-config --cflags gtk4 libadwaita-1)")
                append(" ")
                append("\$(pkg-config --libs gtk4 libadwaita-1)")
            }

        val cmd =
            flatpakRun(
                env = mapOf("PKG_CONFIG_PATH" to PKG_CONFIG_PATH),
                command = "/usr/bin/bash",
                branch = branch,
                args = arrayOf("-c", compileScript),
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
     * Detects the installed `org.gnome.Sdk` branch by querying the flatpak CLI.
     * Returns the highest numeric branch, or null when no SDK is found.
     */
    private fun detectSdkBranch(): String? {
        val cached = detectedBranch
        if (cached != null) return cached

        val result =
            runner.run(
                listOf(flatpakBinary, "list", "--runtime", "--columns=application,branch"),
                timeoutMs = 5_000,
            ) ?: return null

        val branch =
            parseFlatpakRuntimeList(result.stdout)
                .filter { it.appId == "org.gnome.Sdk" }
                .mapNotNull { it.branch.toIntOrNull() }
                .maxOrNull()
                ?.toString()

        detectedBranch = branch
        return branch
    }

    private var detectedBranch: String? = null

    /**
     * Builds a `flatpak run` command targeting the GNOME SDK.
     */
    private fun flatpakRun(
        env: Map<String, String> = emptyMap(),
        extraFilesystems: List<String> = emptyList(),
        command: String,
        branch: String,
        vararg args: String,
    ): List<String> =
        buildList {
            add(flatpakBinary)
            add("run")
            add("--filesystem=home")
            extraFilesystems.forEach { fs -> add("--filesystem=$fs") }
            env.forEach { (k, v) -> add("--env=$k=$v") }
            add("--command=$command")
            add("org.gnome.Sdk//$branch")
            addAll(args)
        }

    companion object {
        private const val SOURCE_RESOURCE = "/gtk-preview-render.c"
        private const val BINARY_NAME = "gtk-preview-render"
        private const val HASH_NAME = "gtk-preview-render.hash"
        private const val TIMEOUT_MS = 30_000L
        private const val PKG_CONFIG_PATH =
            "/usr/lib/x86_64-linux-gnu/pkgconfig:/usr/share/pkgconfig"
    }
}
