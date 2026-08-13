package io.github.andrepg.flatpak.schemas.providers

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import com.jetbrains.jsonSchema.remote.JsonFileResolver
import io.github.andrepg.shared.Localization

/**
 * Remote location of the Flatpak manifest JSON schema, hosted by SchemaStore.
 */
const val FLATPAK_MANIFEST_PATH = "https://www.schemastore.org/flatpak-manifest.json"

/**
 * Provides the Flatpak manifest JSON schema to the IDE for completion and validation.
 *
 * Implements [JsonSchemaFileProvider] to expose a remote JSON Schema draft-07 schema
 * for files that look like Flatpak manifests.
 */
class FlatpakJsonSchemaProvider(private val project: Project) : JsonSchemaFileProvider {
    private val logger = Logger.getInstance(FlatpakJsonSchemaProvider::class.java)

    private val manifestCommonFileNames = setOf(
        "manifest.json",
        "flatpak.json",
        "flatpak-manifest.json"
    )

    private val manifestAppIdRegex =
        Regex("^[a-zA-Z0-9]+(\\.[a-zA-Z0-9]+)+\\.(json|yaml|yml)$")

    /**
     * @return the display name shown in the IDE for this schema provider
     */
    override fun getName(): String = Localization.message("providers.flatpak-manifest.name")

    /**
     * @return the schema is loaded from a remote source rather than bundled locally
     */
    override fun getSchemaType(): SchemaType = SchemaType.remoteSchema

    /**
     * @return the remote schema conforms to JSON Schema draft 7
     */
    override fun getSchemaVersion(): JsonSchemaVersion = JsonSchemaVersion.SCHEMA_7

    /**
     * @return the URL of the remote schema source
     */
    override fun getRemoteSource(): String = GTK_MANIFEST_PATH

    /**
     * Resolves the remote schema to a local [VirtualFile], logging warnings when resolution fails
     * or when remote schema downloads are disabled in the IDE settings.
     *
     * @return the resolved schema file, or null if it could not be obtained
     */
    override fun getSchemaFile(): VirtualFile? {
        val file = JsonFileResolver.urlToFile(GTK_MANIFEST_PATH)

        logger.info("Resolving Flatpak schema from $GTK_MANIFEST_PATH -> ${file?.url}")

        file?.let { if (!it.isValid) logger.warn("Flatpak schema file is invalid: ${it.url}") }

        if (!JsonFileResolver.isRemoteEnabled(project)) {
            logger.warn("Remote JSON schema downloads are disabled in IDE settings; Flatpak schema will not load")
        }

        return file
    }

    /**
     * Checks whether the given file is eligible for Flatpak schema validation.
     *
     * A file qualifies when its name matches a common manifest name (`manifest.json`,
     * `flatpak.json`, `flatpak-manifest.json`) or an app-id style name such as
     * `org.example.App.json`.
     *
     * @param file the file to check
     * @return true if the file looks like a Flatpak manifest
     */
    override fun isAvailable(file: VirtualFile): Boolean =
        file.name in manifestCommonFileNames || file.name.matches(manifestAppIdRegex)
}