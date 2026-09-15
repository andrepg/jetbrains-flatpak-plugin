package io.github.andrepg.gtk.preview.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.andrepg.gtk.preview.GtkPreviewRenderScheduler
import io.github.andrepg.shared.Localization

/**
 * Renders the currently active `.ui` file to a PNG and displays it in the GTK
 * Preview panel.
 *
 * This is just the render entry point: the panel state flip, the size read and
 * the render itself are delegated to [GtkPreviewRenderScheduler], which
 * serializes renders and keeps every Swing mutation on the EDT. Safe to invoke
 * from [getActionUpdateThread] = BGT — `actionPerformed` only posts to the
 * scheduler and returns.
 */
class GtkPreviewRenderAction(
    private val renderScheduler: GtkPreviewRenderScheduler,
) : AnAction(
        Localization.message("gtk.preview.action.render.name"),
        Localization.message("gtk.preview.action.render.description"),
        AllIcons.Actions.Execute,
    ) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (project.isDisposed) return
        renderScheduler.scheduleRender(project)
    }
}
