package io.github.andrepg.gtk.schema.providers

import io.github.andrepg.gtk.schema.SdkHint
import io.github.andrepg.gtk.schema.gir.GtkSchemaStep
import io.github.andrepg.shared.Localization

/**
 * Maps [GtkSchemaStep] to determinate progress text/fraction for the schema
 * generation background task. Lives in the IDE-glue providers package because
 * it depends on [Localization] (IntelliJ's `DynamicBundle`).
 */
object GtkSchemaProgressUi {
    fun text(
        step: GtkSchemaStep,
        hint: SdkHint,
    ): String =
        when (step) {
            GtkSchemaStep.Locating -> Localization.message("gtk.schema.generation.step.locating", hint.key)

            is GtkSchemaStep.Parsing ->
                Localization.message("gtk.schema.generation.step.parsing", step.fileName, step.index, step.total)

            GtkSchemaStep.Rendering -> Localization.message("gtk.schema.generation.step.rendering")

            GtkSchemaStep.Caching -> Localization.message("gtk.schema.generation.step.caching")
        }

    fun fraction(step: GtkSchemaStep): Double =
        when (step) {
            GtkSchemaStep.Locating -> 0.05
            is GtkSchemaStep.Parsing -> 0.1 + 0.8 * (step.index.toDouble() / step.total)
            GtkSchemaStep.Rendering -> 0.95
            GtkSchemaStep.Caching -> 1.0
        }
}