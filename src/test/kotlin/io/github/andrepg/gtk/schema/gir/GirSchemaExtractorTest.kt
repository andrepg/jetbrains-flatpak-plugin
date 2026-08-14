package io.github.andrepg.gtk.schema.gir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CancellationException

class GirSchemaExtractorTest {

    private val girDir = File("test-data/gir").absoluteFile

    @Test
    fun `resolveGirDir accepts a gir-1_0 dir directly`() {
        assertEquals(girDir, GirSchemaExtractor.resolveGirDir(girDir))
    }

    @Test
    fun `resolveGirDir descends into an SDK base dir`() {
        val base = createTempDir()
        try {
            val girDir = base.resolve("commit1/files/share/gir-1.0").apply { mkdirs() }
            File("test-data/gir").listFiles()?.forEach { girDir.resolve(it.name).writeText(it.readText()) }
            assertEquals(girDir, GirSchemaExtractor.resolveGirDir(base))
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `resolveGirDir returns null for unrelated dirs`() {
        assertEquals(null, GirSchemaExtractor.resolveGirDir(File(girDir, "nonexistent")))
    }

    @Test
    fun `parseAll indexes classes interfaces and namespaces`() {
        val registry = GirSchemaExtractor.parseAll(girDir)
        val types = registry.allTypes()
        assertEquals(5, types.size)
        val cTypes = types.map { it.cType }.toSet()
        assertTrue(cTypes.containsAll(setOf("GtkButton", "GtkWidget", "GtkActionable", "AdwHeaderBar", "GObject")))
    }

    @Test
    fun `flattened members traverse parent and interface across namespaces`() {
        val registry = GirSchemaExtractor.parseAll(girDir)
        val button = registry.resolve("Gtk", "Button") ?: error("Gtk.Button missing")

        val properties = registry.flattened(button) { it.properties }
        assertEquals(
            listOf("action-name", "always-show-image", "hexpand", "label", "name", "visible"),
            properties,
        )

        val signals = registry.flattened(button) { it.signals }
        assertEquals(listOf("clicked", "destroy", "map"), signals)
    }

    @Test
    fun `flattened members of an Adw class resolve the cross-namespace parent`() {
        val registry = GirSchemaExtractor.parseAll(girDir)
        val headerBar = registry.resolve("Adw", "HeaderBar") ?: error("Adw.HeaderBar missing")

        val properties = registry.flattened(headerBar) { it.properties }
        assertEquals(
            listOf("hexpand", "name", "show-back-button", "title-widget", "visible"),
            properties,
        )
    }

    @Test
    fun `generateXsd produces a patched XSD without markers or placeholders`() {
        val xsd = GirSchemaExtractor.generateXsd(girDir)
        assertTrue(xsd.startsWith("<?xml"))
        assertTrue(xsd.contains("<xs:enumeration value=\"GtkButton\"/>"))
        assertTrue(xsd.contains("<xs:enumeration value=\"AdwHeaderBar\"/>"))
        assertTrue(!xsd.contains("gb-patch:"))
        assertTrue(!xsd.contains("\${"))
    }

    @Test
    fun `generateXsd reports parsing and rendering progress steps`() {
        val steps = mutableListOf<GtkSchemaStep>()
        GirSchemaExtractor.generateXsd(girDir) { steps += it; true }

        assertEquals(
            listOf(
                GtkSchemaStep.Parsing("Gtk-4.0.gir", 1, 5),
                GtkSchemaStep.Parsing("Adw-1.gir", 3, 5),
                GtkSchemaStep.Parsing("GObject-2.0.gir", 4, 5),
                GtkSchemaStep.Rendering,
            ),
            steps,
        )
    }

    @Test
    fun `generateXsd aborts when the progress callback returns false`() {
        var reported = 0
        assertThrows(CancellationException::class.java) {
            GirSchemaExtractor.generateXsd(girDir) { reported++; false }
        }
        assertEquals(1, reported)
    }

    @Test
    fun `extract writes JSON schema and sibling XSD`() {
        val output = File.createTempFile("gtk-ui-schema", ".json")
        output.delete()
        try {
            GirSchemaExtractor.extract(girDir, output)
            assertTrue(output.isFile)
            assertTrue(output.readText().contains("\"GtkButton\""))
            val xsd = File(output.parentFile, "gtk-ui.xsd")
            assertTrue(xsd.isFile)
            assertTrue(xsd.readText().contains("<xs:schema"))
        } finally {
            output.delete()
            File(output.parentFile, "gtk-ui.xsd").delete()
        }
    }
}
