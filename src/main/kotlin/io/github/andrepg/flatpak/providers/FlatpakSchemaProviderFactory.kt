package io.github.andrepg.flatpak.providers

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory

class FlatpakSchemaProviderFactory : JsonSchemaProviderFactory {
    private val logger = Logger.getInstance(javaClass.name)

    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        logger.info("Flatpak JSON Schema provider factory loaded, registering FlatpakJsonSchemaProvider")
        return listOf(FlatpakJsonSchemaProvider(project))
    }
}