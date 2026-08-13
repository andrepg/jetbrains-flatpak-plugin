package io.github.andrepg.gtk.schema.gir

import io.github.andrepg.gtk.schema.locator.GirSdkLocator
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.system.exitProcess

/**
 * Extracts a JSON Schema (draft-07) for GtkBuilder `.ui` files from the GObject
 * Introspection (GIR) XML descriptions shipped with the GNOME SDK.
 *
 * The schema validates the GtkBuilder syntax (`<interface>`, `<object>`,
 * `<property>`, `<signal>`, `<child>`, ...) and, more importantly, enumerates
 * the actual class names (e.g. `GtkButton`, `AdwHeaderBar`, `GtkSourceView`)
 * together with the properties and signals each class exposes, flattened
 * through inheritance and implemented interfaces (including cross-namespace
 * parents such as `GObject.Object` and `Gio.Application`).
 *
 * The core is JDK-only (no IntelliJ/Flatpak imports) so it can run both inside
 * the IDE (runtime schema generation) and from the `extractGtkSchema` Gradle task.
 *
 * Usage (CLI):
 *   GirSchemaExtractorKt [--gir-dir <girDir|sdkBaseDir>] [--schema-out <output.json>]
 *
 * `girDir` may point directly at a directory containing the `.gir` files or at
 * an SDK runtime base directory (the first nested `files/share/gir-1.0` that
 * holds the GIR files is used). When `--gir-dir` is omitted the installed GNOME
 * SDK is located automatically (via [GirSdkLocator]); missing optional GIR files
 * (e.g. `GtkSource-5.gir`) are skipped with a warning.
 */
object GirSchemaExtractor {

    private const val NS_CORE = "http://www.gtk.org/introspection/core/1.0"
    private const val NS_C = "http://www.gtk.org/introspection/c/1.0"
    private const val NS_GLIB = "http://www.gtk.org/introspection/glib/1.0"

    private val GIR_FILE_NAMES = listOf(
        "Gtk-4.0.gir",
        "GtkSource-5.gir",
        "Adw-1.gir",
        "GObject-2.0.gir",
        "Gio-2.0.gir",
    )

    private const val FALLBACK_FLATPAK_BINARY = "/usr/bin/flatpak"
    private const val SDK_APP_ID = "org.gnome.Sdk"

    /**
     * A class or interface declared by a GIR namespace.
     *
     * @property namespace the GIR namespace name (e.g. `Gtk`, `Adw`)
     * @property name the type name within the namespace (e.g. `Box`)
     * @property cType the C type name used by GtkBuilder in `class="..."`
     * @property parent the raw `parent` attribute (`Name` or `Ns.Name`), classes only
     * @property requires raw references (`implements`, `prerequisite`) to interfaces
     */
    data class TypeEntry(
        val namespace: String,
        val name: String,
        val cType: String,
        val parent: String?,
        val requires: List<String>,
        val properties: Set<String>,
        val signals: Set<String>,
    ) {
        val key: String get() = "$namespace.$name"
    }

    /**
     * Index of all parsed types, keyed by `namespace.name`, with helpers to
     * resolve inheritance/interface chains and flatten their members.
     */
    class Registry(entries: List<TypeEntry>) {
        private val byKey = LinkedHashMap<String, TypeEntry>()

        init {
            entries.forEach { byKey[it.key] = it }
        }

        /** Resolves a parent/implements reference against [fromNamespace]. */
        fun resolve(fromNamespace: String, ref: String): TypeEntry? =
            byKey[if ('.' in ref) ref else "$fromNamespace.$ref"]

        /** All types, deduplicated and merged by C type, sorted by C type. */
        fun allTypes(): List<TypeEntry> {
            val byCType = LinkedHashMap<String, TypeEntry>()
            for (entry in byKey.values) {
                val previous = byCType[entry.cType]
                byCType[entry.cType] = if (previous == null) entry else previous.copy(
                    properties = previous.properties + entry.properties,
                    signals = previous.signals + entry.signals,
                )
            }
            return byCType.values.sortedBy { it.cType }
        }

