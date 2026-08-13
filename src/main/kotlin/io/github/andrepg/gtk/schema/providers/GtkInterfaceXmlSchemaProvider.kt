package io.github.andrepg.gtk.schema.providers

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.XmlSchemaProvider
import io.github.andrepg.flatpak.detection.FlatpakProjectDetector
import io.github.andrepg.flatpak.settings.DefaultFlatpakPaths
import io.github.andrepg.flatpak.utils.FlatpakManifestReader
import io.github.andrepg.gtk.schema.GtkSchemaManager
import io.github.andrepg.gtk.schema.SdkHint
import java.io.File

/**
 * Serves the GtkBuilder XSD to GtkBuilder interface files: `.ui`, Glade
 * `.glade` and `.xml` files whose content matches the schema.
 *
 * Implements the XML plugin's [XmlSchemaProvider] extension point. For `.ui`
 * and `.glade` files the name alone identifies a GTK interface file. A plain
 * `.xml` is only treated as one when its root element is the schema's root
 * `<interface>` — i.e. the document already matches our valid schema — and the
 * project is a recognized Flatpak project (the feature's scope).
 *
 * [getSchema] returns the schema for the project's GNOME SDK. A generated XSD
 * cached in the plugin config dir is preferred (background generation on first
 * open), otherwise the bundled `gtk-ui.xsd` from the classpath.
 *
 * This class is the composition root: it reads the SDK hint from the Flatpak
 * manifest domain and delegates schema resolution to [GtkSchemaManager].
 */
class GtkInterfaceXmlSchemaProvider : XmlSchemaProvider() {

    private val interfaceFileRegex = Regex(""".*\.(ui|glade)$""", RegexOption.IGNORE_CASE)
    private val xmlFileRegex = Regex(""".*\.xml$""", RegexOption.IGNORE_CASE)

    private val schemaManager = GtkSchemaManager(PathManager.getConfigDir().resolve("flatpak-schemas").toFile())

    /**
     * @return true for GtkBuilder `.ui`/Glade `.glade` files, or for `.xml`
     * files whose root element is `<interface>` (they match the served schema)
     */
    override fun isAvailable(file: XmlFile): Boolean {
        if (file.name.matches(interfaceFileRegex)) return true
        if (!file.name.matches(xmlFileRegex)) return false
        return file.rootTag?.name == GTK_INTERFACE_ROOT
    }

    /**
     * Resolves the schema for the project's GNOME SDK (generated and cached, or
     * bundled), or null when no SDK/pattern matches.
     *
     * @param url the namespace requested for the file
     * @param module the module the file belongs to
     * @param baseFile the file being processed
     * @return the resolved schema as an [XmlFile], or null
     */
    override fun getSchema(url: String, module: Module?, baseFile: PsiFile): XmlFile? {
        val xmlFile = baseFile as? XmlFile ?: return null
        if (!isAvailable(xmlFile)) return null
        val project = xmlFile.project

        val hint = sdkHint(project)
        if (xmlFile.name.matches(xmlFileRegex) && hint == null) return null
        val generated = schemaManager.cachedSchema(hint)
        if (generated == null && hint != null && schemaManager.markRequested(hint)) {
            scheduleGeneration(hint)
        }

        val schemaUrl = generated?.toURI()?.toString()
            ?: javaClass.getResource(GTK_UI_XSD_PATH)?.toString()
            ?: return null
        val virtualFile = VirtualFileManager.getInstance().findFileByUrl(schemaUrl) ?: return null
        return PsiManager.getInstance(project).findFile(virtualFile) as? XmlFile
    }

    /**
     * @return the canonical GTK interface namespace for files this provider can serve
     */
    override fun getAvailableNamespaces(file: XmlFile, tagName: String?): Set<String> =
        if (isAvailable(file)) setOf(GTK_INTERFACE_NAMESPACE) else emptySet()

    /**
     * The schema is a static classpath resource; safe to serve while the project is being indexed.
     */
    override fun isDumbAware(): Boolean = true

    /**
     * Derives the SDK hint from the project's Flatpak manifests: the first
     * manifest declaring a GNOME `sdk`/`runtime` (e.g. `org.gnome.Sdk//50`)
     * drives the schema branch; non-GNOME or manifest-less projects get null.
     */
    private fun sdkHint(project: Project): SdkHint? {
        for ((file, _) in FlatpakProjectDetector.findManifests(project)) {
            val candidate = FlatpakManifestReader.readSdk(file.path)
                ?: FlatpakManifestReader.readRuntime(file.path)
                ?: continue
            val (appId, branch) = splitBranch(candidate)
            if (appId.startsWith("org.gnome.")) {
                return SdkHint(appId, branch)
            }
        }
        return null
    }

    private fun scheduleGeneration(hint: SdkHint) {
        ApplicationManager.getApplication().executeOnPooledThread {
            schemaManager.generateSchema(hint, DefaultFlatpakPaths.MAIN_BINARY)
        }
    }

    private fun splitBranch(value: String): Pair<String, String?> {
        val index = value.indexOf("//")
        return if (index >= 0) {
            value.substring(0, index) to value.substring(index + 2).takeIf { it.isNotEmpty() }
        } else {
            value to null
        }
    }

    private companion object {
        const val GTK_UI_XSD_PATH = "/schemas/gtk-ui.xsd"
        const val GTK_INTERFACE_NAMESPACE = "urn:io.github.andrepg:flatpak-support:schemas:gtk-ui"
        const val GTK_INTERFACE_ROOT = "interface"
    }
}
