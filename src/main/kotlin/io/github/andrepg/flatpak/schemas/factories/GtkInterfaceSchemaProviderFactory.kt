package io.github.andrepg.flatpak.schemas.factories

import com.intellij.openapi.project.Project
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import io.github.andrepg.flatpak.schemas.providers.GtkInterfaceSchemaProvider

/**
 * Registers the GTK interface schema provider with the IDE.
 *
 * Implements IntelliJ's [JsonSchemaProviderFactory] extension point so that GNOME/Adwaita
 * interface files receive schema-based completion and validation.
 */
class GtkInterfaceSchemaProviderFactory : JsonSchemaProviderFactory {

    /**
     * Creates the list of JSON schema providers for the given project.
     *
     * @param project the project the providers are associated with
     * @return the GTK interface schema provider for [project]
     */
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> =
        listOf(GtkInterfaceSchemaProvider(project))
}
