package io.github.andrepg.gtk.preview.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import io.github.andrepg.gtk.isGtkUiFile
import io.github.andrepg.gtk.preview.GtkBuilderToolRunner
import io.github.andrepg.gtk.preview.GtkPreviewNotifications
import io.github.andrepg.gtk.preview.RenderDimensions
import io.github.andrepg.gtk.preview.UiTemplateResolver
import io.github.andrepg.gtk.preview.ui.GtkPreviewPanel
import io.github.andrepg.shared.log.Log
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicLong

/**
 * Renders the currently active `.ui` file to a PNG and displays it
 * in the GTK Preview panel.
 *
 * [triggerRender] is the single entry point for both user-triggered
 * actions (toolbar button) and automatic re-renders (file save listener).
 * Each invocation is generation-stamped so stale renders never overwrite
 * a newer one.
 */
class GtkPreviewRenderAction(
    private val panel: GtkPreviewPanel,
    private val portraitSide: () -> Boolean = { true },
) : AnAction(
        "Render Preview",
        "Render the current GTK preview",
        AllIcons.Actions.Execute,
    ) {
    private val log = Log.getInstance(GtkPreviewRenderAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        triggerRender(project)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    /**
     * Compiles the renderer (if needed) and renders the active `.ui`
     * file to a PNG displayed in [panel].
     */
    fun triggerRender(project: Project) {
        if (project.isDisposed) return

        val generation = renderGeneration.incrementAndGet()

        panel.setFailed(false)
        panel.setLoading(true)
        panel.refresh()

        val task =
            object : Task.Backgroundable(project, "Rendering GTK preview", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    doRender(project, indicator, generation)
                }

                override fun onFinished() {
                    if (project.isDisposed) return
                    applyIfCurrent(generation) {
                        setLoading(false)
                    }
                    panel.refresh()
                }
            }

        ProgressManager.getInstance().run(task)
    }

    private fun doRender(
        project: Project,
        indicator: ProgressIndicator,
        generation: Long,
    ) {
        try {
            val uiFile = findActiveUiFile(project)
            if (uiFile == null) {
                applyIfCurrent(generation) {
                    setFailed(true)
                    errorMessage = "No .ui file open"
                }
                return
            }

            val toolRunner = GtkBuilderToolRunner()
            if (toolRunner.needsCompilation()) {
                GtkPreviewNotifications.compilationStarted(project)
            }
            indicator.text = "Compiling renderer\u2026"
            val binary = toolRunner.compile()

            indicator.text = "Rendering ${uiFile.fileName}\u2026"
            val outputPng = Files.createTempFile(configDir(), "preview-", ".png")

            // Resolve project templates (custom widget classes) into plain
            // GTK4 objects so the C renderer can display them.  Skip zero work
            // when the project has no template definitions or none match.
            var renderInput = uiFile
            val projectBase = project.basePath?.let { Paths.get(it) }
            if (projectBase != null) {
                val original = Files.readString(uiFile)
                val resolved = UiTemplateResolver.resolve(original, projectBase)
                if (resolved != original) {
                    renderInput =
                        Files
                            .createTempFile(configDir(), "resolved-", ".ui")
                            .also { it.toFile().writeText(resolved) }
                }
            }

            try {
                val (width, height) = RenderDimensions.renderSize(portraitSide())
                toolRunner.render(binary, renderInput, outputPng, width, height)
            } finally {
                if (renderInput != uiFile) Files.deleteIfExists(renderInput)
            }

            applyIfCurrent(generation) {
                renderedImage = outputPng
            }
        } catch (e: Exception) {
            log.warn("Preview render failed", e)
            applyIfCurrent(generation) {
                setFailed(true)
                errorMessage = e.message ?: "Render failed"
            }
            GtkPreviewNotifications.compilationFailed(project, e.message ?: "Unknown error")
        }
    }

    private fun applyIfCurrent(
        generation: Long,
        block: GtkPreviewPanel.() -> Unit,
    ) {
        if (generation == renderGeneration.get()) {
            panel.block()
        }
    }

    private fun findActiveUiFile(project: Project): java.nio.file.Path? {
        val file = FileEditorManager.getInstance(project).selectedEditor?.file ?: return null
        if (!isGtkUiFile(file.name)) return null
        return Paths.get(file.path)
    }

    private fun configDir(): java.nio.file.Path =
        io.github.andrepg.gtk.preview.GtkPreviewConfig.configDir
            .toPath()

    private companion object {
        val renderGeneration = AtomicLong(0)
    }
}
