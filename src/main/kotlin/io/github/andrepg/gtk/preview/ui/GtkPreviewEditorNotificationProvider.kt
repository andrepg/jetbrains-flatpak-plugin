package io.github.andrepg.gtk.preview.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import io.github.andrepg.gtk.schema.providers.GtkSdkHintResolver
import io.github.andrepg.shared.Localization
import io.github.andrepg.shared.license.PremiumFeatureGate
import java.util.function.Function
import javax.swing.JComponent

/**
 * Provides a notification for GTK interface files (.ui, .glade).
 *
 * IDE glue: builds the notification panel with validation status and Preview button.
 */
class GtkPreviewEditorNotificationProvider : EditorNotificationProvider {
    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        if (!isGtkPreviewEnabled()) {
            return null
        }
        if (!file.name.endsWith(".ui") && !file.name.endsWith(".glade")) return null

        val hint = GtkSdkHintResolver.resolve(project)
        if (hint == null) return null

        return Function { _ ->
            val panel = EditorNotificationPanel()
            val service = project.getService(GtkPreviewService::class.java)
            ApplicationManager.getApplication().executeOnPooledThread {
                val validation = service.validate(file)
                ApplicationManager.getApplication().invokeLater {
                    updatePanel(panel, validation, file, project)
                }
            }
            panel
        }
    }

    private fun isGtkPreviewEnabled(): Boolean {
        return System.getProperty("flatpak.gtk.preview.enabled", "false").toBoolean()
    }

    private fun updatePanel(panel: EditorNotificationPanel, validation: GtkPreviewService.ValidationResult, file: VirtualFile, project: Project) {
        panel.clear()
        when {
            validation.message != null -> {
                panel.text = validation.message
            }

            validation.adwUnsupported -> {
                panel.text = Localization.message("preview.notification.adw-unsupported")
            }

            validation.gatePassed -> {
                panel.text = Localization.message("preview.notification.title")
                if (PremiumFeatureGate.isPremiumAvailable()) {
                    panel.createActionLabel(Localization.message("preview.notification.preview")) {
                        openPreview(project, file)
                    }
                } else {
                    panel.createActionLabel(Localization.message("preview.notification.upgrade")) {
                        PremiumFeatureGate.requestAccess(Localization.message("preview.notification.subscription-message"))
                    }
                }
            }

            else -> {
                panel.text = Localization.message("preview.notification.validation-failed")
            }
        }
    }

    private fun openPreview(project: Project, file: VirtualFile) {
        val window = ToolWindowManager.getInstance(project).getToolWindow("gtk-preview") ?: return
        window.show()
        (window.contentManager.contents.firstOrNull()?.component as? GtkPreviewPanel)?.requestRender(file)
    }
}
