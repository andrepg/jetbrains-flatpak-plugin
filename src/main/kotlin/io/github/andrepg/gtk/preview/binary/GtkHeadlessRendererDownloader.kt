package io.github.andrepg.gtk.preview.binary

import io.github.andrepg.gtk.preview.GtkPreviewConfig
import io.github.andrepg.shared.log.Log
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration

/**
 * Downloads and validates the precompiled gtk-headless-preview binary from GitHub releases.
 *
 * The release assets are addressed through the fixed `/releases/latest/download/` prefix, so
 * there is no GitHub API round-trip and no URL construction per release tag. Because that
 * prefix always points at the newest release, [RENDERER_VERSION] is a purely local cache key:
 * bump it whenever the plugin should adopt a newer renderer binary.
 *
 * Assets are published per architecture and GNOME version
 * (`gtk-headless-preview-<arch>-gnome-<branch>`), so the cache is keyed by both: a project
 * whose GNOME SDK is branch 50 gets the GNOME 50 build, and an ARM machine gets the aarch64
 * build — the binary always matches the SDK it is executed in.
 */
internal object GtkHeadlessRendererDownloader {
    private val log = Log.getInstance(GtkHeadlessRendererDownloader::class.java)

    /** Local cache key; bump it when a newer renderer release must be adopted. */
    const val RENDERER_VERSION = "0.0.1"

    /** Fixed download prefix for the assets of the newest release. */
    private const val RELEASE_BASE_URL = "https://github.com/andrepg/gtk-headless-renderer/releases/latest/download"

    private const val BINARY_NAME = GtkPreviewConfig.RENDERER_BINARY_NAME

    private const val USER_AGENT = "jetbrains-flatpak-plugin"

