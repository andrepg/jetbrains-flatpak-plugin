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
 */
internal object GtkHeadlessRendererDownloader {
    private val log = Log.getInstance(GtkHeadlessRendererDownloader::class.java)

    /** Local cache key; bump it when a newer renderer release must be adopted. */
    const val RENDERER_VERSION = "0.0.1"

    /** Fixed download prefix for the assets of the newest release. */
    private const val RELEASE_BASE_URL = "https://github.com/andrepg/gtk-headless-renderer/releases/latest/download"

    // Hardcoded for now as requested
    private const val TARGET_ARCH = "x86_64"
    private const val GNOME_VERSION = "51"
    private const val BINARY_NAME = GtkPreviewConfig.RENDERER_BINARY_NAME
    private const val ASSET_NAME = "gtk-headless-preview-$TARGET_ARCH-gnome-$GNOME_VERSION"

    private const val USER_AGENT = "jetbrains-flatpak-plugin"

    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build()
    }

    /**
     * Ensures the renderer binary is present and valid in [binDir].
     *
     * @param binDir directory that holds the binary and its `.version` cache marker
     * @return path to the executable binary
     * @throws IllegalStateException if the download, checksum verification or move fails
     */
    fun ensureBinary(binDir: File = GtkPreviewConfig.binDir): File = ensureBinary(binDir, RELEASE_BASE_URL)

    /**
     * [ensureBinary] with an explicit release base URL, so tests can point it at a local server.
     */
    internal fun ensureBinary(binDir: File, baseUrl: String): File {
        binDir.mkdirs()
        val binary = File(binDir, BINARY_NAME)
        if (isCurrent(binDir)) {
            log.debug("$BINARY_NAME ($TARGET_ARCH, GNOME $GNOME_VERSION) already present at ${binary.absolutePath}")
            return binary
        }

        log.info("Downloading GTK preview renderer $ASSET_NAME (cache key $RENDERER_VERSION)...")
        val temp = File(binDir, "$BINARY_NAME.download")
        try {
            download("$baseUrl/$ASSET_NAME", temp)
            verifySha256(temp, downloadText("$baseUrl/$ASSET_NAME.sha256"))
            Files.move(temp.toPath(), binary.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            runCatching { Files.deleteIfExists(temp.toPath()) }
        }

        makeExecutable(binary)
        writeMarker(markerFile(binDir))
        log.info("$BINARY_NAME installed at ${binary.absolutePath}")
        return binary
    }

    /**
     * True when an executable binary with the current [RENDERER_VERSION] marker is cached.
     */
    internal fun isCurrent(binDir: File = GtkPreviewConfig.binDir): Boolean {
        val binary = File(binDir, BINARY_NAME)
        if (!binary.isFile || !binary.canExecute()) return false
        val marker = markerFile(binDir)
        val markerVersion = if (marker.exists()) runCatching { marker.readText().trim() }.getOrNull() else null
        return markerVersion == RENDERER_VERSION
    }

    private fun markerFile(binDir: File): File = File(binDir, "$BINARY_NAME.version")

    private fun download(url: String, targetFile: File) {
        val response = send(url, HttpResponse.BodyHandlers.ofInputStream())
        targetFile.parentFile?.mkdirs()
        response.body().use { input ->
            Files.newOutputStream(targetFile.toPath()).use { output -> input.copyTo(output) }
        }
    }

    private fun downloadText(url: String): String = send(url, HttpResponse.BodyHandlers.ofString()).body()

    private fun <T> send(url: String, handler: HttpResponse.BodyHandler<T>): HttpResponse<T> {
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
    private fun verifySha256(binaryFile: File, checksumContent: String) {
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

    private fun makeExecutable(file: File) {
        if (!file.setExecutable(true, false)) {
            log.warn("Failed to set executable permission on ${file.absolutePath}")
        }
    }

    private fun writeMarker(marker: File) {
        try {
            marker.writeText(RENDERER_VERSION)
        } catch (e: Exception) {
            log.debug("Failed to write version marker: ${e.message}")
        }
    }
}
