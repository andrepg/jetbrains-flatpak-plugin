package io.github.andrepg.gtk.preview

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class GtkPreviewRefreshAction :
    AnAction(
        "Refresh Preview",
        "Refresh the GTK preview",
        AllIcons.Actions.Refresh,
    ) {
    override fun actionPerformed(e: AnActionEvent) {
        // TODO: wire up refresh logic
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
