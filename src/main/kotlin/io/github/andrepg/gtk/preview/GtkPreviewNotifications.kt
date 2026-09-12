package io.github.andrepg.gtk.preview

import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import io.github.andrepg.gtk.GtkNotifications
import io.github.andrepg.shared.Localization

/**
 * Notification helpers for the GTK Preview feature.
 */
internal object GtkPreviewNotifications {
    fun compilationStarted(project: Project) {
        GtkNotifications.notify(
            project,
            GtkNotifications.PREVIEW_GROUP_ID,
            Localization.message("gtk.preview.compilation.title"),
            Localization.message("gtk.preview.compilation.started"),
            NotificationType.INFORMATION,
        )
    }

    fun compilationFailed(
        project: Project,
        error: String,
    ) {
        GtkNotifications.notify(
            project,
            GtkNotifications.PREVIEW_GROUP_ID,
            Localization.message("gtk.preview.compilation.title"),
            Localization.message("gtk.preview.compilation.failed", error),
            NotificationType.ERROR,
        )
    }
}