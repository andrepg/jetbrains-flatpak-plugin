package io.github.andrepg.flatpak.schemas.providers

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import com.jetbrains.jsonSchema.remote.JsonFileResolver
import io.github.andrepg.flatpak.detection.FlatpakProjectDetector
import io.github.andrepg.shared.Localization
import io.github.andrepg.shared.log.Log

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
    private val log = Log.getInstance(FlatpakJsonSchemaProvider::class.java)

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
    override fun getRemoteSource(): String = FLATPAK_MANIFEST_PATH

    /**
     * Resolves the remote schema to a local [VirtualFile], logging warnings when resolution fails
     * or when remote schema downloads are disabled in the IDE settings.
     *
     * @return the resolved schema file, or null if it could not be obtained
     */
    override fun getSchemaFile(): VirtualFile? {
        val file = JsonFileResolver.urlToFile(FLATPAK_MANIFEST_PATH)

        log.info("Resolving Flatpak schema from $FLATPAK_MANIFEST_PATH -> ${file?.url}")

        file?.let { if (!it.isValid) log.warn("Flatpak schema file is invalid: ${it.url}") }

        if (!JsonFileResolver.isRemoteEnabled(project)) {
            log.warn("Remote JSON schema downloads are disabled in IDE settings; Flatpak schema will not load")
        }

        return file
    }

    /**
     * Checks whether the given file is eligible for Flatpak schema validation.
     *
     * Delegates to the shared [FlatpakProjectDetector.isCandidateName] predicate so
     * completion/validation and manifest detection always agree on what a manifest
     * is: common names (`manifest.json`, `flatpak.json`, `flatpak-manifest.json`)
     * or reverse-DNS app-id names with at least three segments
     * (`org.example.App.json`/`.yaml`/`.yml`), case-insensitive.
     *
     * @param file the file to check
     * @return true if the file looks like a Flatpak manifest
     */
    override fun isAvailable(file: VirtualFile): Boolean =
        FlatpakProjectDetector.isCandidateName(file.name)
}
