package io.github.andrepg.gtk.schema.providers

import com.intellij.openapi.vfs.VirtualFile
import io.github.andrepg.gtk.schema.SdkHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File

class GtkSdkHintResolverTest {
    @Test
    fun `resolve returns hint for GNOME SDK manifest`() {
        val manifests = listOf(
            manifest("""{"app-id":"org.example.App","sdk":"org.gnome.Sdk//50"}"""),
            manifest("""{"app-id":"org.example.App","runtime":"org.freedesktop.Platform"}"""),
        )
        assertEquals(SdkHint("org.gnome.Sdk", "50"), GtkSdkHintResolver.resolveFromManifests(manifests))
    }

    @Test
    fun `resolve returns hint for GNOME runtime manifest`() {
        val manifests = listOf(
            manifest("""{"app-id":"org.example.App","runtime":"org.freedesktop.Platform"}"""),
            manifest("""{"app-id":"org.example.App","runtime":"org.gnome.Platform//50"}"""),
        )
        assertEquals(SdkHint("org.gnome.Platform", "50"), GtkSdkHintResolver.resolveFromManifests(manifests))
    }

    @Test
    fun `resolve returns null for non-GNOME manifest`() {
        val manifests = listOf(
            manifest("""{"app-id":"org.example.App","runtime":"org.freedesktop.Platform"}"""),
        )
        assertNull(GtkSdkHintResolver.resolveFromManifests(manifests))
    }

    @Test
    fun `resolve returns null for manifest-less project`() {
        assertNull(GtkSdkHintResolver.resolveFromManifests(emptyList()))
    }

    private fun manifest(content: String): Pair<VirtualFile, String> {
        val file = File.createTempFile("manifest", ".json")
        file.writeText(content)
        file.deleteOnExit()
        val virtualFile = mock(VirtualFile::class.java)
        `when`(virtualFile.path).thenReturn(file.path)
        return virtualFile to "org.example.App"
    }
}
