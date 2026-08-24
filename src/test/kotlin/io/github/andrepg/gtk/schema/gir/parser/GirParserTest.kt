package io.github.andrepg.gtk.schema.gir.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GirParserTest {
    private val girDir = File("test-data/gir").absoluteFile

    @Test
    fun `parseAll indexes classes interfaces and namespaces`() {
        val registry = GirParser.parseAll(girDir)
        val types = registry.allTypes()
        assertEquals(7, types.size)
        val cTypes = types.map { it.cType }.toSet()
        assertTrue(
            cTypes.containsAll(
                setOf("GtkButton", "GtkWidget", "GtkActionable", "GtkBuildable", "AdwHeaderBar", "AdwBin", "GObject"),
            ),
        )
    }

    @Test
    fun `flattened members traverse parent and interface across namespaces`() {
        val registry = GirParser.parseAll(girDir)
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
        val registry = GirParser.parseAll(girDir)
        val headerBar = registry.resolve("Adw", "HeaderBar") ?: error("Adw.HeaderBar missing")

        val properties = registry.flattened(headerBar) { it.properties }
        assertEquals(
            listOf("hexpand", "name", "show-back-button", "title-widget", "visible"),
            properties,
        )
    }

    @Test
    fun `flattened traverses same-namespace interface prerequisites`() {
        val registry = GirParser.parseAll(girDir)
        val actionable = registry.resolve("Gtk", "Actionable") ?: error("Gtk.Actionable missing")

        assertEquals(listOf("action-name", "hexpand", "name", "visible"), registry.flattened(actionable) { it.properties })
        assertEquals(listOf("destroy", "map"), registry.flattened(actionable) { it.signals })
    }

    @Test
    fun `flattened resolves interface prerequisites across namespaces and files`() {
        val registry = GirParser.parseAll(girDir)
        val bin = registry.resolve("Adw", "Bin") ?: error("Adw.Bin missing")

        assertEquals(
            listOf("buildable-id", "child", "name"),
            registry.flattened(bin) { it.properties },
        )
        assertEquals(listOf("notify"), registry.flattened(bin) { it.signals })
    }
}
