package io.github.andrepg.gtk.schema.gir

import io.github.andrepg.gtk.schema.gir.GirSchemaExtractor.Js
import io.github.andrepg.shared.text.EscapeTables
import java.util.regex.Matcher.quoteReplacement

/**
 * Curated GtkBuilder grammar patches applied on top of the GIR-derived schema
 * data after every `generateBundledGtkSchema` run.
 *
 * The GIR files describe classes, properties and signals, but GtkBuilder
 * syntax also contains constructs GIR can never express: free element
 * ordering, mixed content, extra attributes (`translatable`, `context`,
 * `value`), widget-valued `<property>` elements, `AdwBreakpoint`
 * `<condition>`/`<setter>` fragments, the Pango `<attributes>` container and
 * user-defined widget classes.
 *
 * Each patch is named and documented so it can be reviewed, extended or
 * dropped independently of the data-driven generation. `applyXsd` splices
 * XSD fragments into marker lines emitted by the raw generator;
 * `applyJson` rewrites the generated JSON Schema tree in place.
 */
internal object SchemaPatches {

    /** GIR-derived name lists injected into XSD patch fragments. */
    data class GtkEnums(
        val classNames: List<String>,
        val propertyNames: List<String>,
        val signalNames: List<String>,
    )

    // ---------------------------------------------------------------- XSD

    data class XsdPatch(
        val id: String,
        val description: String,
        val fragment: String,
    )

    val xsdPatches: List<XsdPatch> = listOf(
        XsdPatch(
            id = "class-name-union",
            description = "class/parent attributes accept known GIR classes or any app-defined class name (identifier pattern).",
            fragment = """
                |  <xs:simpleType name="className">
                |    <xs:union>
                |      <xs:simpleType>
                |        <xs:restriction base="xs:string">
                ${'$'}{classEnums}
                |        </xs:restriction>
                |      </xs:simpleType>
                |      <xs:simpleType>
                |        <xs:restriction base="xs:string">
                |          <xs:pattern value="[A-Za-z_][A-Za-z0-9_.]*"/>
                |        </xs:restriction>
                |      </xs:simpleType>
                |    </xs:union>
                |  </xs:simpleType>
            """.trimMargin(),
        ),
        XsdPatch(
            id = "property-element",
            description = "Widget-valued properties may contain a nested <object>; translatable properties carry translatable/context/comments.",
            fragment = """
                |  <xs:element name="property">
                |    <xs:complexType mixed="true">
                |      <xs:sequence minOccurs="0" maxOccurs="1">
                |        <xs:element ref="object"/>
                |      </xs:sequence>
                |      <xs:attribute name="name" use="required">
                |        <xs:simpleType>
                |          <xs:restriction base="xs:string">
                ${'$'}{propertyEnums}
                |          </xs:restriction>
                |        </xs:simpleType>
                |      </xs:attribute>
                |      <xs:attribute name="translatable">
                |        <xs:simpleType>
                |          <xs:restriction base="xs:string">
                |            <xs:enumeration value="yes"/>
                |            <xs:enumeration value="no"/>
                |            <xs:enumeration value="true"/>
                |            <xs:enumeration value="false"/>
                |          </xs:restriction>
                |        </xs:simpleType>
                |      </xs:attribute>
                |      <xs:attribute name="context" type="xs:string"/>
                |      <xs:attribute name="comments" type="xs:string"/>
                |    </xs:complexType>
                |  </xs:element>
            """.trimMargin(),
        ),
        XsdPatch(
            id = "signal-element",
            description = "Signals expose handler/object/swapped/after alongside the name enum.",
            fragment = """
                |  <xs:element name="signal">
                |    <xs:complexType mixed="true">
                |      <xs:attribute name="name" use="required">
                |        <xs:simpleType>
                |          <xs:restriction base="xs:string">
                ${'$'}{signalEnums}
                |          </xs:restriction>
                |        </xs:simpleType>
                |      </xs:attribute>
                |      <xs:attribute name="handler" type="xs:string"/>
                |      <xs:attribute name="object" type="xs:string"/>
                |      <xs:attribute name="swapped">
                |        <xs:simpleType>
                |          <xs:restriction base="xs:string">
                |            <xs:enumeration value="yes"/>
                |            <xs:enumeration value="no"/>
                |            <xs:enumeration value="true"/>
                |            <xs:enumeration value="false"/>
                |          </xs:restriction>
                |        </xs:simpleType>
                |      </xs:attribute>
                |      <xs:attribute name="after">
                |        <xs:simpleType>
                |          <xs:restriction base="xs:string">
                |            <xs:enumeration value="yes"/>
                |            <xs:enumeration value="no"/>
                |            <xs:enumeration value="true"/>
                |            <xs:enumeration value="false"/>
                |          </xs:restriction>
                |        </xs:simpleType>
                |      </xs:attribute>
                |    </xs:complexType>
                |  </xs:element>
            """.trimMargin(),
        ),
    )

