package io.github.andrepg.gtk.schema.gir.parser

import io.github.andrepg.gtk.schema.gir.GtkSchemaProgress
import io.github.andrepg.gtk.schema.gir.GtkSchemaStep
import io.github.andrepg.shared.log.Log
import org.w3c.dom.Element
import java.io.File
import java.util.concurrent.CancellationException
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses GIR (GObject Introspection) XML files shipped with the GNOME SDK into
 * a [Registry] of classes/interfaces with their properties and signals,
 * flattened-ready for XSD rendering.
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

    private fun generateTypeEntry(
        namespace: String,
        element: Element,
        implements: List<String>,
    ): TypeEntry {
        val name = element.getAttribute("name")
        val cType = element.getAttributeNS(NS_C, "type").ifEmpty { namespace + name }
        val parent = element.getAttribute("parent").ifEmpty { null }

//        val implements = children(element, NS_CORE, "implements").map { it.getAttribute("name") }
        val properties = children(element, NS_CORE, "property").map { it.getAttribute("name") }
        val signals = children(element, NS_GLIB, "signal").map { it.getAttribute("name") }

        return TypeEntry(
            namespace = namespace,
            name = name,
            cType = cType,
            parent = parent,
            requires = implements,
            properties = properties.toSet(),
            signals = signals.toSet(),
        )
    }

    // ---------------------------------------------------------------- Parsing

    private fun parseGir(file: File): List<TypeEntry> {
        val factory = DocumentBuilderFactory.newInstance().apply { this.isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(file)

        val namespace = children(document.documentElement, NS_CORE, "namespace").firstOrNull() ?: return emptyList()
        val namespaceName = namespace.getAttribute("name")

        val entries = mutableListOf<TypeEntry>()

        val gtkClasses = children(namespace, NS_CORE, "class")
        val gtkInterfaces = children(namespace, NS_CORE, "interface")

        gtkClasses.forEach { cls ->
            val implements =
                children(cls, NS_CORE, "implements").map {
                    it.getAttribute("name")
                }

            entries += generateTypeEntry(namespaceName, cls, implements)
        }

        gtkInterfaces.forEach { cls ->
            val requires =
                buildList {
                    cls.getAttribute("prerequisite").takeIf { it.isNotEmpty() }?.let { add(it) }
                    children(cls, NS_CORE, "interface").forEach { add(it.getAttribute("name")) }
                }

            entries += generateTypeEntry(namespaceName, cls, requires)
        }

        return entries
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
                    parseGir(file)
                }
            }
        return Registry(entries)
    }
}
