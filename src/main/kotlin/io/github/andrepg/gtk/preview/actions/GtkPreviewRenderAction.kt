package io.github.andrepg.gtk.preview.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import io.github.andrepg.gtk.preview.tasks.GtkPreviewBackgroundRenderTask
import io.github.andrepg.gtk.preview.ui.GtkPreviewPanel
import io.github.andrepg.shared.log.Log

/**
 * Renders the currently active `.ui` file to a PNG and displays it
 * in the GTK Preview panel.
 */
class GtkPreviewRenderAction(
    private val panel: GtkPreviewPanel,
) : AnAction(
        "Render Preview",
        "Render the current GTK preview",
        AllIcons.Actions.Execute,
    ) {
    private val log = Log.getInstance(GtkPreviewRenderAction::class.java)

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project

        if (project == null || project.isDisposed) return

        this.callBackgroundTask(project)
    }

    fun callBackgroundTask(project: Project) {
        val task = GtkPreviewBackgroundRenderTask(project, panel)

        ProgressManager.getInstance().run(task)
    }
}
