package io.github.andrepg.flatpak.runs.configuration

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.shared.Localization

/**
 * Programmatic creation and deduplication of Flatpak run configurations.
 */
class FlatpakRunGenerator {

    companion object {

        /**
         * @return the registered [FlatpakRunSettingsType] for this configuration type
         */
        fun factory(): FlatpakRunSettingsFactory {
            val type = ConfigurationTypeUtil.findConfigurationType(FlatpakRunSettingsType::class.java)
            return type.configurationFactories.first() as FlatpakRunSettingsFactory
        }

        /**
         * Creates a `Build <app-id>` run configuration for [file], reusing the existing one when a
         * configuration with the same [VirtualFile] is already registered.
         *
         * @return the existing or newly created settings
         */
        fun createForManifest(
            project: Project,
            file: VirtualFile,
            appId: String
        ): RunnerAndConfigurationSettings {
            val runManager = RunManager.getInstance(project)
            findExisting(project, file)?.let { return it }

            val settings = runManager.createConfiguration(
                Localization.message("runs.configuration.build.name", appId),
                factory()
            )
            val configuration = settings.configuration as FlatpakRunSettings
            configuration.command = UserVisibleCommand.BUILD
            configuration.manifestPath = file.path
            configuration.buildDir = FlatpakRunSettingsAttributes().buildDir ?: "build"
            runManager.addConfiguration(settings)
            return settings
        }

        /**
         * @return an existing Flatpak configuration targeting [file], or null
         */
        fun findExisting(project: Project, file: VirtualFile): RunnerAndConfigurationSettings? =
            RunManager.getInstance(project).allSettings.firstOrNull { settings ->
                settings.type is FlatpakRunSettingsType &&
                    (settings.configuration as? FlatpakRunSettings)?.manifestPath == file.path
            }
    }
}
