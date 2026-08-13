package io.github.andrepg.flatpak.providers

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import com.jetbrains.jsonSchema.remote.JsonFileResolver

const val FLATPAK_MANIFEST_PATH = "https://www.schemastore.org/flatpak-manifest.json"

class FlatpakJsonSchemaProvider(private val project: Project) : JsonSchemaFileProvider {
    private val manifestAppIdRegex =
        Regex("^[a-zA-Z0-9]+(\\.[a-zA-Z0-9]+)+\\.(json|yaml|yml)$")
    private val manifestCommonFileNames = setOf("manifest.json", "flatpak.json", "flatpak-manifest.json")

    private val logger = Logger.getInstance(javaClass.name)

    override fun getName(): String = "Flatpak Manifest"

    override fun getSchemaType(): SchemaType = SchemaType.remoteSchema

    override fun getSchemaVersion(): JsonSchemaVersion = JsonSchemaVersion.SCHEMA_7

    override fun getRemoteSource(): String? = FLATPAK_MANIFEST_PATH

    override fun getSchemaFile(): VirtualFile? {
        val file = JsonFileResolver.urlToFile(FLATPAK_MANIFEST_PATH)

        logger.info("Resolving Flatpak schema from $FLATPAK_MANIFEST_PATH -> ${file?.url}")

        file?.let { if (!it.isValid) logger.warn("Flatpak schema file is invalid: ${it.url}") }

        if (!JsonFileResolver.isRemoteEnabled(project)) {
            logger.warn("Remote JSON schema downloads are disabled in IDE settings; Flatpak schema will not load")
        }

        return file
    }

    override fun isAvailable(file: VirtualFile): Boolean =
        file.name in manifestCommonFileNames || file.name.matches(manifestAppIdRegex)
}