        /**
         * Collects [pick] (properties or signals) from [root] and everything it
         * inherits or implements, walking parents and interface references
         * recursively across namespaces.
         */
        fun flattened(root: TypeEntry, pick: (TypeEntry) -> Set<String>): List<String> {
            val visited = mutableSetOf<String>()
            val result = LinkedHashSet<String>()

            fun walk(entry: TypeEntry) {
                if (!visited.add(entry.key)) return
                result += pick(entry)
                entry.parent?.let { resolve(entry.namespace, it)?.let(::walk) }
                entry.requires.forEach { resolve(entry.namespace, it)?.let(::walk) }
            }

            walk(root)
            return result.sorted()
        }
    }

    // ------------------------------------------------------------------ JSON

    internal sealed class Js {
        data class Str(val value: String) : Js()
        data class Arr(val items: List<Js>) : Js()
        data class Obj(val entries: List<Pair<String, Js>>) : Js()

        fun render(): String {
            val sb = StringBuilder()
            write(sb)
            return sb.toString()
        }

        private fun write(sb: StringBuilder) {
            when (this) {
                is Str -> writeString(sb, value)
                is Arr -> {
                    sb.append('[')
                    items.forEachIndexed { index, item ->
                        if (index > 0) sb.append(',')
                        item.write(sb)
                    }
                    sb.append(']')
                }
                is Obj -> {
                    sb.append('{')
                    entries.forEachIndexed { index, (key, value) ->
                        if (index > 0) sb.append(',')
                        writeString(sb, key)
                        sb.append(':')
                        value.write(sb)
                    }
                    sb.append('}')
                }
            }
        }

