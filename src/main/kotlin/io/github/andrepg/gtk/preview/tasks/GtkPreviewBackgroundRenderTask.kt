package io.github.andrepg.gtk.preview.tasks

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import io.github.andrepg.gtk.isGtkUiFile
import io.github.andrepg.gtk.preview.GtkBuilderToolRunner
import io.github.andrepg.gtk.preview.GtkPreviewConfig
import io.github.andrepg.gtk.preview.GtkPreviewNotifications
import io.github.andrepg.gtk.preview.ui.GtkPreviewPanel
import io.github.andrepg.shared.Localization
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * Compiles the GTK preview renderer (when needed) and renders the active `.ui`
 * file to a PNG, then applies the result to the [GtkPreviewPanel].
 *
 * All compilation/rendering happens on the background thread in [run]; the
 * panel is never touched there. The EDT callback [onFinished] swaps in either
 * the rendered image or the failure message, and [onThrowable] just records
 * the failure so [onFinished] renders the error panel.
 *
 * @property onFinishedAction invoked from [onFinished] once the panel state is
 *   applied, letting the caller (the render scheduler) start the next pending
 *   render.
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
     * Render size in pixels, captured on the EDT at scheduling time so the
     * snapshot matches the panel's current bounds.
     */
    private var renderWidth: Int
    private var renderHeight: Int

    /**
     * Completion hook for the render scheduler, invoked on the EDT.
     */
    private val onFinishedAction: () -> Unit

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

    /**
     * Temp PNG produced by [run]; deleted from [onFinished] when it was not
     * applied to the panel (failure or cancellation).
     */
    private var tempOutput: Path? = null

    constructor(
        project: Project,
        panel: GtkPreviewPanel,
        renderWidth: Int,
        renderHeight: Int,
        onFinishedAction: () -> Unit = {},
    ) : super(
        project,
        Localization.message("gtk.preview.compilation.title"),
        true,
    ) {
        this.rendererPanel = panel
        this.renderWidth = renderWidth
        this.renderHeight = renderHeight
        this.onFinishedAction = onFinishedAction
    }

    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true
        indicator.text = Localization.message("gtk.preview.compilation.title")

        if (indicator.isCanceled) {
            failureMessage = Localization.message("gtk.preview.compilation.cancelled")
            return
        }

        // M2: free the previous render's PNG (already loaded by the panel, so
        // deleting it off the EDT cannot disturb the visible preview).
        rendererPanel.renderedImage?.let { previous ->
            runCatching { Files.deleteIfExists(previous) }
        }

        // M1: only announce a balloon when a (re)compilation actually happens;
        // plain re-renders (open, save, refresh) stay silent.
        if (gtkCompiler.needsCompilation()) {
            indicator.text = Localization.message("gtk.preview.compilation.started")
            GtkPreviewNotifications.compilationStarted(project)
        }

        val binary = gtkCompiler.compile()
        if (indicator.isCanceled) {
            failureMessage = Localization.message("gtk.preview.compilation.cancelled")
            return
        }

        val openedFile = getActiveTemplate(project)
        if (openedFile == null) {
            failureMessage = Localization.message("gtk.preview.compilation.ui_not_found")
            return
        }

        indicator.text = Localization.message("gtk.preview.compilation.rendering")

        val outputFile = Files.createTempFile(configDirectory, "preview-", ".png")
        tempOutput = outputFile

        // M4: a render the user cancelled must not apply a partial/stale result.
        val rendered =
            gtkCompiler.render(
                binary = binary,
                uiFile = openedFile,
                outputPng = outputFile,
                width = renderWidth,
                height = renderHeight,
                searchDir = openedFile.parent,
            )

        if (indicator.isCanceled) {
            failureMessage = Localization.message("gtk.preview.compilation.cancelled")
            return
        }

        // M5: the existence of a PNG file is not enough — reject empty/invalid
        // output so the error panel is shown instead of a blank preview.
        if (ImageIO.read(rendered.toFile()) == null) {
            failureMessage = Localization.message("gtk.preview.compilation.invalid_image")
            return
        }

        renderedPath = rendered
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

        // M2: delete a produced temp PNG that was not applied to the panel
        // (failure or cancellation mid-run).
        val temp = tempOutput
        if (temp != null && temp != renderedPath) {
            runCatching { Files.deleteIfExists(temp) }
        }

        onFinishedAction()
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
