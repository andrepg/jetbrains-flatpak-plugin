package io.github.andrepg.gtk.schema.gir

import io.github.andrepg.gtk.schema.gir.builder.XsdBuilder
import io.github.andrepg.gtk.schema.gir.parser.GirParser
import java.io.File
import java.util.concurrent.CancellationException

/**
 * Orchestrates GtkBuilder XSD generation from the GNOME SDK's GIR files:
 * [GirParser] turns the `.gir` XML into a type registry and [XsdBuilder]
 * renders the patched XSD on top of the static skeleton.
 *
 * JDK-only (no IntelliJ imports): it runs inside the IDE through
 * [io.github.andrepg.gtk.schema.GtkSchemaManager] and stays unit-testable.
 *
 * `girDir` is the SDK's `files/share/gir-1.0` directory as resolved by
 * [io.github.andrepg.gtk.schema.locator.GirSdkLocator].
 */
object GirSchemaExtractor {
    /**
     * Generates the patched GtkBuilder XSD string from the GIR files under
     * [girDir]; fails fast when no `Gtk-4.0.gir` is present.
     *
     * @param onProgress optional progress reporter; returning `false` aborts
     *   generation with a [CancellationException]
     */
    internal fun generateXsd(
        girDir: File,
        onProgress: GtkSchemaProgress? = null,
    ): String {
        if (!File(girDir, "Gtk-4.0.gir").isFile) {
            error("No Gtk-4.0.gir found under $girDir; expected the SDK's gir-1.0 directory.")
        }
        val registry = GirParser.parseAll(girDir, onProgress)
        if (onProgress?.report(GtkSchemaStep.Rendering) == false) {
            throw CancellationException("GtkBuilder schema generation cancelled")
        }
        return XsdBuilder.renderXsd(XsdBuilder.buildEnums(registry))
    }
}
