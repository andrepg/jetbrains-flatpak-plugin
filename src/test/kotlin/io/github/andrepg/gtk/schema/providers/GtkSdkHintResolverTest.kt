package io.github.andrepg.gtk.schema.providers

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.andrepg.flatpak.detection.FlatpakProjectDetector
import io.github.andrepg.flatpak.utils.FlatpakManifestReader
import io.github.andrepg.gtk.schema.SdkHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class GtkSdkHintResolverTest {
    @Test
    fun `resolve returns hint for GNOME SDK manifest`() {
        val project = mockProject(
            manifest("org.gnome.Sdk//50", "org.example.App"),
            manifest("org.freedesktop.Platform", "org.example.App"),
        )
        assertEquals(SdkHint("org.gnome.Sdk", "50"), GtkSdkHintResolver.resolve(project))
    }

    @Test
    fun `resolve returns hint for GNOME runtime manifest`() {
        val project = mockProject(
            manifest("org.freedesktop.Platform", "org.example.App"),
            manifest("org.gnome.Platform//50", "org.example.App"),
        )
        assertEquals(SdkHint("org.gnome.Platform", "50"), GtkSdkHintResolver.resolve(project))
    }

    @Test
    fun `resolve returns null for non-GNOME manifest`() {
        val project = mockProject(
            manifest("org.freedesktop.Platform", "org.example.App"),
        )
        assertNull(GtkSdkHintResolver.resolve(project))
    }

    @Test
    fun `resolve returns null for manifest-less project`() {
        val project = mockProject()
        assertNull(GtkSdkHintResolver.resolve(project))
    }

    private fun mockProject(vararg manifests: Pair<String, String>): Project {
        val project = mock(Project::class.java)
        val files = manifests.map { (content, appId) ->
            mockFile(content, appId)
        }
        `when`(FlatpakProjectDetector.findManifests(project)).thenReturn(files)
        return project
    }

    private fun manifest(content: String, appId: String): Pair<String, String> = content to appId

    private fun mockFile(content: String, appId: String): Pair<VirtualFile, String> {
        val file = mock(VirtualFile::class.java)
        `when`(file.path).thenReturn("/path/to/manifest.json")
        `when`(FlatpakManifestReader.readSdk(file.path)).thenReturn(content)
        `when`(FlatpakManifestReader.readRuntime(file.path)).thenReturn(content)
        `when`(FlatpakManifestReader.readAppId(file.path)).thenReturn(appId)
        return file to appId
    }
}
