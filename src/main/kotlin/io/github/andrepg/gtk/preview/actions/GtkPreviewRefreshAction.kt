package io.github.andrepg.gtk.preview.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Resets the GTK Preview panel and triggers a fresh re-render.
 *
 * @property onRender callback invoked with the [AnActionEvent] so the
 *   caller can access the project and start the render
 */
class GtkPreviewRefreshAction(
    private val onRender: (AnActionEvent) -> Unit,
) : AnAction(
        "Refresh Preview",
        "Re-render the current GTK preview",
        AllIcons.Actions.Refresh,
    ) {
    override fun actionPerformed(e: AnActionEvent) {
        onRender(e)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
