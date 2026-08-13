package io.github.andrepg.flatpak.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FlatpakProjectDetectorTest {

    @Test
    fun `matches reverse-DNS manifest filenames`() {
        assertTrue(FlatpakProjectDetector.isCandidateName("org.example.app.json"))
        assertTrue(FlatpakProjectDetector.isCandidateName("org.gnome.builder.yaml"))
        assertTrue(FlatpakProjectDetector.isCandidateName("com.github.user.tool.yml"))
    }

    @Test
    fun `rejects non reverse-DNS filenames`() {
        assertFalse(FlatpakProjectDetector.isCandidateName("org.example.json"))
        assertFalse(FlatpakProjectDetector.isCandidateName("example.json"))
        assertFalse(FlatpakProjectDetector.isCandidateName("org_example_app.json"))
        assertFalse(FlatpakProjectDetector.isCandidateName("org.example.app.txt"))
    }

    @Test
    fun `matches flatpak and manifest prefixed names`() {
        assertTrue(FlatpakProjectDetector.isCandidateName("manifest.json"))
        assertTrue(FlatpakProjectDetector.isCandidateName("flatpak.json"))
        assertTrue(FlatpakProjectDetector.isCandidateName("flatpak-manifest.yaml"))
        assertTrue(FlatpakProjectDetector.isCandidateName("manifest.yml"))
    }

    @Test
    fun `filters out non manifest extensions`() {
        assertFalse(FlatpakProjectDetector.isCandidateName("manifest.txt"))
        assertFalse(FlatpakProjectDetector.isCandidateName("flatpak.xml"))
        assertFalse(FlatpakProjectDetector.isCandidateName("org.example.app.json5"))
    }

    @Test
    fun `reads app-id from a matching candidate file`() {
        val appId = FlatpakProjectDetector.readAppIdFromCandidate(
            "org.example.app.json",
            File("test-data/valid-manifest.json").absolutePath
        )
        assertEquals("org.example.MyApp", appId)
    }

    @Test
    fun `does not read content when the name does not match`() {
        val appId = FlatpakProjectDetector.readAppIdFromCandidate(
            "random.json",
            File("test-data/valid-manifest.json").absolutePath
        )
        assertNull(appId)
    }

    @Test
    fun `returns null when the manifest has no app-id`() {
        val temp = File.createTempFile("manifest", ".json").apply {
            writeText("""{"runtime":"org.freedesktop.Platform","sdk":"org.freedesktop.Sdk"}""")
            deleteOnExit()
        }
        val appId = FlatpakProjectDetector.readAppIdFromCandidate("org.example.app.json", temp.path)
        assertNull(appId)
    }
}
