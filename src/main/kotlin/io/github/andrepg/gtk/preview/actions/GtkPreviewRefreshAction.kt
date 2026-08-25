package io.github.andrepg.gtk.preview.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.andrepg.gtk.preview.ui.GtkPreviewPanel

class GtkPreviewRefreshAction(
    private val panel: GtkPreviewPanel,
) : AnAction(
        "Refresh Preview",
        "Refresh the GTK preview",
        AllIcons.Actions.Refresh,
    ) {
    override fun actionPerformed(e: AnActionEvent) {
        panel.setFailed(false)
        panel.refresh()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
