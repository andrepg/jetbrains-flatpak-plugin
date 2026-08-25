package io.github.andrepg.gtk.preview.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.components.JBLabel
import io.github.andrepg.gtk.preview.GtkPreviewRefreshAction
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JPanel

class GtkPreviewPanel {
    private fun buildToolbar(actionGroup: DefaultActionGroup): ActionToolbar =
        ActionManager.getInstance().createActionToolbar(
            "GTK Preview",
            actionGroup,
            true,
        )

    private fun buildActionGroup(): DefaultActionGroup =
        DefaultActionGroup().apply {
            add(GtkPreviewRefreshAction())
        }

    fun getPanel(): JPanel =
        JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder()

            val actionGroup = buildActionGroup()
            val toolbar = buildToolbar(actionGroup)

            toolbar.targetComponent = this@apply

            add(toolbar.component, BorderLayout.NORTH)
            add(JBLabel("Preview"), BorderLayout.CENTER)
        }
}