    /**
     * Splices the XSD fragments into the marker lines emitted by the raw
     * generator and expands the GIR name enums. Fails fast if a marker is
     * unknown, duplicated or left unresolved.
     */
    fun applyXsd(raw: String, enums: GtkEnums): String {
        var out = raw
        for (patch in xsdPatches) {
            val marker = "<!-- gb-patch:${patch.id} -->"
            check(marker in out) { "XSD patch '${patch.id}' has no marker in the generated schema" }
            out = out.replace(Regex("""(?m)^[ \t]*\Q$marker\E[ \t]*\r?\n"""), quoteReplacement(patch.fragment + "\n"))
        }
        val enumPlaceholders = listOf(
            "classEnums" to enumLines(enums.classNames, indent = 10),
            "propertyEnums" to enumLines(enums.propertyNames, indent = 12),
            "signalEnums" to enumLines(enums.signalNames, indent = 12),
        )
        for ((name, lines) in enumPlaceholders) {
            out = out.replace(Regex("""(?m)^[ \t]*\$\{$name\}[ \t]*\r?\n"""), quoteReplacement(lines + "\n"))
        }
        check("gb-patch:" !in out) { "Unresolved GtkBuilder patch markers remain in the generated XSD" }
        check("\${" !in out) { "Unresolved GIR enum placeholders remain in the generated XSD" }
        return out
    }

    private fun enumLines(names: List<String>, indent: Int): String =
        names.joinToString("\n") { " ".repeat(indent) + "<xs:enumeration value=\"${xmlEscape(it)}\"/>" }

    private fun xmlEscape(value: String): String = EscapeTables.xml(value)

    // ---------------------------------------------------------------- JSON

    data class JsonPatch(
        val id: String,
        val description: String,
        val apply: (Js) -> Js,
    )

    val jsonPatches: List<JsonPatch> = listOf(
        JsonPatch(
            id = "object-class-fallback",
            description = "Accept app-defined widget classes (not in the GIR enums) via a permissive fallback variant.",
            apply = { root ->
                val anyOfPath = listOf<Any>("\$defs", "object", "items", "allOf", 1, "anyOf")
                val current = readAt(root, anyOfPath)?.arrItems() ?: return@JsonPatch root
                replaceAt(root, anyOfPath, Js.Arr(current + genericClassVariant()))
            },
        ),
        JsonPatch(
            id = "object-common-extra",
            description = "Objects may carry Pango <attributes> and AdwBreakpoint <condition>/<setter> fragments.",
            apply = { root ->
                val propsPath = listOf<Any>("\$defs", "objectCommon", "properties")
                val current = readAt(root, propsPath)?.objEntries() ?: return@JsonPatch root
                val extra = listOf(
                    "attributes" to obj(
                        "type" to str("array"),
                        "items" to obj(
                            "type" to str("object"),
                            "properties" to obj(
                                "name" to obj("type" to str("string")),
                                "value" to obj("type" to str("string")),
                            ),
                        ),
                    ),
                    "condition" to obj(
                        "type" to str("array"),
                        "items" to obj("type" to str("string"), "\$" to obj("type" to str("string"))),
                    ),
                    "setter" to obj(
                        "type" to str("array"),
                        "items" to obj(
                            "type" to str("object"),
                            "properties" to obj(
                                "object" to obj("type" to str("string")),
                                "property" to obj("type" to str("string")),
                                "\$" to obj("type" to str("string")),
                            ),
                        ),
                    ),
                )
                replaceAt(root, propsPath, Js.Obj(current + extra))
            },
        ),
        JsonPatch(
            id = "property-child-object",
            description = "Widget-valued properties may contain a nested <object>.",
            apply = { root ->
                mapObjects(root) { item ->
                    val keys = item.entries.map { it.first }.toSet()
                    if ("translatable" in keys && "comments" in keys) {
                        Js.Obj(item.entries + ("object" to ref("#/\$defs/object")))
                    } else {
                        item
                    }
                }
            },
        ),
        JsonPatch(
            id = "menu-attribute-attrs",
            description = "Menu <attribute> also supports value/translatable/context.",
            apply = { root ->
                replaceAt(
                    root,
                    listOf<Any>("\$defs", "menuAttribute"),
                    obj(
                        "type" to str("object"),
                        "properties" to obj(
                            "name" to obj("type" to str("string")),
                            "value" to obj("type" to str("string")),
                            "\$" to obj("type" to str("string")),
                            "translatable" to yesNoEnum(),
                            "context" to obj("type" to str("string")),
                        ),
                    ),
                )
            },
        ),
    )

