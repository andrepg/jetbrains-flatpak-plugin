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
        GirSchemaExtractor.generateXsd(girDir) {
            steps += it
            true
        }

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
            GirSchemaExtractor.generateXsd(girDir) {
                reported++
                false
            }
        }
        assertEquals(1, reported)
    }
}
