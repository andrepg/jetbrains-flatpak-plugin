package io.github.andrepg.flatpak.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class FlatpakManifestReaderTest {
    @Test
    fun `reads app-id from JSON manifest`() {
        val jsonManifest = File("test-data/valid-manifest.json").absoluteFile
        val appId = FlatpakManifestReader.readAppId(jsonManifest.path)
        assertEquals("org.example.MyApp", appId)
    }

    @Test
    fun `reads id from YAML manifest`() {
        val yamlContent = """
            id: org.example.MyApp
            runtime: org.freedesktop.Platform
            runtime-version: '22.08'
            sdk: org.freedesktop.Sdk
        """
        val yamlFile = File.createTempFile("manifest", ".yaml").apply {
            writeText(yamlContent)
            deleteOnExit()
        }
        val appId = FlatpakManifestReader.readAppId(yamlFile.path)
        assertEquals("org.example.MyApp", appId)
    }

    @Test
    fun `returns null for missing file`() {
        val appId = FlatpakManifestReader.readAppId("nonexistent-file.json")
        assertEquals(null, appId)
    }
}