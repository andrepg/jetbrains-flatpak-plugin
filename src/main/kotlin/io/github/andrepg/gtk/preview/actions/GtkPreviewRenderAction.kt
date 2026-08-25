package io.github.andrepg.gtk.preview.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import io.github.andrepg.gtk.preview.ui.GtkPreviewPanel

class GtkPreviewRenderAction(
    private val panel: GtkPreviewPanel,
) : AnAction(
        "Render Preview",
        "Render the current GTK preview",
        AllIcons.Actions.Execute,
    ) {
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        panel.setFailed(false)
        panel.setLoading(true)
        panel.refresh()

        val task =
            object : Task.Backgroundable(project, "Rendering GTK preview", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    // TODO: actual render logic
                }

                override fun onFinished() {
                    if (project.isDisposed) return
                    panel.setLoading(false)
                    panel.refresh()
                }
            }

        ProgressManager.getInstance().run(task)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
