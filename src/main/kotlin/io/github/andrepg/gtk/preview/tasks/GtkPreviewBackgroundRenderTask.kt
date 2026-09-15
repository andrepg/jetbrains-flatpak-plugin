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
import java.time.Instant
import java.util.Date

class GtkPreviewBackgroundRenderTask : Task.Backgroundable {
    /**
     * GTK Compiler binary handler to fire and compile interface
     */
    private val gtkCompiler: GtkBuilderToolRunner = GtkBuilderToolRunner()

    private val configDirectory = GtkPreviewConfig.configDir.toPath()

    /**
     * Content panel to render the output
     */
    private var rendererPanel: GtkPreviewPanel

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

        onStarted()

        if (gtkCompiler.needsCompilation()) {
            indicator.text = Localization.message("gtk.preview.compilation.started")
        }

        val binary = gtkCompiler.compile()

        indicator.text = Localization.message("gtk.preview.compilation.rendering")

        val openedFile = getActiveTemplate(project) ?: return

        val outputFile = Files.createTempFile(configDirectory, Date.from(Instant.now()).toString(), ".png")
        val (width, height) = RenderDimensions.renderSize(true)

        // TODO Maybe move this to a property to free it later on onSuccess
        val result = gtkCompiler.render(binary, openedFile, outputFile, width, height)

        System.console().writer().print(result.toString())
    }

    private fun onStarted() {
        GtkPreviewNotifications.compilationStarted(project)

        rendererPanel.setLoading(true)
        rendererPanel.setFailed(false)
        rendererPanel.refresh()
    }

    override fun onFinished() {
        super.onFinished()
        rendererPanel.setLoading(false)
        rendererPanel.refresh()
    }

    override fun onSuccess() {
        super.onSuccess()
    }

    /**
     * Called when runner throws an error
     */
    override fun onThrowable(error: Throwable) {
        super.onThrowable(error)

        GtkPreviewNotifications.compilationFailed(project, error.message ?: "")

        rendererPanel.setLoading(false)
        rendererPanel.setFailed(true)
        rendererPanel.errorMessage = error.localizedMessage
        rendererPanel.refresh()
    }

    /**
     * Get GTK template path from opened editor when is valid
     *
     * @param project project opened at IDE
     * @return file path or null
     */
    private fun getActiveTemplate(project: Project): Path? {
        val file = FileEditorManager.getInstance(project).selectedEditor?.file ?: return null

        return if (isGtkUiFile(file.name)) file.toNioPath() else null
    }
}
