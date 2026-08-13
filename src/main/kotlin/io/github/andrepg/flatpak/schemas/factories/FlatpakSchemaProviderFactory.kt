package io.github.andrepg.flatpak.schemas.factories

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import io.github.andrepg.flatpak.schemas.providers.FlatpakJsonSchemaProvider

/**
 * Registers the Flatpak JSON schema provider with the IDE.
 *
 * Implements IntelliJ's [JsonSchemaProviderFactory] extension point (declared in `plugin.xml`)
 * so that Flatpak manifest files receive schema-based completion and validation.
 */
class FlatpakSchemaProviderFactory : JsonSchemaProviderFactory {
    private val logger = Logger.getInstance(FlatpakSchemaProviderFactory::class.java)

    /**
     * Creates the list of JSON schema providers for the given project.
     *
     * @param project the project the providers are associated with
     * @return a list containing a single [FlatpakJsonSchemaProvider]
     */
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        logger.info("Flatpak JSON Schema provider factory loaded, registering FlatpakJsonSchemaProvider")
        return listOf(FlatpakJsonSchemaProvider(project))
    }
}