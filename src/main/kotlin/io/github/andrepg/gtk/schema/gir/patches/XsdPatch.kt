package io.github.andrepg.gtk.schema.gir.patches

/**
 * One named GtkBuilder grammar patch: [fragment] is spliced by
 * `SchemaPatches.applyXsd` into the `gb-patch:<id>` marker line of
 * `XsdSkeleton.RAW`, failing fast when a marker is unknown, duplicated or
 * left unresolved.
 */
interface XsdPatch {
    val id: String

    val description: String

    val fragment: String
}
