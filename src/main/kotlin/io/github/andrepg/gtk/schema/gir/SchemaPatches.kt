package io.github.andrepg.gtk.schema.gir

import io.github.andrepg.gtk.schema.gir.patches.ElementPropertyValues
import io.github.andrepg.gtk.schema.gir.patches.GenericWidgetProperties
import io.github.andrepg.gtk.schema.gir.patches.SignalElementProperties
import io.github.andrepg.gtk.schema.gir.patches.XsdPatch
import io.github.andrepg.shared.text.EscapeTables
import java.util.regex.Matcher.quoteReplacement

/**
 * Curated GtkBuilder grammar patches applied on top of the GIR-derived schema
 * data at every schema generation.
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
 * XSD fragments into marker lines emitted by the raw generator.
 */
internal object SchemaPatches {
    /** GIR-derived name lists injected into XSD patch fragments. */
    data class GtkEnums(
        val classNames: List<String>,
        val propertyNames: List<String>,
        val signalNames: List<String>,
    )

    val xsdPatches: List<XsdPatch> =
        listOf(
            GenericWidgetProperties,
            ElementPropertyValues,
            SignalElementProperties,
        )

    /**
     * Splices the XSD fragments into the marker lines emitted by the raw
     * generator and expands the GIR name enums. Fails fast if a marker is
     * unknown, duplicated or left unresolved.
     */
    fun applyXsd(
        raw: String,
        enums: GtkEnums,
    ): String {
        var out = raw
        for (patch in xsdPatches) {
            val marker = "<!-- gb-patch:${patch.id} -->"
            check(marker in out) { "XSD patch '${patch.id}' has no marker in the generated schema" }
            out = out.replace(Regex("""(?m)^[ \t]*\Q$marker\E[ \t]*\r?\n"""), quoteReplacement(patch.fragment + "\n"))
        }
        val enumPlaceholders =
            listOf(
                "classEnums" to enumLines(enums.classNames, indent = 10),
                "propertyEnums" to enumLines(enums.propertyNames, indent = 12),
                "signalEnums" to enumLines(enums.signalNames, indent = 12),
            )
        for ((name, lines) in enumPlaceholders) {
            out = out.replace(Regex("""(?m)^[ \t]*\$\{$name}[ \t]*\r?\n"""), quoteReplacement(lines + "\n"))
        }
        check("gb-patch:" !in out) { "Unresolved GtkBuilder patch markers remain in the generated XSD" }
        check($$"${" !in out) { "Unresolved GIR enum placeholders remain in the generated XSD" }
        return out
    }

    private fun enumLines(
        names: List<String>,
        indent: Int,
    ): String = names.joinToString("\n") { " ".repeat(indent) + "<xs:enumeration value=\"${xmlEscape(it)}\"/>" }

    private fun xmlEscape(value: String): String = EscapeTables.xml(value)
}
