package io.github.andrepg.gtk.preview

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import io.github.andrepg.shared.Localization

/**
 * Notification helpers for the GTK Preview feature.
 */
internal object GtkPreviewNotifications {
    private const val GROUP_ID = "io.github.andrepg.flatpak.preview"

    fun compilationStarted(project: Project) {
        NotificationGroupManager
            .getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(
                Localization.message("gtk.preview.compilation.title"),
                Localization.message("gtk.preview.compilation.started"),
                NotificationType.INFORMATION,
            ).notify(project)
    }

    fun compilationFailed(
        project: Project,
        error: String,
    ) {
        NotificationGroupManager
            .getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(
                Localization.message("gtk.preview.compilation.title"),
                Localization.message("gtk.preview.compilation.failed", error),
                NotificationType.ERROR,
            ).notify(project)
    }
}