        private fun writeString(sb: StringBuilder, value: String) {
            sb.append('"')
            for (ch in value) {
                when (ch) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    else -> if (ch.code < 0x20) sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
                }
            }
            sb.append('"')
        }
    }

    private fun obj(vararg entries: Pair<String, Js>) = Js.Obj(entries.toList())
    private fun arr(vararg items: Js) = Js.Arr(items.toList())
    private fun str(value: String) = Js.Str(value)
    private fun ref(path: String) = obj("\$ref" to str(path))
    private fun strEnum(vararg values: String) = obj("enum" to arr(*values.map { str(it) }.toTypedArray()))
    private fun yesNoEnum() = strEnum("yes", "no", "true", "false")

    // ----------------------------------------------------------- Schema model

    private fun buildSchema(registry: Registry): Js {
        val allTypes = registry.allTypes()
        val allClassNames = allTypes.map { it.cType }

        val classVariants = allTypes.map { type ->
            obj(
                "type" to str("object"),
                "properties" to obj(
                    "class" to obj("const" to str(type.cType)),
                    "property" to propertyArray(registry.flattened(type) { it.properties }),
                    "signal" to signalArray(registry.flattened(type) { it.signals }),
                ),
            )
        }

        return obj(
            "\$schema" to str("http://json-schema.org/draft-07/schema#"),
            "\$id" to str("urn:io.github.andrepg:flatpak-support:schemas:gtk-ui"),
            "title" to str("GtkBuilder UI Layout (GTK 4 + Libadwaita + GtkSource-5)"),
            "description" to str(
                "Generated by the Flatpak DevTools extractGtkSchema task from the " +
                    "GObject Introspection (GIR) files of the GNOME SDK " +
                    "(Gtk-4.0.gir, GtkSource-5.gir, Adw-1.gir, GObject-2.0.gir, Gio-2.0.gir). " +
                    "Class, property and signal names are flattened through inheritance " +
                    "and implemented interfaces."
            ),
            "type" to str("object"),
            "properties" to obj(
                "interface" to ref("#/\$defs/interface"),
            ),
            "\$defs" to obj(
                "interface" to interfaceDef(),
                "requires" to requiresDef(),
                "object" to objectDef(classVariants),
                "objectCommon" to objectCommonDef(),
                "template" to templateDef(allClassNames),
                "property" to genericPropertyDef(),
                "signal" to genericSignalDef(),
                "child" to childDef(),
                "menu" to menuDef(),
                "menuItem" to menuItemDef(),
                "menuSection" to menuSectionDef(),
                "menuAttribute" to menuAttributeDef(),
                "layout" to layoutDef(),
                "packing" to packingDef(),
                "accessibility" to accessibilityDef(),
                "style" to styleDef(),
            ),
        )
    }

    private fun interfaceDef() = obj(
        "type" to str("object"),
        "properties" to obj(
            "requires" to obj("type" to str("array"), "items" to ref("#/\$defs/requires")),
            "object" to ref("#/\$defs/object"),
            "template" to ref("#/\$defs/template"),
            "menu" to obj("type" to str("array"), "items" to ref("#/\$defs/menu")),
        ),
    )

    private fun requiresDef() = obj(
        "type" to str("object"),
        "properties" to obj(
            "lib" to obj("type" to str("string")),
            "version" to obj("type" to str("string")),
        ),
    )

    private fun objectDef(variants: List<Js>) = obj(
        "type" to str("array"),
        "items" to obj(
            "allOf" to arr(
                ref("#/\$defs/objectCommon"),
                obj("anyOf" to Js.Arr(variants)),
            ),
        ),
    )

    private fun objectCommonDef() = obj(
        "type" to str("object"),
        "required" to arr(str("class")),
        "properties" to obj(
            "id" to obj("type" to str("string")),
            "child" to obj("type" to str("array"), "items" to ref("#/\$defs/child")),
            "layout" to ref("#/\$defs/layout"),
            "packing" to ref("#/\$defs/packing"),
            "accessibility" to ref("#/\$defs/accessibility"),
            "style" to ref("#/\$defs/style"),
        ),
    )

    private fun templateDef(allClassNames: List<String>) = obj(
        "type" to str("object"),
        "properties" to obj(
            "class" to obj("type" to str("string")),
            "parent" to strEnum(*allClassNames.toTypedArray()),
            "property" to ref("#/\$defs/property"),
            "signal" to ref("#/\$defs/signal"),
            "child" to obj("type" to str("array"), "items" to ref("#/\$defs/child")),
        ),
    )

    private fun propertyArray(names: List<String>) = obj(
        "type" to str("array"),
        "items" to obj(
            "type" to str("object"),
            "properties" to obj(
                "name" to strEnum(*names.toTypedArray()),
                "$" to obj("type" to str("string")),
                "translatable" to yesNoEnum(),
                "context" to obj("type" to str("string")),
                "comments" to obj("type" to str("string")),
            ),
        ),
    )

    private fun signalArray(names: List<String>) = obj(
        "type" to str("array"),
        "items" to obj(
            "type" to str("object"),
            "properties" to obj(
                "name" to strEnum(*names.toTypedArray()),
                "handler" to obj("type" to str("string")),
                "object" to obj("type" to str("string")),
                "swapped" to yesNoEnum(),
                "after" to yesNoEnum(),
            ),
        ),
    )

    private fun genericPropertyDef() = obj(
        "type" to str("object"),
        "properties" to obj(
            "name" to obj("type" to str("string")),
            "$" to obj("type" to str("string")),
            "translatable" to yesNoEnum(),
            "context" to obj("type" to str("string")),
            "comments" to obj("type" to str("string")),
        ),
    )

    private fun genericSignalDef() = obj(
        "type" to str("object"),
        "properties" to obj(
            "name" to obj("type" to str("string")),
            "handler" to obj("type" to str("string")),
            "object" to obj("type" to str("string")),
            "swapped" to yesNoEnum(),
            "after" to yesNoEnum(),
        ),
    )

    private fun childDef() = obj(
        "type" to str("object"),
        "properties" to obj(
            "type" to obj("type" to str("string")),
            "object" to ref("#/\$defs/object"),
            "layout" to ref("#/\$defs/layout"),
            "packing" to ref("#/\$defs/packing"),
        ),
    )

    private fun menuDef() = obj(
        "type" to str("object"),
        "properties" to obj(
            "id" to obj("type" to str("string")),
            "attribute" to obj("type" to str("array"), "items" to ref("#/\$defs/menuAttribute")),
            "item" to obj("type" to str("array"), "items" to ref("#/\$defs/menuItem")),
            "section" to obj("type" to str("array"), "items" to ref("#/\$defs/menuSection")),
        ),
    )

    private fun menuItemDef() = obj(
        "type" to str("object"),
        "properties" to obj(
            "attribute" to obj("type" to str("array"), "items" to ref("#/\$defs/menuAttribute")),
            "item" to obj("type" to str("array"), "items" to ref("#/\$defs/menuItem")),
            "submenu" to obj("type" to str("array"), "items" to ref("#/\$defs/menuItem")),
            "section" to obj("type" to str("array"), "items" to ref("#/\$defs/menuSection")),
        ),
    )

    private fun menuSectionDef() = obj(
        "type" to str("object"),
        "properties" to obj(
            "attribute" to obj("type" to str("array"), "items" to ref("#/\$defs/menuAttribute")),
            "item" to obj("type" to str("array"), "items" to ref("#/\$defs/menuItem")),
        ),
    )

    private fun menuAttributeDef() = obj(
        "type" to str("object"),
        "properties" to obj(
            "name" to obj("type" to str("string")),
            "$" to obj("type" to str("string")),
        ),
    )

    private fun layoutDef() = obj(
        "type" to str("object"),
        "properties" to obj(
            "property" to obj("type" to str("array"), "items" to ref("#/\$defs/property")),
        ),
    )

    private fun packingDef() = obj(
        "type" to str("object"),
        "properties" to obj(
            "property" to obj("type" to str("array"), "items" to ref("#/\$defs/property")),
        ),
    )

    private fun accessibilityDef() = obj(
        "type" to str("object"),
        "properties" to obj(
            "property" to obj("type" to str("array"), "items" to ref("#/\$defs/property")),
            "relation" to obj(
                "type" to str("array"),
                "items" to obj(
                    "type" to str("object"),
                    "properties" to obj(
                        "target" to obj("type" to str("string")),
                        "$" to obj("type" to str("string")),
                    ),
                ),
            ),
        ),
    )

    private fun styleDef() = obj(
        "type" to str("object"),
        "properties" to obj(
            "class" to obj(
                "type" to str("array"),
                "items" to obj(
                    "type" to str("object"),
                    "properties" to obj("name" to obj("type" to str("string"))),
                ),
            ),
            "node" to obj(
                "type" to str("array"),
                "items" to obj(
                    "type" to str("object"),
                    "properties" to obj(
                        "id" to obj("type" to str("string")),
                        "class" to obj(
                            "type" to str("array"),
                            "items" to obj(
                                "type" to str("object"),
                                "properties" to obj("name" to obj("type" to str("string"))),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    // ---------------------------------------------------------------- Parsing

    private fun parseGir(file: File): List<TypeEntry> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val document = factory.newDocumentBuilder().parse(file)
        val namespace = children(document.documentElement, NS_CORE, "namespace").firstOrNull()
            ?: return emptyList()
        val namespaceName = namespace.getAttribute("name")

        val entries = mutableListOf<TypeEntry>()

        for (cls in children(namespace, NS_CORE, "class")) {
            val name = cls.getAttribute("name")
            val cType = cls.getAttributeNS(NS_C, "type").ifEmpty { namespaceName + name }
            val parent = cls.getAttribute("parent").ifEmpty { null }
            val implements = children(cls, NS_CORE, "implements").map { it.getAttribute("name") }
            entries += TypeEntry(
                namespace = namespaceName,
                name = name,
                cType = cType,
                parent = parent,
                requires = implements,
                properties = children(cls, NS_CORE, "property").map { it.getAttribute("name") }.toSet(),
                signals = children(cls, NS_GLIB, "signal").map { it.getAttribute("name") }.toSet(),
            )
        }

        for (iface in children(namespace, NS_CORE, "interface")) {
            val name = iface.getAttribute("name")
            val cType = iface.getAttributeNS(NS_C, "type").ifEmpty { namespaceName + name }
            val requires = buildList {
                iface.getAttribute("prerequisite").takeIf { it.isNotEmpty() }?.let { add(it) }
                children(iface, NS_CORE, "interface").forEach { add(it.getAttribute("name")) }
            }
            entries += TypeEntry(
                namespace = namespaceName,
                name = name,
                cType = cType,
                parent = null,
                requires = requires,
                properties = children(iface, NS_CORE, "property").map { it.getAttribute("name") }.toSet(),
                signals = children(iface, NS_GLIB, "signal").map { it.getAttribute("name") }.toSet(),
            )
        }

        return entries
    }

    private fun children(element: Element, namespaceUri: String, tag: String): List<Element> {
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

    internal fun resolveGirDir(candidate: File): File? {
        if (File(candidate, "Gtk-4.0.gir").isFile) return candidate
        candidate.listFiles()?.asSequence()?.filter { it.isDirectory }?.forEach { commit ->
            val gir = File(commit, "files/share/gir-1.0")
            if (File(gir, "Gtk-4.0.gir").isFile) return gir
        }
        return null
    }

    /**
     * Parses every present GIR file in [girDir] into a [Registry]. Missing
     * optional files (e.g. `GtkSource-5.gir`) are skipped with a warning.
     */
    internal fun parseAll(girDir: File): Registry {
        val entries = GIR_FILE_NAMES.flatMap { name ->
            val file = File(girDir, name)
            if (!file.isFile) {
                System.err.println("WARNING: $name not found in $girDir; skipping")
                emptyList()
            } else {
                parseGir(file)
            }
        }
        return Registry(entries)
    }

    internal fun buildEnums(registry: Registry): SchemaPatches.GtkEnums {
        val allTypes = registry.allTypes()
        return SchemaPatches.GtkEnums(
            classNames = allTypes.map { it.cType },
            propertyNames = allTypes
                .flatMap { registry.flattened(it) { e -> e.properties } }
                .distinct()
                .sorted(),
            signalNames = allTypes
                .flatMap { registry.flattened(it) { e -> e.signals } }
                .distinct()
                .sorted(),
        )
    }

    internal fun renderXsd(registry: Registry, enums: SchemaPatches.GtkEnums): String =
        SchemaPatches.applyXsd(buildXsd(), enums)

    /**
     * Generates the patched GtkBuilder XSD string from the GIR files under
     * [girDir]. The dir is resolved through [resolveGirDir]; fails fast when no
     * `Gtk-4.0.gir` is present.
     */
    internal fun generateXsd(girDir: File): String {
        val resolved = resolveGirDir(girDir)
            ?: error("No Gtk-4.0.gir found under $girDir. Pass the gir-1.0 dir or the GNOME SDK runtime base dir, or override with -PgirDir=")
        val registry = parseAll(resolved)
        return renderXsd(registry, buildEnums(registry))
    }

    private fun buildXsd(): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">")
        sb.appendLine("  <xs:annotation>")
        sb.appendLine("    <xs:documentation>")
        sb.appendLine("      GtkBuilder UI layout schema for GTK 4 + Libadwaita + GtkSource-5.")
        sb.appendLine("      Generated from the GObject Introspection (GIR) files of the GNOME SDK")
        sb.appendLine("      by the Flatpak DevTools extractGtkSchema task.")
        sb.appendLine("      No target namespace: applies to plain (namespace-less) GtkBuilder .ui files.")
        sb.appendLine("    </xs:documentation>")
        sb.appendLine("  </xs:annotation>")

        sb.appendLine("  <xs:element name=\"interface\">")
        sb.appendLine("    <xs:complexType>")
        sb.appendLine("      <xs:choice minOccurs=\"0\" maxOccurs=\"unbounded\">")
        sb.appendLine("        <xs:element ref=\"requires\"/>")
        sb.appendLine("        <xs:element ref=\"object\"/>")
        sb.appendLine("        <xs:element ref=\"template\"/>")
        sb.appendLine("        <xs:element ref=\"menu\"/>")
        sb.appendLine("      </xs:choice>")
        sb.appendLine("    </xs:complexType>")
        sb.appendLine("  </xs:element>")

        sb.appendLine("  <xs:element name=\"requires\">")
        sb.appendLine("    <xs:complexType>")
        sb.appendLine("      <xs:attribute name=\"lib\" type=\"xs:string\" use=\"required\"/>")
        sb.appendLine("      <xs:attribute name=\"version\" type=\"xs:string\" use=\"required\"/>")
        sb.appendLine("    </xs:complexType>")
        sb.appendLine("  </xs:element>")

        sb.appendLine("  <xs:element name=\"object\" type=\"objectType\"/>")
        sb.appendLine()
        sb.appendLine("<!-- gb-patch:class-name-union -->")
        sb.appendLine("  <xs:complexType name=\"objectType\">")
        sb.appendLine("    <xs:choice minOccurs=\"0\" maxOccurs=\"unbounded\">")
        sb.appendLine("      <xs:element ref=\"condition\"/>")
        sb.appendLine("      <xs:element ref=\"setter\"/>")
        sb.appendLine("      <xs:element ref=\"property\"/>")
        sb.appendLine("      <xs:element ref=\"signal\"/>")
        sb.appendLine("      <xs:element ref=\"child\"/>")
        sb.appendLine("      <xs:element ref=\"layout\"/>")
        sb.appendLine("      <xs:element ref=\"packing\"/>")
        sb.appendLine("      <xs:element ref=\"accessibility\"/>")
        sb.appendLine("      <xs:element ref=\"style\"/>")
        sb.appendLine("      <xs:element ref=\"attributes\"/>")
        sb.appendLine("    </xs:choice>")
        sb.appendLine("    <xs:attribute name=\"class\" type=\"className\" use=\"required\"/>")
        sb.appendLine("    <xs:attribute name=\"id\" type=\"xs:string\"/>")
        sb.appendLine("  </xs:complexType>")
        sb.appendLine("<!-- gb-patch:property-element -->")
        sb.appendLine("<!-- gb-patch:signal-element -->")
        sb.appendLine("  <xs:element name=\"child\">")
        sb.appendLine("    <xs:complexType>")
        sb.appendLine("      <xs:sequence>")
        sb.appendLine("        <xs:element ref=\"object\" minOccurs=\"1\" maxOccurs=\"1\"/>")
        sb.appendLine("        <xs:element ref=\"layout\" minOccurs=\"0\"/>")
        sb.appendLine("        <xs:element ref=\"packing\" minOccurs=\"0\"/>")
        sb.appendLine("      </xs:sequence>")
        sb.appendLine("      <xs:attribute name=\"type\" type=\"xs:string\"/>")
        sb.appendLine("    </xs:complexType>")
        sb.appendLine("  </xs:element>")

        sb.appendLine("  <xs:element name=\"template\">")
        sb.appendLine("    <xs:complexType>")
        sb.appendLine("      <xs:sequence>")
        sb.appendLine("        <xs:element ref=\"property\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("        <xs:element ref=\"signal\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("        <xs:element ref=\"child\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("      </xs:sequence>")
        sb.appendLine("      <xs:attribute name=\"class\" type=\"xs:string\" use=\"required\"/>")
        sb.appendLine("      <xs:attribute name=\"parent\" type=\"className\" use=\"required\"/>")
        sb.appendLine("    </xs:complexType>")
        sb.appendLine("  </xs:element>")

        sb.appendLine("  <xs:element name=\"layout\">")
        sb.appendLine("    <xs:complexType>")
        sb.appendLine("      <xs:sequence>")
        sb.appendLine("        <xs:element ref=\"property\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("      </xs:sequence>")
        sb.appendLine("    </xs:complexType>")
        sb.appendLine("  </xs:element>")

        sb.appendLine("  <xs:element name=\"packing\">")
        sb.appendLine("    <xs:complexType>")
        sb.appendLine("      <xs:sequence>")
        sb.appendLine("        <xs:element ref=\"property\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("      </xs:sequence>")
        sb.appendLine("    </xs:complexType>")
        sb.appendLine("  </xs:element>")

        sb.appendLine("  <xs:element name=\"accessibility\">")
        sb.appendLine("    <xs:complexType>")
        sb.appendLine("      <xs:sequence>")
        sb.appendLine("        <xs:element ref=\"property\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("        <xs:element ref=\"relation\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("      </xs:sequence>")
        sb.appendLine("    </xs:complexType>")
        sb.appendLine("  </xs:element>")

        sb.appendLine("  <xs:element name=\"relation\">")
        sb.appendLine("    <xs:complexType mixed=\"true\">")
        sb.appendLine("      <xs:attribute name=\"target\" type=\"xs:string\"/>")
        sb.appendLine("    </xs:complexType>")
        sb.appendLine("  </xs:element>")

        sb.appendLine("  <xs:element name=\"condition\">")
        sb.appendLine("    <xs:complexType mixed=\"true\"/>")
        sb.appendLine("  </xs:element>")

        sb.appendLine("  <xs:element name=\"setter\">")
        sb.appendLine("    <xs:complexType mixed=\"true\">")
        sb.appendLine("      <xs:attribute name=\"object\" type=\"xs:string\"/>")
        sb.appendLine("      <xs:attribute name=\"property\" type=\"xs:string\" use=\"required\"/>")
        sb.appendLine("    </xs:complexType>")
        sb.appendLine("  </xs:element>")

        sb.appendLine("  <xs:element name=\"style\">")
        sb.appendLine("    <xs:complexType>")
        sb.appendLine("      <xs:sequence>")
        sb.appendLine("        <xs:element ref=\"class\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("        <xs:element ref=\"node\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("      </xs:sequence>")
        sb.appendLine("    </xs:complexType>")
        sb.appendLine("  </xs:element>")

        sb.appendLine("  <xs:element name=\"node\">")
        sb.appendLine("    <xs:complexType>")
        sb.appendLine("      <xs:sequence>")
        sb.appendLine("        <xs:element ref=\"class\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("      </xs:sequence>")
        sb.appendLine("      <xs:attribute name=\"id\" type=\"xs:string\"/>")
        sb.appendLine("    </xs:complexType>")
        sb.appendLine("  </xs:element>")

        sb.appendLine("  <xs:element name=\"class\">")
        sb.appendLine("    <xs:complexType>")
        sb.appendLine("      <xs:attribute name=\"name\" type=\"xs:string\" use=\"required\"/>")
        sb.appendLine("    </xs:complexType>")
        sb.appendLine("  </xs:element>")

        sb.appendLine("  <xs:element name=\"menu\">")
        sb.appendLine("    <xs:complexType>")
        sb.appendLine("      <xs:sequence>")
        sb.appendLine("        <xs:element ref=\"attribute\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("        <xs:element ref=\"item\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("        <xs:element ref=\"section\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("      </xs:sequence>")
        sb.appendLine("      <xs:attribute name=\"id\" type=\"xs:string\"/>")
        sb.appendLine("    </xs:complexType>")
        sb.appendLine("  </xs:element>")

        sb.appendLine("  <xs:element name=\"item\">")
        sb.appendLine("    <xs:complexType>")
        sb.appendLine("      <xs:sequence>")
        sb.appendLine("        <xs:element ref=\"attribute\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("        <xs:element ref=\"item\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("        <xs:element ref=\"submenu\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("        <xs:element ref=\"section\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("      </xs:sequence>")
        sb.appendLine("    </xs:complexType>")
        sb.appendLine("  </xs:element>")

        sb.appendLine("  <xs:element name=\"submenu\">")
        sb.appendLine("    <xs:complexType>")
        sb.appendLine("      <xs:sequence>")
        sb.appendLine("        <xs:element ref=\"item\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("        <xs:element ref=\"section\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("      </xs:sequence>")
        sb.appendLine("    </xs:complexType>")
        sb.appendLine("  </xs:element>")

        sb.appendLine("  <xs:element name=\"section\">")
        sb.appendLine("    <xs:complexType>")
        sb.appendLine("      <xs:sequence>")
        sb.appendLine("        <xs:element ref=\"attribute\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("        <xs:element ref=\"item\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("      </xs:sequence>")
        sb.appendLine("    </xs:complexType>")
        sb.appendLine("  </xs:element>")

        sb.appendLine("  <xs:element name=\"attributes\">")
        sb.appendLine("    <xs:complexType>")
        sb.appendLine("      <xs:sequence>")
        sb.appendLine("        <xs:element ref=\"attribute\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>")
        sb.appendLine("      </xs:sequence>")
        sb.appendLine("    </xs:complexType>")
        sb.appendLine("  </xs:element>")

        sb.appendLine("  <xs:element name=\"attribute\">")
        sb.appendLine("    <xs:complexType mixed=\"true\">")
        sb.appendLine("      <xs:attribute name=\"name\" type=\"xs:string\" use=\"required\"/>")
        sb.appendLine("      <xs:attribute name=\"value\" type=\"xs:string\"/>")
        sb.appendLine("      <xs:attribute name=\"translatable\">")
        sb.appendLine("        <xs:simpleType>")
        sb.appendLine("          <xs:restriction base=\"xs:string\">")
        sb.appendLine("            <xs:enumeration value=\"yes\"/>")
        sb.appendLine("            <xs:enumeration value=\"no\"/>")
        sb.appendLine("            <xs:enumeration value=\"true\"/>")
        sb.appendLine("            <xs:enumeration value=\"false\"/>")
        sb.appendLine("          </xs:restriction>")
        sb.appendLine("        </xs:simpleType>")
        sb.appendLine("      </xs:attribute>")
        sb.appendLine("      <xs:attribute name=\"context\" type=\"xs:string\"/>")
        sb.appendLine("    </xs:complexType>")
        sb.appendLine("  </xs:element>")

        sb.appendLine("</xs:schema>")
        return sb.toString()
    }

    /**
     * Generates the bundled artifacts from a GNOME SDK: `gtk-ui-schema.json`
     * and the sibling `gtk-ui.xsd`. Used by the `extractGtkSchema` Gradle task
     * and the CLI entry point.
     */
    fun extract(girDir: File, output: File) {
        val resolved = resolveGirDir(girDir)
            ?: error("No Gtk-4.0.gir found under $girDir. Pass the gir-1.0 dir or the GNOME SDK runtime base dir, or override with -PgirDir=")

        val registry = parseAll(resolved)
        val enums = buildEnums(registry)

        output.parentFile?.mkdirs()
        output.writeText(SchemaPatches.applyJson(buildSchema(registry)).render())

        val xsdOutput = File(output.parentFile, "gtk-ui.xsd")
        xsdOutput.writeText(renderXsd(registry, enums))

        val allTypes = registry.allTypes()
        val propertyCount = allTypes.sumOf { registry.flattened(it) { e -> e.properties }.size }
        val signalCount = allTypes.sumOf { registry.flattened(it) { e -> e.signals }.size }
        println("GIR dir : $resolved")
        println("Output  : ${output.absolutePath}")
        println("XSD     : ${xsdOutput.absolutePath}")
        println("Types   : ${allTypes.size} classes/interfaces")
        println("Flattened members: $propertyCount properties, $signalCount signals")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val girDirArg = parseArg(args, "--gir-dir")
        val output = parseArg(args, "--schema-out")?.let(::File)
            ?: File("src/main/resources/schemas/gtk-ui-schema.json")

        val girDir = girDirArg?.let(::File) ?: autoDetectGirDir()
        if (girDir == null) {
            System.err.println("Usage: GirSchemaExtractorKt [--gir-dir <girDir|sdkBaseDir>] [--schema-out <output.json>]")
            System.err.println("No --gir-dir given and none auto-detected from the installed GNOME SDK.")
            exitProcess(2)
        }
        extract(girDir, output)
    }

    private fun parseArg(args: Array<String>, name: String): String? {
        val index = args.indexOf(name)
        return if (index >= 0 && index + 1 < args.size) args[index + 1] else null
    }

    private fun autoDetectGirDir(): File? =
        GirSdkLocator.locate(SDK_APP_ID, branchHint = null, flatpakBinary = FALLBACK_FLATPAK_BINARY)
}
