package io.github.andrepg.flatpak.runs.configuration

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import io.github.andrepg.flatpak.detection.FlatpakProjectDetector
import io.github.andrepg.shared.Localization
import io.github.andrepg.shared.log.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Project-open glue: detects Flatpak manifests in the background and offers to create run
 * configurations. Shown at most once per project ("shown once" flag in [PropertiesComponent]).
 */
class RunPostStartupDetection : ProjectActivity {

    private val log = Log.getInstance(RunPostStartupDetection::class.java)

    override suspend fun execute(project: Project) {
        if (project.isDisposed) return
        val properties = PropertiesComponent.getInstance(project)
        if (properties.getBoolean(FLAG_KEY, false)) {
            log.debug("Flatpak detection notification already shown for ${project.name}; skipping")
            return
        }

        val manifests = withContext(Dispatchers.Default) {
            FlatpakProjectDetector.findManifests(project)
        }
        if (project.isDisposed || manifests.isEmpty()) return

        log.info(
            "Offering run configurations for ${manifests.size} detected manifest(s) in ${project.name}: " +
                manifests.joinToString(", ") { it.second }
        )

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            properties.setValue(FLAG_KEY, true)
            showNotification(project, manifests)
        }
    }

    private fun showNotification(project: Project, manifests: List<Pair<VirtualFile, String>>) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                Localization.message("detection.notification.title"),
                Localization.message("detection.notification.body", describe(manifests)),
                NotificationType.INFORMATION
            )
        notification.addAction(
            NotificationAction.createSimple(Localization.message("detection.notification.action.create")) {
                manifests.forEach { (file, appId) ->
                    FlatpakRunGenerator.createForManifest(project, file, appId)
                }
                notification.expire()
            }
        )
        notification.notify(project)
    }

    private fun describe(manifests: List<Pair<VirtualFile, String>>): String {
        val visible = manifests.take(MAX_NAMES).joinToString(", ") { it.second }
        return if (manifests.size > MAX_NAMES) {
            Localization.message("detection.notification.body.more", visible, manifests.size - MAX_NAMES)
        } else {
            visible
        }
    }

    private companion object {
        const val FLAG_KEY = "io.github.andrepg.flatpak.notificationShown"
        const val NOTIFICATION_GROUP_ID = "io.github.andrepg.flatpak.detection"
        const val MAX_NAMES = 5
    }
}
