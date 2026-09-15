package io.github.andrepg.gtk.preview.tasks

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import io.github.andrepg.gtk.isGtkUiFile
import io.github.andrepg.gtk.preview.GtkBuilderToolRunner
import io.github.andrepg.gtk.preview.GtkPreviewConfig
import io.github.andrepg.gtk.preview.GtkPreviewNotifications
import io.github.andrepg.gtk.preview.RenderDimensions
import io.github.andrepg.gtk.preview.ui.GtkPreviewPanel
import io.github.andrepg.shared.Localization
import java.nio.file.Files
import java.nio.file.Path

/**
 * Compiles the GTK preview renderer (when needed) and renders the active `.ui`
 * file to a PNG, then applies the result to the [GtkPreviewPanel].
 *
 * All compilation/rendering happens on the background thread in [run]; the
 * panel is never touched there. The EDT callbacks apply the final state:
 * [onFinished] swaps in either the rendered image or the failure message, and
 * [onThrowable] just records the failure so [onFinished] renders the error
 * panel.
 */
class GtkPreviewBackgroundRenderTask : Task.Backgroundable {
    /**
     * GTK compiler binary handler to compile and invoke the renderer.
     */
    private val gtkCompiler: GtkBuilderToolRunner = GtkBuilderToolRunner()

    private val configDirectory = GtkPreviewConfig.configDir.toPath()

    /**
     * Content panel to render the output.
     */
    private var rendererPanel: GtkPreviewPanel

    /**
     * Path of the most recently rendered PNG, produced on the background thread
     * and consumed by [onFinished] on the EDT.
     */
    private var renderedPath: Path? = null

    /**
     * Human-readable failure reason (no active `.ui` file or a thrown error),
     * consumed by [onFinished] on the EDT.
     */
    private var failureMessage: String? = null

    constructor(
        project: Project,
        panel: GtkPreviewPanel,
    ) : super(
        project,
        Localization.message("gtk.preview.compilation.title"),
        true,
    ) {
        this.rendererPanel = panel
    }

    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true
        indicator.text = Localization.message("gtk.preview.compilation.title")

        GtkPreviewNotifications.compilationStarted(project)

        if (gtkCompiler.needsCompilation()) {
            indicator.text = Localization.message("gtk.preview.compilation.started")
        }

        val binary = gtkCompiler.compile()

        val openedFile = getActiveTemplate(project)
        if (openedFile == null) {
            failureMessage = Localization.message("gtk.preview.compilation.ui_not_found")
            return
        }

        indicator.text = Localization.message("gtk.preview.compilation.rendering")

        val outputFile = Files.createTempFile(configDirectory, "preview-", ".png")
        val (width, height) = RenderDimensions.renderSize(true)

        renderedPath =
            gtkCompiler.render(
                binary = binary,
                uiFile = openedFile,
                outputPng = outputFile,
                width = width,
                height = height,
                searchDir = openedFile.parent,
            )
    }

    /**
     * Applies the final state to the panel. Runs on the EDT after [run]
     * (or [onThrowable]) completes.
     */
    override fun onFinished() {
        super.onFinished()
        rendererPanel.setLoading(false)
        rendererPanel.setFailed(failureMessage != null)
        rendererPanel.errorMessage = failureMessage
        rendererPanel.renderedImage = renderedPath
        rendererPanel.refresh()
    }

    override fun onSuccess() {
        super.onSuccess()
    }

    /**
     * Called when [run] throws an error. Only records the failure so
     * [onFinished] can render the error panel; the notification is posted here.
     */
    override fun onThrowable(error: Throwable) {
        super.onThrowable(error)

        GtkPreviewNotifications.compilationFailed(project, error.message ?: "")

        failureMessage = error.localizedMessage ?: error.message
    }

    /**
     * Get GTK template path from the opened editor when valid.
     *
     * @param project project opened at the IDE
     * @return file path or null
     */
    private fun getActiveTemplate(project: Project): Path? {
        val file = FileEditorManager.getInstance(project).selectedEditor?.file ?: return null
        return if (isGtkUiFile(file.name)) file.toNioPath() else null
    }
}