    /** Applies the JSON transforms to the raw generated tree, in order. */
    fun applyJson(raw: Js): Js = jsonPatches.fold(raw) { tree, patch -> patch.apply(tree) }

    // ------------------------------------------------------ JSON tree tools

    private fun obj(vararg entries: Pair<String, Js>) = Js.Obj(entries.toList())
    private fun arr(vararg items: Js) = Js.Arr(items.toList())
    private fun str(value: String) = Js.Str(value)
    private fun ref(path: String) = obj("\$ref" to str(path))
    private fun yesNoEnum() = obj("enum" to arr(*listOf("yes", "no", "true", "false").map(::str).toTypedArray()))

    private fun genericClassVariant() = obj(
        "type" to str("object"),
        "properties" to obj(
            "class" to obj("type" to str("string")),
            "property" to obj("type" to str("array"), "items" to ref("#/\$defs/property")),
            "signal" to obj("type" to str("array"), "items" to ref("#/\$defs/signal")),
        ),
    )

    private fun Js.objEntries(): List<Pair<String, Js>>? = (this as? Js.Obj)?.entries

    private fun Js.arrItems(): List<Js>? = (this as? Js.Arr)?.items

    /** Returns the value found by descending through object keys and array indices. */
    private fun readAt(root: Js, path: List<Any>): Js? {
        var current: Js = root
        for (part in path) {
            current = when (part) {
                is String -> current.objEntries()?.firstOrNull { it.first == part }?.second ?: return null
                is Int -> current.arrItems()?.getOrNull(part) ?: return null
                else -> return null
            }
        }
        return current
    }

    /** Returns a copy of [root] with the node at [path] replaced. */
    private fun replaceAt(root: Js, path: List<Any>, replacement: Js): Js {
        if (path.isEmpty()) return replacement
        return when (val head = path.first()) {
            is String -> {
                val obj = root as? Js.Obj ?: return root
                val entries = obj.entries.toMutableList()
                val index = entries.indexOfFirst { it.first == head }
                if (index < 0) return root
                entries[index] = head to replaceAt(entries[index].second, path.drop(1), replacement)
                Js.Obj(entries)
            }
            is Int -> {
                val arr = root as? Js.Arr ?: return root
                val items = arr.items.toMutableList()
                if (head !in items.indices) return root
                items[head] = replaceAt(items[head], path.drop(1), replacement)
                Js.Arr(items)
            }
            else -> root
        }
    }

    /** Returns a copy of the tree with [edit] applied to every object node. */
    private fun mapObjects(root: Js, edit: (Js.Obj) -> Js.Obj): Js = when (root) {
        is Js.Obj -> edit(Js.Obj(root.entries.map { (key, value) -> key to mapObjects(value, edit) }))
        is Js.Arr -> Js.Arr(root.items.map { mapObjects(it, edit) })
        is Js.Str -> root
    }
}
