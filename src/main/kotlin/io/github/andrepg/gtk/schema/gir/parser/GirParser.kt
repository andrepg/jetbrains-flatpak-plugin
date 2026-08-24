package io.github.andrepg.gtk.schema.gir.parser

import io.github.andrepg.gtk.schema.gir.GtkSchemaProgress
import io.github.andrepg.gtk.schema.gir.GtkSchemaStep
import io.github.andrepg.shared.log.Log
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.util.concurrent.CancellationException
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses GIR (GObject Introspection) XML files shipped with the GNOME SDK into
 * a [Registry] of classes/interfaces with their properties and signals,
 * flattened-ready for XSD rendering.
 *
 * Inheritance sources follow the GIR format: `parent` attributes and
 * `<implements>` children on classes, `<prerequisite>` children on interfaces
 * — all resolved across namespaces and GIR files by [Registry.flattened].
 *
 * JDK-only (no IntelliJ imports): it runs inside the IDE through
 * [io.github.andrepg.gtk.schema.GtkSchemaManager] and stays unit-testable.
 *
 * `girDir` is the SDK's `files/share/gir-1.0` directory as resolved by
 * [io.github.andrepg.gtk.schema.locator.GirSdkLocator]. Missing optional GIR
 * files (e.g. `GtkSource-5.gir`) are skipped with a warning.
 */
object GirParser {
    private val log = Log.getInstance(GirParser::class.java)

    private const val NS_CORE = "http://www.gtk.org/introspection/core/1.0"
    private const val NS_C = "http://www.gtk.org/introspection/c/1.0"
    private const val NS_GLIB = "http://www.gtk.org/introspection/glib/1.0"

    /** GIR files parsed by [parseAll], in order; the first is mandatory. */
    val GIR_FILE_NAMES =
        listOf(
            "Gtk-4.0.gir",
            "GtkSource-5.gir",
            "Adw-1.gir",
            "GObject-2.0.gir",
            "Gio-2.0.gir",
        )

    // ---------------------------------------------------------------- Parsing

    private fun buildTypeEntry(
        namespace: String,
        element: Element,
        requires: List<String>,
    ): TypeEntry {
        val name = element.getAttribute("name")
        val cType = element.getAttributeNS(NS_C, "type").ifEmpty { namespace + name }
        val parent = element.getAttribute("parent").ifEmpty { null }
        val properties = children(element, NS_CORE, "property").map { it.getAttribute("name") }
        val signals = children(element, NS_GLIB, "signal").map { it.getAttribute("name") }

        return TypeEntry(
            namespace = namespace,
            name = name,
            cType = cType,
            parent = parent,
            requires = requires,
            properties = properties.toSet(),
            signals = signals.toSet(),
        )
    }

    private fun parseGir(file: File): List<TypeEntry> {
        val document = newDocument(file)

        val namespace = children(document.documentElement, NS_CORE, "namespace").firstOrNull() ?: return emptyList()
        val namespaceName = namespace.getAttribute("name")

        val classes = children(namespace, NS_CORE, "class")
        val interfaces = children(namespace, NS_CORE, "interface")

        return classes.map { cls ->
            buildTypeEntry(namespaceName, cls, children(cls, NS_CORE, "implements").map { it.getAttribute("name") })
        } + interfaces.map { iface ->
            buildTypeEntry(namespaceName, iface, children(iface, NS_CORE, "prerequisite").map { it.getAttribute("name") })
        }
    }

    private fun newDocument(file: File): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        return factory.newDocumentBuilder().parse(file)
    }

    private fun children(
        element: Element,
        namespaceUri: String,
        tag: String,
    ): List<Element> {
        val result = mutableListOf<Element>()
        var node = element.firstChild
        while (node != null) {
            if (node is Element && node.namespaceURI == namespaceUri && node.localName == tag) {
                result += node
            }
            node = node.nextSibling
        }
        return result
    }

    /**
     * Parses every present GIR file in [girDir] into a [Registry]. Missing
     * optional files (e.g. `GtkSource-5.gir`) are skipped with a warning.
     */
    internal fun parseAll(
        girDir: File,
        onProgress: GtkSchemaProgress? = null,
    ): Registry {
        val entries =
            GIR_FILE_NAMES.flatMapIndexed { index, name ->
                val file = File(girDir, name)
                if (!file.isFile) {
                    log.warn("$name not found in $girDir; skipping")
                    emptyList()
                } else {
                    if (onProgress?.report(GtkSchemaStep.Parsing(name, index + 1, GIR_FILE_NAMES.size)) == false) {
                        throw CancellationException("GtkBuilder schema generation cancelled")
                    }
                    try {
                        parseGir(file)
                    } catch (e: Exception) {
                        throw IllegalStateException("Failed to parse $name", e)
                    }
                }
            }
        return Registry(entries)
    }
}
