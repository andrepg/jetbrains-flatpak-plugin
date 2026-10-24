package io.github.andrepg.gtk.preview.binary

import com.sun.net.httpserver.HttpServer
import io.github.andrepg.gtk.preview.GtkPreviewConfig
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

class GtkHeadlessRendererDownloaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: HttpServer
    private val requests = AtomicInteger()
    private val paths = mutableListOf<String>()
    private var payload = ByteArray(0)
    private var checksumContent: String? = null

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            paths += exchange.requestURI.path
            requests.incrementAndGet()

            val body: ByteArray
            val status: Int
            if (exchange.requestURI.path.endsWith(".sha256")) {
                val checksum = checksumContent
                if (checksum == null) {
                    status = 404
                    body = ByteArray(0)
                } else {
                    status = 200
                    body = checksum.toByteArray()
                }
            } else {
                status = 200
                body = payload
            }

            exchange.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
            if (body.isNotEmpty()) {
                exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
        server.start()
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    private val baseUrl: String
        get() = "http://127.0.0.1:${server.address.port}"

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun markerFile(binDir: File): File =
        File(binDir, "${GtkPreviewConfig.RENDERER_BINARY_NAME}.version")

    @Test
    fun `downloads verifies and caches the renderer`() {
        payload = "binary-content".toByteArray()
        checksumContent = "${sha256(payload)}  gtk-headless-preview-x86_64-gnome-51"
        val binDir = tmp.newFolder("bin")

        val binary = GtkHeadlessRendererDownloader.ensureBinary(binDir, baseUrl)

        assertTrue("binary missing", binary.isFile)
        assertTrue("binary not executable", binary.canExecute())
        assertArrayEquals(payload, Files.readAllBytes(binary.toPath()))
        assertEquals(
            GtkHeadlessRendererDownloader.RENDERER_VERSION,
            markerFile(binDir).readText().trim(),
        )
        assertEquals("expected binary + checksum downloads", 2, requests.get())
        assertTrue("checksum asset not requested: $paths", paths.any { it.endsWith(".sha256") })
        assertTrue("temporary download left behind: $paths", paths.none { it.endsWith(".download") })
        assertFalse(File(binDir, "${GtkPreviewConfig.RENDERER_BINARY_NAME}.download").exists())
    }

    @Test
    fun `does not hit the network when the cache is current`() {
        payload = "binary-content".toByteArray()
        checksumContent = sha256(payload)
        val binDir = tmp.newFolder("bin")

        GtkHeadlessRendererDownloader.ensureBinary(binDir, baseUrl)
        val requestsAfterFirst = requests.get()
        GtkHeadlessRendererDownloader.ensureBinary(binDir, baseUrl)

        assertTrue(GtkHeadlessRendererDownloader.isCurrent(binDir))
        assertEquals("second call must reuse the cache", requestsAfterFirst, requests.get())
    }

    @Test
    fun `re-downloads when the cache marker is stale`() {
        payload = "binary-content".toByteArray()
        checksumContent = sha256(payload)
        val binDir = tmp.newFolder("bin")

        GtkHeadlessRendererDownloader.ensureBinary(binDir, baseUrl)
        markerFile(binDir).writeText("0.0.0")
        assertFalse(GtkHeadlessRendererDownloader.isCurrent(binDir))

        GtkHeadlessRendererDownloader.ensureBinary(binDir, baseUrl)

        assertTrue(GtkHeadlessRendererDownloader.isCurrent(binDir))
        assertEquals("expected a second binary + checksum download", 4, requests.get())
    }

    @Test
    fun `rejects a binary whose checksum does not match`() {
        payload = "binary-content".toByteArray()
        checksumContent = sha256("tampered".toByteArray())
        val binDir = tmp.newFolder("bin")

        assertThrows(IllegalStateException::class.java) {
            GtkHeadlessRendererDownloader.ensureBinary(binDir, baseUrl)
        }

        assertFalse("rejected binary must not be cached", File(binDir, GtkPreviewConfig.RENDERER_BINARY_NAME).exists())
        assertFalse("marker must not be written", markerFile(binDir).exists())
        assertFalse(
            "temporary download must be cleaned up",
            File(binDir, "${GtkPreviewConfig.RENDERER_BINARY_NAME}.download").exists(),
        )
    }
}
