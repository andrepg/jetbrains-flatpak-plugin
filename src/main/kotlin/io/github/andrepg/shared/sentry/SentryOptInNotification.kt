package io.github.andrepg.shared.sentry

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import io.github.andrepg.shared.Localization
import io.github.andrepg.shared.diagnostics.DiagnosticsInitializer
import io.github.andrepg.shared.log.Log
import kotlinx.coroutines.Runnable

class SentryOptInNotification {
    private val log = Log.getInstance(DiagnosticsInitializer::class.java)

    val title = Localization.message("sentry.analytics-invitation.title")
    val description = Localization.message("sentry.analytics-invitation.message")

    // From plugin.xml notificationGroup
    val groupId = "io.github.andrepg.shared.diagnostics"

    // Current notification to display when required
    val notification = Notification(groupId, title, description, NotificationType.INFORMATION)

    fun notify(project: Project?) {
        notification.addActions(
            listOf(
                actionAccept(),
                actionForget(),
                actionDismiss(),
            ),
        )

        notification.notify(project)
    }

    private fun actionAccept(): NotificationAction =
        createAction(
            Localization.message("sentry.analytics-invitation.accept"),
        ) {
            log.info(Localization.message("sentry.analytics-invitation.accept"))
        }

    private fun actionForget(): NotificationAction =
        createAction(
            Localization.message("sentry.analytics-invitation.forget"),
        ) {
            log.info(Localization.message("sentry.analytics-invitation.forget"))
        }

    private fun actionDismiss(): NotificationAction =
        createAction(
            Localization.message("sentry.analytics-invitation.dismiss"),
        ) {
            log.info(Localization.message("sentry.analytics-invitation.dismiss"))
        }

    private fun createAction(
        title: String,
        action: Runnable,
    ): NotificationAction = NotificationAction.createSimple(title) { action.run() }
}
