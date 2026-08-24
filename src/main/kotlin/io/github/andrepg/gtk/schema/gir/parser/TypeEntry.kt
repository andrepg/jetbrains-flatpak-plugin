package io.github.andrepg.gtk.schema.gir.parser

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
