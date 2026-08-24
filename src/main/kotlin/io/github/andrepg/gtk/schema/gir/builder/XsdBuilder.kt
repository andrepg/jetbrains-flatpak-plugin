package io.github.andrepg.gtk.schema.gir.builder

import io.github.andrepg.gtk.schema.gir.SchemaPatches
import io.github.andrepg.gtk.schema.gir.parser.Registry

/**
 * Renders the final GtkBuilder XSD from a parsed [Registry]:
 * derives the GIR name enums (classes, properties, signals flattened through
 * inheritance) and applies [SchemaPatches] on top of [XsdSkeleton.RAW].
 */
object XsdBuilder {
    /** Derives the GIR name lists injected into the XSD patch fragments. */
    internal fun buildEnums(registry: Registry): SchemaPatches.GtkEnums {
        val allTypes = registry.allTypes()
        return SchemaPatches.GtkEnums(
            classNames = allTypes.map { it.cType },
            propertyNames =
                allTypes
                    .flatMap { registry.flattened(it) { e -> e.properties } }
                    .distinct()
                    .sorted(),
            signalNames =
                allTypes
                    .flatMap { registry.flattened(it) { e -> e.signals } }
                    .distinct()
                    .sorted(),
        )
    }

    /** Applies the curated patches to [XsdSkeleton.RAW], expanding [enums]. */
    internal fun renderXsd(enums: SchemaPatches.GtkEnums): String = SchemaPatches.applyXsd(XsdSkeleton.RAW, enums)
}
