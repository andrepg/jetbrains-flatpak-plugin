package io.github.andrepg.gtk.preview.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

/**
 * Resets the GTK Preview panel and triggers a fresh re-render.
 *
 * @property onRender callback invoked with the [AnActionEvent] so the
 *   caller can access the project and start the render
 */
class GtkPreviewRefreshAction(
    private val onRender: (Project) -> Unit,
) : AnAction(
        "Refresh Preview",
        "Re-render the current GTK preview",
        AllIcons.Actions.Refresh,
    ) {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { project -> onRender(project) }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
