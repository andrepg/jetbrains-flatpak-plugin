package io.github.andrepg.gtk.preview.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.andrepg.gtk.preview.GtkPreviewRenderScheduler
import io.github.andrepg.shared.Localization

/**
 * Renders the currently active `.ui` file to a PNG and displays it
 * in the GTK Preview panel.
 */
class GtkPreviewRenderAction(
    private val renderScheduler: GtkPreviewRenderScheduler,
) : AnAction(
        "Render Preview",
        "Render the current GTK preview",
        AllIcons.Actions.Execute,
    ) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (project.isDisposed) return
        renderScheduler.scheduleRender(project)
    }
}
