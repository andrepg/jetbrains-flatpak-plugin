package io.github.andrepg.gtk.schema.providers

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.xml.XmlSchemaProvider
import io.github.andrepg.flatpak.settings.FlatpakSettings
import io.github.andrepg.gtk.schema.GtkSchemaManager
import io.github.andrepg.gtk.schema.SdkHint
import io.github.andrepg.gtk.schema.gir.GtkSchemaStep
import io.github.andrepg.shared.Localization
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

        val hint = GtkSdkHintResolver.resolve(project)
        if (xmlFile.name.matches(xmlFileRegex) && hint == null) return null
        val generated = schemaManager.cachedSchema(hint)
        if (generated == null && hint != null && schemaManager.markRequested(hint)) {
            scheduleGeneration(project, hint)
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
     * Schedules schema generation as a cancellable background task with a
     * determinate progress bar, notifying via balloon on success/failure.
     *
     * Safe to call from any thread ([Task.Backgroundable] routes non-EDT starts
     * through `invokeLater`), which covers [getSchema] being invoked during
     * highlighting and background analysis.
     */
    private fun scheduleGeneration(project: Project, hint: SdkHint) {
        if (project.isDisposed) return
        val generation = object : Task.Backgroundable(
            project,
            Localization.message("gtk.schema.generation.title", hint.key),
            true,
        ) {
            private var outcome = GenerationOutcome.CANCELLED

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                val generated = schemaManager.generateSchema(hint, FlatpakSettings.flatpakBinary) { step ->
                    indicator.text = progressText(step, hint)
                    indicator.fraction = progressFraction(step)
                    !indicator.isCanceled()
                }
                outcome = when {
                    indicator.isCanceled() -> GenerationOutcome.CANCELLED
                    generated != null -> GenerationOutcome.SUCCESS
                    else -> GenerationOutcome.FAILED
                }
            }

            override fun onFinished() {
                if (project.isDisposed) return
                when (outcome) {
                    GenerationOutcome.SUCCESS -> notifyGeneration(project, hint, success = true)
                    GenerationOutcome.FAILED -> notifyGeneration(project, hint, success = false)
                    GenerationOutcome.CANCELLED -> {}
                }
            }
        }
        ProgressManager.getInstance().run(generation)
    }

    private fun progressText(step: GtkSchemaStep, hint: SdkHint): String = when (step) {
        GtkSchemaStep.Locating -> Localization.message("gtk.schema.generation.step.locating", hint.key)
        is GtkSchemaStep.Parsing ->
            Localization.message("gtk.schema.generation.step.parsing", step.fileName, step.index, step.total)
        GtkSchemaStep.Rendering -> Localization.message("gtk.schema.generation.step.rendering")
        GtkSchemaStep.Caching -> Localization.message("gtk.schema.generation.step.caching")
    }

    private fun progressFraction(step: GtkSchemaStep): Double = when (step) {
        GtkSchemaStep.Locating -> 0.05
        is GtkSchemaStep.Parsing -> 0.1 + 0.8 * (step.index.toDouble() / step.total)
        GtkSchemaStep.Rendering -> 0.95
        GtkSchemaStep.Caching -> 1.0
    }

    private fun notifyGeneration(project: Project, hint: SdkHint, success: Boolean) {
        val key = if (success) "gtk.schema.generation.notification.success" else "gtk.schema.generation.notification.failure"
        val type = if (success) NotificationType.INFORMATION else NotificationType.WARNING
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                Localization.message("gtk.schema.generation.title", hint.key),
                Localization.message(key, hint.key),
                type,
            )
            .notify(project)
    }

    private companion object {
        const val GTK_UI_XSD_PATH = "/schemas/gtk-ui.xsd"
        const val GTK_INTERFACE_NAMESPACE = "urn:io.github.andrepg:flatpak-support:schemas:gtk-ui"
        const val GTK_INTERFACE_ROOT = "interface"
        const val NOTIFICATION_GROUP_ID = "io.github.andrepg.flatpak.schema"
    }

    private enum class GenerationOutcome { SUCCESS, FAILED, CANCELLED }
}
