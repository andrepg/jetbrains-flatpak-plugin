package io.github.andrepg.gtk.preview

import io.github.andrepg.gtk.preview.tasks.GtkPreviewBackgroundRenderTask
import io.github.andrepg.gtk.preview.ui.GtkPreviewPanel
import io.github.andrepg.shared.license.PremiumFeatureGate
import io.github.andrepg.shared.log.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Serializes GTK preview renders.
 *
 * At most one render runs at a time. While a render is in flight, further
 * triggers are ignored (not queued/coalesced).
 */
class GtkPreviewRenderScheduler(
    private val panel: GtkPreviewPanel,
) {
    private val log = Log.getInstance(GtkPreviewRenderScheduler::class.java)

    private val running = AtomicBoolean(false)

    fun isRunning(): Boolean = running.get()

    /**
     * Schedules a render of the currently open `.ui` file. If a render is
     * already running, the request is ignored.
     */
    fun scheduleRender(project: com.intellij.openapi.project.Project) {
        if (project.isDisposed) return

        // By checking premium status here we avoid restarting the IDE after a purchase
        if (!PremiumFeatureGate.isPremiumAvailable()) {
            log.debug("Skipping preview render; premium feature is locked")
            return
        }

        if (!running.compareAndSet(false, true)) {
            log.debug("Skipping preview render; already running")
            return
        }

        startRender(project)
    }

    private fun startRender(project: com.intellij.openapi.project.Project) {
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                renderFinished()
                return@invokeLater
            }
            try {
                runOnEdt(project)
            } catch (t: Throwable) {
                log.warn("Failed to start GTK preview render", t)
                renderFinished()
            }
        }
    }

    /**
     * EDT: flips the panel to the loading state, captures its current size and
     * hands the render to the platform progress manager.
     */
    private fun runOnEdt(project: com.intellij.openapi.project.Project) {
        if (!io.github.andrepg.shared.license.PremiumFeatureGate.isPremiumAvailable()) {
            renderFinished()
            return
        }
        panel.setLoading(true)
        panel.setFailed(false)
        panel.errorMessage = null
        panel.renderedImage = null
        panel.refresh()

        val (width, height) = RenderDimensions.renderSize(panel)

        val task =
            GtkPreviewBackgroundRenderTask(project, panel, width, height) {
                renderFinished()
            }

        com.intellij.openapi.progress.ProgressManager.getInstance().run(task)
    }

    /**
     * Called from the render task's `onFinished` (on the EDT): releases the
     * running guard.
     */
    private fun renderFinished() {
        running.set(false)
    }
}
