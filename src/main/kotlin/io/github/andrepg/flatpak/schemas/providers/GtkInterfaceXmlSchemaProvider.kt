package io.github.andrepg.flatpak.schemas.providers

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.xml.XmlSchemaProvider

/**
 * Bundled location of the GTK interface XSD.
 */
private const val GTK_UI_XSD_PATH = "/schemas/gtk-ui.xsd"

/**
 * Canonical identifier of the GTK interface schema, mirroring the plugin's `standardResource` URL.
 */
private const val GTK_INTERFACE_NAMESPACE = "urn:io.github.andrepg:flatpak-support:schemas:gtk-ui"

/**
 * Serves the bundled GtkBuilder XSD to GtkBuilder `.ui` and Glade `.glade` files.
 *
 * Implements the XML plugin's [XmlSchemaProvider] extension point: for files whose name matches
 * a GTK interface file, [getSchema] returns the generated `gtk-ui.xsd` (no target namespace,
 * root `<interface>`) resolved from the plugin classpath.
 */
class GtkInterfaceXmlSchemaProvider : XmlSchemaProvider() {

    private val uiFileRegex = Regex(""".*\.(ui|glade)$""", RegexOption.IGNORE_CASE)

    /**
     * @return true if the file is a GtkBuilder `.ui` or Glade `.glade` interface file
     */
    override fun isAvailable(file: XmlFile): Boolean = file.name.matches(uiFileRegex)

    /**
     * Resolves the bundled schema file on the classpath, whether the plugin is running from the
     * build output or a packed JAR.
     *
     * @param namespace the namespace requested for the file
     * @param module the module the file belongs to
     * @param file the file being processed
     * @return the bundled schema as an [XmlFile], or null if the file is not a GTK interface
     */
    override fun getSchema(url: String, module: Module?, baseFile: PsiFile): XmlFile? {
        val xmlFile = baseFile as? XmlFile ?: return null
        if (!isAvailable(xmlFile)) return null
        if (module == null) return null

        val resourceUrl = javaClass.getResource(GTK_UI_XSD_PATH) ?: return null
        val virtualFile = VirtualFileManager.getInstance().findFileByUrl(resourceUrl.toString()) ?: return null
        return PsiManager.getInstance(module.project).findFile(virtualFile) as? XmlFile
    }

    /**
     * @return the canonical GTK interface namespace for files this provider can serve
     */
    override fun getAvailableNamespaces(file: XmlFile, namespace: String?): Set<String> =
        if (isAvailable(file)) setOf(GTK_INTERFACE_NAMESPACE) else emptySet()

    /**
     * The schema is a static classpath resource; safe to serve while the project is being indexed.
     */
    override fun isDumbAware(): Boolean = true
}
