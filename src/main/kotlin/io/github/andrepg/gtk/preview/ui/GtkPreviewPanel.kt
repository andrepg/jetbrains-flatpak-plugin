package io.github.andrepg.gtk.preview.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.Alarm
import io.github.andrepg.shared.Localization
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Image
import java.io.File
import javax.imageio.ImageIO
import javax.swing.*

/**
 * The GTK preview panel.
 *
 * IDE glue: tracks the active editor, debounces re-render on document change,
 * and shows the PNG or a status/error text.
 */
class GtkPreviewPanel(private val project: Project, toolWindow: ToolWindow) : JPanel(BorderLayout()), Disposable {
    private val service = project.getService(GtkPreviewService::class.java)
    private val toolWindowContentManager = toolWindow.contentManager
    private val imageLabel = JBLabel(Localization.message("preview.panel.no-preview"), SwingConstants.CENTER)
    private val scrollPane = JBScrollPane(imageLabel)
    private val fileLabel = JBLabel(" ")
    private val statusLabel = JBLabel(" ")
    private val previewButton = JButton(Localization.message("preview.notification.preview")).apply {
            addActionListener {
                currentFile?.let { onFileChanged(it) }
            }
        }

    
    private val debounceTimer = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private var currentFile: VirtualFile? = null
    private var currentValidation: GtkPreviewService.ValidationResult? = null

    init {
        layout = BorderLayout()
        add(buildToolbar(), BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        imageLabel.preferredSize = Dimension(400, 300)
        imageLabel.foreground = JBColor.GRAY
        scrollPane.border = BorderFactory.createEmptyBorder()
        setupEditorTracking()
        setupToolWindowTracking()
    }

    private fun buildToolbar(): JComponent {
        val toolbar = JPanel(BorderLayout())
        toolbar.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)

        val leftPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(fileLabel)
        }

        val rightPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(statusLabel)
            add(Box.createHorizontalStrut(8))
            add(previewButton)
        }

        toolbar.add(leftPanel, BorderLayout.WEST)
        toolbar.add(rightPanel, BorderLayout.EAST)
        return toolbar
    }

    private fun setupEditorTracking() {
        val connection = project.messageBus.connect(this)
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                onFileChanged(event.newFile)
            }
        })

        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
                if (file != null && (file.name.endsWith(".ui") || file.name.endsWith(".glade"))) {
                    debounceTimer.cancelAllRequests()
                    debounceTimer.addRequest({ onFileChanged(file) }, 500)
                }
            }
        }, this)
    }

    private fun setupToolWindowTracking() {
        toolWindowContentManager.addContentManagerListener(object : ContentManagerListener {
            override fun selectionChanged(event: ContentManagerEvent) {
                if (event.content.component === this@GtkPreviewPanel) {
                    onToolWindowShown()
                }
            }
        })
    }

    private fun onFileChanged(file: VirtualFile?) {
        currentFile = file
        fileLabel.text = file?.name ?: " "
        if (file == null || !file.name.endsWith(".ui") && !file.name.endsWith(".glade")) {
            setImage(null)
            statusLabel.text = Localization.message("preview.panel.select-file")
            return
        }

        statusLabel.text = Localization.message("preview.panel.validating")
        ApplicationManager.getApplication().executeOnPooledThread {
            val validation = service.validate(file)
            currentValidation = validation
            ApplicationManager.getApplication().invokeLater {
                updateStatus(validation)
                if (isVisible) {
                    renderIfGatePassed(validation)
                }
            }
        }
    }

    private fun onToolWindowShown() {
        currentFile?.let { file ->
            if (file.name.endsWith(".ui") || file.name.endsWith(".glade")) {
                currentValidation?.let { renderIfGatePassed(it) }
            }
        }
    }

    private fun renderIfGatePassed(validation: GtkPreviewService.ValidationResult) {
        val file = currentFile ?: return
        val branch = validation.branch ?: return
        val canRender = validation.gatePassed || (validation.adwUnsupported && validation.ldPreload != null)
        if (!canRender) return

        statusLabel.text = Localization.message("preview.panel.rendering")
        ApplicationManager.getApplication().executeOnPooledThread {
            val pngFile = service.render(file, branch, validation.ldPreload?.let { File(it) }).pngFile
            ApplicationManager.getApplication().invokeLater {
                if (pngFile != null) {
                    setImage(pngFile)
                    statusLabel.text = " "
                } else {
                    statusLabel.text = Localization.message("preview.panel.render-failed")
                }
            }
        }
    }

    private fun updateStatus(validation: GtkPreviewService.ValidationResult) {
        when {
            validation.message != null -> {
                statusLabel.text = validation.message
                previewButton.isEnabled = false
            }
            validation.adwUnsupported -> {
                statusLabel.text = Localization.message("preview.notification.adw-unsupported")
                // With a shim compiled, rendering may still succeed even if the
                // validator flagged unknown types; keep the button available.
                previewButton.isEnabled = validation.ldPreload != null
            }
            validation.gatePassed -> {
                statusLabel.text = " "
                previewButton.isEnabled = true
            }
            else -> {
                statusLabel.text = Localization.message("preview.notification.validation-failed")
                previewButton.isEnabled = false
            }
        }
    }

    private fun setImage(pngFile: File?) {
        if (pngFile == null) {
            imageLabel.icon = null
            imageLabel.text = Localization.message("preview.panel.no-preview")
            return
        }

        val image = ImageIO.read(pngFile)
        val scaled = image.getScaledInstance(image.width, image.height, Image.SCALE_SMOOTH)
        imageLabel.icon = ImageIcon(scaled)
        imageLabel.text = ""
    }

    override fun dispose() {
        debounceTimer.cancelAllRequests()
    }

    fun requestRender(file: VirtualFile) {
        currentFile = file
        onFileChanged(file)
    }
}
