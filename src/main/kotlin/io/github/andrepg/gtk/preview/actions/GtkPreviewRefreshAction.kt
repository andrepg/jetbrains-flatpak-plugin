package io.github.andrepg.gtk.preview.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import io.github.andrepg.gtk.preview.GtkPreviewRenderScheduler
import io.github.andrepg.shared.Localization

/**
 * Resets the GTK Preview panel and triggers a fresh re-render.
 *
 * @property onRender callback invoked with the [AnActionEvent] so the
 *   caller can access the project and start the render
 */
class GtkPreviewRefreshAction(
    private val scheduler: GtkPreviewRenderScheduler,
    private val onRender: (Project) -> Unit,
) : AnAction(
        Localization.message("gtk.preview.action.refresh.name"),
        Localization.message("gtk.preview.action.refresh.description"),
        AllIcons.Actions.Refresh,
    ) {
    override fun actionPerformed(e: AnActionEvent) {
        if (scheduler.isRunning()) return

        e.project?.let { project -> onRender(project) }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)

        e.presentation.isEnabled = !scheduler.isRunning()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
