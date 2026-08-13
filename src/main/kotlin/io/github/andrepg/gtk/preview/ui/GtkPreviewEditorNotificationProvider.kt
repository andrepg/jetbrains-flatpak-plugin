package io.github.andrepg.gtk.preview.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import io.github.andrepg.gtk.schema.providers.GtkSdkHintResolver
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
        if (!file.name.endsWith(".ui") && !file.name.endsWith(".glade")) return null

        val hint = GtkSdkHintResolver.resolve(project)
        if (hint == null) return null

        return Function { _ ->
            val panel = EditorNotificationPanel().apply {
                text = "GTK Preview"
            }

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

    private fun updatePanel(panel: EditorNotificationPanel, validation: GtkPreviewService.ValidationResult, file: VirtualFile) {
        panel.clear()
        when {
            validation.message != null -> {
                panel.text = validation.message
                panel.createActionLabel("Preview") {}.isEnabled = false
            }

            validation.adwUnsupported -> {
                panel.text = "Adwaita types unsupported"
                panel.createActionLabel("Preview") {}.isEnabled = false
            }

            validation.gatePassed -> {
                panel.text = "GTK Preview"
                panel.createActionLabel("Preview") {
                    val window = ToolWindowManager.getInstance(ProjectManager.getInstance().defaultProject)

                    window.getToolWindow("gtk-preview")?.show() {}

                    val previewPanel = window.getToolWindow("gtk-preview")?.contentManager.contents.firstOrNull()

                }
            }

            else -> {
                panel.text = "Validation failed"
                panel.createActionLabel("Preview") {}.isEnabled = false
            }
        }
    }
}
