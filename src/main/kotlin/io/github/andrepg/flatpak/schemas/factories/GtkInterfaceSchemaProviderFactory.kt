package io.github.andrepg.flatpak.schemas.factories

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import io.github.andrepg.shared.exceptions.FeatureNotImplementedException

/**
 * Registers the GTK interface schema provider with the IDE.
 *
 * Implements IntelliJ's [JsonSchemaProviderFactory] extension point so that GNOME/Adwaita
 * interface files receive schema-based completion and validation.
 */
class GtkInterfaceSchemaProviderFactory : JsonSchemaProviderFactory {
    private val logger = Logger.getInstance(GtkInterfaceSchemaProviderFactory::class.java)

    /**
     * Creates the list of JSON schema providers for the given project.
     *
     * Not yet implemented; throws until the GTK interface schema is wired up.
     *
     * @param project the project the providers are associated with
     * @throws FeatureNotImplementedException always, as the feature is not yet implemented
     */
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        logger.info("Flatpak JSON Schema provider factory loaded, registering FlatpakJsonSchemaProvider")

        throw FeatureNotImplementedException()
        // return listOf(FlatpakJsonSchemaProvider(project))
    }
}