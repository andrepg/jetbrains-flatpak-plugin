package io.github.andrepg.flatpak.schemas.providers

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import com.jetbrains.jsonSchema.remote.JsonFileResolver
import io.github.andrepg.shared.Localization
import io.github.andrepg.shared.exceptions.FeatureNotImplementedException

/**
 * Remote location of the GTK interface JSON schema, hosted by SchemaStore.
 */
const val GTK_MANIFEST_PATH = "https://www.schemastore.org/flatpak-manifest.json"

/**
 * Provides the GTK interface JSON schema to the IDE for completion and validation.
 *
 * Implements [JsonSchemaFileProvider] to expose a remote JSON Schema draft-07 schema
 * for GNOME/Adwaita interface files.
 */
class GtkInterfaceSchemaProvider(private val project: Project) : JsonSchemaFileProvider {
    private val logger = Logger.getInstance(FlatpakJsonSchemaProvider::class.java)

    // TODO Change to current file regex (.ui and .xml files)
    private val manifestAppIdRegex =
        Regex("^[a-zA-Z0-9]+(\\.[a-zA-Z0-9]+)+\\.(json|yaml|yml)$")

    /**
     * @return the display name shown in the IDE for this schema provider
     */
    override fun getName(): String = Localization.message("providers.gtk-interface.name")

    /**
     * @return the schema is loaded from a remote source rather than bundled locally
     */
    // TODO: After checking how GTK schema is available, we need to change here
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
     * Resolves the remote schema to a local [VirtualFile].
     *
     * Not yet implemented; throws until the GTK interface schema is wired up.
     *
     * @return the resolved schema file
     * @throws FeatureNotImplementedException always, as the feature is not yet implemented
     */
    // TODO: After checking how GTK schema is available, we need to change here
    override fun getSchemaFile(): VirtualFile? {
        val file = JsonFileResolver.urlToFile(GTK_MANIFEST_PATH)

        throw FeatureNotImplementedException()
    }

    /**
     * Checks whether the given file is eligible for GTK interface schema validation.
     *
     * @param file the file to check
     * @return true if the file name looks like a GTK interface definition
     */
    override fun isAvailable(file: VirtualFile): Boolean = file.name.matches(manifestAppIdRegex)
}