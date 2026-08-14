package io.github.andrepg.gtk.schema.gir

/**
 * Reports the coarse steps of GtkBuilder schema generation.
 *
 * JDK-only (no IntelliJ imports): the IDE glue maps each step to localized
 * text and a determinate progress fraction, and can abort generation by
 * returning `false` from [GtkSchemaProgress.report].
 */
sealed interface GtkSchemaStep {

    /** Looking up the project's GNOME SDK in the Flatpak installation. */
    data object Locating : GtkSchemaStep

    /** Parsing one GIR file; [index] is 1-based, [total] the number of files. */
    data class Parsing(val fileName: String, val index: Int, val total: Int) : GtkSchemaStep

    /** Assembling the XSD from the parsed registry. */
    data object Rendering : GtkSchemaStep

    /** Writing the generated XSD into the plugin cache. */
    data object Caching : GtkSchemaStep
}

/**
 * Callback receiving schema-generation progress steps.
 *
 * @return `true` to keep generating, `false` to abort (e.g. the user cancelled).
 */
fun interface GtkSchemaProgress {
    fun report(step: GtkSchemaStep): Boolean
}
