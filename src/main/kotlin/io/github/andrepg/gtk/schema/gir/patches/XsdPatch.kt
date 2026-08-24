package io.github.andrepg.gtk.schema.gir.patches

/**
 * One named GtkBuilder grammar patch: [fragment] is spliced by
 * `SchemaPatches.applyXsd` into the raw generator's `gb-patch:<id>` marker
 * line, failing fast when a marker is unknown, duplicated or left unresolved.
 */
interface XsdPatch {
    val id: String

    val description: String

    val fragment: String
}
