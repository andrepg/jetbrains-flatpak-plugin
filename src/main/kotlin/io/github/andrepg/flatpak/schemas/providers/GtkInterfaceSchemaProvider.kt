package io.github.andrepg.flatpak.schemas.providers

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import io.github.andrepg.shared.Localization

/**
 * Bundled location of the GTK interface JSON schema.
 */
private const val GTK_UI_SCHEMA_PATH = "/schemas/gtk-ui-schema.json"

/**
 * Provides the bundled GTK/Adwaita interface schema to the IDE for completion and validation.
 *
 * Implements [JsonSchemaFileProvider] to expose the generated `gtk-ui-schema.json`
 * (JSON Schema draft-07) for GtkBuilder `.ui` and Glade `.glade` interface files.
 */
class GtkInterfaceSchemaProvider(private val project: Project) : JsonSchemaFileProvider {

    private val uiFileRegex = Regex(""".*\.(ui|glade)$""", RegexOption.IGNORE_CASE)

    /**
     * @return the display name shown in the IDE for this schema provider
     */
    override fun getName(): String = Localization.message("providers.gtk-interface.name")

    /**
     * @return the schema is bundled with the plugin rather than loaded from a remote source
     */
    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema

    /**
     * @return the bundled schema conforms to JSON Schema draft 7
     */
    override fun getSchemaVersion(): JsonSchemaVersion = JsonSchemaVersion.SCHEMA_7

    /**
     * Resolves the bundled schema file on the classpath, whether the plugin is running from the
     * build output or a packed JAR.
     *
     * @return the bundled schema file, or null if it is not on the classpath
     */
    override fun getSchemaFile(): VirtualFile? {
        val resourceUrl = javaClass.getResource(GTK_UI_SCHEMA_PATH) ?: return null
        return VirtualFileManager.getInstance().findFileByUrl(resourceUrl.toString())
    }

    /**
     * Checks whether the given file is eligible for GTK interface schema validation.
     *
     * @param file the file to check
     * @return true if the file is a GtkBuilder `.ui` or Glade `.glade` interface file
     */
    override fun isAvailable(file: VirtualFile): Boolean = file.name.matches(uiFileRegex)
}
