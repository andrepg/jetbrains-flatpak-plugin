package io.github.andrepg.gtk

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * Shared balloon helpers for the GTK feature surface. Carries the notification
 * group ids so the preview and schema entry points post to the same groups
 * without duplicating the `NotificationGroupManager` ceremony.
 */
internal object GtkNotifications {
    const val PREVIEW_GROUP_ID = "io.github.andrepg.flatpak.preview"
    const val SCHEMA_GROUP_ID = "io.github.andrepg.flatpak.schema"

    fun notify(
        project: Project,
        groupId: String,
        title: String,
        content: String,
        type: NotificationType,
    ) = Notification(groupId, title, content, type).notify(project)
}