    /** Serializes downloads so two projects cannot clobber the same temp/cache file. */
    private val downloadLock = Any()

    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build()
    }

    /**
     * Resolves the release architecture for the current host.
     *
     * @throws IllegalStateException when the host architecture has no published renderer
     */
    internal fun hostArch(): String =
        when (val arch = System.getProperty("os.arch").orEmpty().lowercase()) {
            "amd64", "x86_64", "x64" -> "x86_64"
            "aarch64", "arm64" -> "aarch64"
            else -> error(
                "The GTK preview renderer is not available for architecture '$arch' " +
                    "(supported: x86_64, aarch64)",
            )
        }

    /** Release asset name for [arch] on the GNOME [gnomeVersion] branch. */
    internal fun assetName(
        arch: String,
        gnomeVersion: String,
    ): String = "$BINARY_NAME-$arch-gnome-$gnomeVersion"

    /** Local executable for [arch] on the GNOME [gnomeVersion] branch. */
    internal fun binaryFile(
        binDir: File,
        arch: String,
        gnomeVersion: String,
    ): File = File(binDir, assetName(arch, gnomeVersion))

    /** Cache marker for [binaryFile]; one marker per architecture/branch pair. */
    internal fun markerFile(
        binDir: File,
        arch: String,
        gnomeVersion: String,
    ): File = File(binDir, "${assetName(arch, gnomeVersion)}.version")

    /**
     * Ensures the renderer binary for [arch]/[gnomeVersion] is present and valid in [binDir]
     * and returns its path.
     *
     * @throws IllegalStateException if the architecture is unsupported, the asset is missing
     *   for that GNOME branch, the checksum does not match, or the file cannot be made executable
     */
    fun ensureBinary(
        binDir: File,
        arch: String,
        gnomeVersion: String,
    ): File = ensureBinary(binDir, RELEASE_BASE_URL, arch, gnomeVersion)

    /**
     * [ensureBinary] with an explicit release base URL, so tests can point it at a local server.
     */
    internal fun ensureBinary(
        binDir: File,
        baseUrl: String,
        arch: String,
        gnomeVersion: String,
    ): File =
        synchronized(downloadLock) {
            check(binDir.mkdirs() || binDir.isDirectory) { "Could not create ${binDir.absolutePath}" }
            removeLegacyCache(binDir)

            val binary = binaryFile(binDir, arch, gnomeVersion)
            if (isCurrent(binDir, arch, gnomeVersion)) {
                log.debug("${binary.name} already present at ${binary.absolutePath}")
                return@synchronized binary
            }

            val asset = assetName(arch, gnomeVersion)
            log.info("Downloading GTK preview renderer $asset (cache key $RENDERER_VERSION)...")
            val temp = File(binDir, "$asset.download-${ProcessHandle.current().pid()}-${System.nanoTime()}")
            try {
                download("$baseUrl/$asset", temp, asset, arch, gnomeVersion)
                val checksum =
                    downloadText("$baseUrl/$asset.sha256", asset, arch, gnomeVersion)
                verifySha256(temp, checksum)
                Files.move(temp.toPath(), binary.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                runCatching { Files.deleteIfExists(temp.toPath()) }
            }

            makeExecutable(binary)
            writeMarker(markerFile(binDir, arch, gnomeVersion))
            log.info("${binary.name} installed at ${binary.absolutePath}")
            binary
        }

    /**
     * True when an executable binary with the current [RENDERER_VERSION] marker exists for
     * [arch]/[gnomeVersion].
     */
    internal fun isCurrent(
        binDir: File,
        arch: String,
        gnomeVersion: String,
    ): Boolean {
        val binary = binaryFile(binDir, arch, gnomeVersion)
        if (!binary.isFile || !binary.canExecute()) return false
        val marker = markerFile(binDir, arch, gnomeVersion)
        val markerVersion = if (marker.exists()) runCatching { marker.readText().trim() }.getOrNull() else null
        return markerVersion == RENDERER_VERSION
    }

    /**
     * Removes pre-variant cache files (`gtk-headless-preview` and `.version`) left by older
     * plugin builds that shipped a single hardcoded renderer.
     */
    private fun removeLegacyCache(binDir: File) {
        listOf(File(binDir, BINARY_NAME), File(binDir, "$BINARY_NAME.version")).forEach { legacy ->
            if (legacy.isFile) {
                runCatching { Files.deleteIfExists(legacy.toPath()) }
                    .onSuccess { log.debug("Removed legacy renderer cache ${legacy.name}") }
            }
        }
    }

    private fun download(
        url: String,
        targetFile: File,
        asset: String,
        arch: String,
        gnomeVersion: String,
    ) {
        val response =
            try {
                send(url, HttpResponse.BodyHandlers.ofInputStream())
            } catch (e: Exception) {
                throw downloadFailure(asset, arch, gnomeVersion, e)
            }
        targetFile.parentFile?.mkdirs()
        response.body().use { input ->
            Files.newOutputStream(targetFile.toPath()).use { output -> input.copyTo(output) }
        }
    }

    private fun downloadText(
        url: String,
        asset: String,
        arch: String,
        gnomeVersion: String,
    ): String =
        try {
            send(url, HttpResponse.BodyHandlers.ofString()).body()
        } catch (e: Exception) {
            throw downloadFailure(asset, arch, gnomeVersion, e)
        }

    private fun downloadFailure(
        asset: String,
        arch: String,
        gnomeVersion: String,
        cause: Exception,
    ): IllegalStateException =
        IllegalStateException(
            "Could not download the GTK preview renderer '$asset' for $arch on GNOME $gnomeVersion. " +
                "Make sure a renderer build exists for that GNOME SDK branch. ${cause.message.orEmpty()}",
            cause,
        )

    private fun <T> send(
        url: String,
        handler: HttpResponse.BodyHandler<T>,
    ): HttpResponse<T> {
        val request =
            HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build()

        val response =
            try {
                httpClient.send(request, handler)
            } catch (e: Exception) {
                throw IllegalStateException("Failed to download $url: ${e.message}", e)
            }

        if (response.statusCode() >= 400) {
            throw IllegalStateException("Failed to download $url: HTTP ${response.statusCode()}")
        }
        return response
    }

    /**
     * Accepts either a bare hash or the `hash  filename` shape GitHub ships.
     */
    private fun verifySha256(
        binaryFile: File,
        checksumContent: String,
    ) {
        val expectedHash = checksumContent.trim().split("\\s+".toRegex()).first().lowercase()
        if (expectedHash.isEmpty()) {
            throw IllegalStateException("Empty SHA256 checksum for ${binaryFile.name}")
        }
        val actualHash = calculateSha256(binaryFile).lowercase()
        if (expectedHash != actualHash) {
            throw IllegalStateException(
                "SHA256 checksum mismatch for ${binaryFile.name}. Expected $expectedHash, got $actualHash",
            )
        }
        log.debug("SHA256 checksum verified for ${binaryFile.name}")
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file.toPath()).use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * @throws IllegalStateException when the file cannot be made executable — the marker is
     *   intentionally not written, so the cache never reports a broken binary as current
     */
    private fun makeExecutable(file: File) {
        if (!file.setExecutable(true, false) || !file.canExecute()) {
            throw IllegalStateException(
                "Cannot make ${file.absolutePath} executable. " +
                    "The plugin config directory may be mounted with 'noexec'.",
            )
        }
    }

    private fun writeMarker(marker: File) {
        try {
            marker.writeText(RENDERER_VERSION)
        } catch (e: Exception) {
            log.warn("Failed to write version marker: ${e.message}")
        }
    }
}
