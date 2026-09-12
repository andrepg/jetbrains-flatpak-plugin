package io.github.andrepg.flatpak.runs.configuration

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.andrepg.flatpak.exception.FlatpakConfigurationException
import io.github.andrepg.flatpak.runs.FlatpakDefaults
import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.shared.Localization
import io.github.andrepg.shared.log.Log

/**
 * Programmatic creation and deduplication of Flatpak run configurations.
 */
class FlatpakRunGenerator {
    companion object {
        private val log = Log.getInstance(FlatpakRunGenerator::class.java)

        /**
         * @return the registered [FlatpakRunSettingsType] for this configuration type
         */
        fun factory(): FlatpakRunSettingsFactory {
            val type =
                try {
                    ConfigurationTypeUtil.findConfigurationType(FlatpakRunSettingsType::class.java)
                } catch (e: Exception) {
                    throw FlatpakConfigurationException("Flatpak run configuration type is not registered", e)
                }
            return type.configurationFactories.firstOrNull() as? FlatpakRunSettingsFactory
                ?: throw FlatpakConfigurationException("Flatpak run configuration factory is not registered")
        }

        /**
         * The default run-configuration name for a command and app-id, e.g. `[build] org.example.App`.
         */
        fun formatRunName(
            command: UserVisibleCommand,
            appId: String,
        ): String = Localization.message("runs.configuration.name", command.name.lowercase(), appId)

        /**
         * Populates a newly created Flatpak run configuration for [file].
         * Shared by [createForManifest] and the run-configuration producer so
         * both entry points build the same `[build] <app-id>` configuration.
         */
        fun configureForManifest(
            configuration: FlatpakRunSettings,
            file: VirtualFile,
            appId: String,
        ) {
            configuration.command = UserVisibleCommand.BUILD
            configuration.manifestPath = file.path
            configuration.buildDir = FlatpakDefaults.BUILD_DIR.value
            configuration.name = formatRunName(UserVisibleCommand.BUILD, appId)
        }

        /**
         * Creates a `[build] <app-id>` run configuration for [file], reusing the existing one when a
         * configuration with the same [VirtualFile] is already registered.
         *
         * @return the existing or newly created settings
         */
        fun createForManifest(
            project: Project,
            file: VirtualFile,
            appId: String,
        ): RunnerAndConfigurationSettings {
            val runManager = RunManager.getInstance(project)
            findExisting(project, file)?.let { existing ->
                log.debug("Reusing existing run configuration for ${file.path}: ${existing.name}")
                return existing
            }

            val name = formatRunName(UserVisibleCommand.BUILD, appId)
            val settings =
                runManager.createConfiguration(
                    name,
                    factory(),
                )
            val configuration = settings.configuration as FlatpakRunSettings
            configureForManifest(configuration, file, appId)
            runManager.addConfiguration(settings)
            log.info("Created run configuration '$name' for ${file.path}")
            return settings
        }

        /**
         * @return an existing Flatpak configuration targeting [file], or null
         */
        fun findExisting(
            project: Project,
            file: VirtualFile,
        ): RunnerAndConfigurationSettings? =
            RunManager.getInstance(project).allSettings.firstOrNull { settings ->
                settings.type is FlatpakRunSettingsType &&
                    (settings.configuration as? FlatpakRunSettings)?.manifestPath == file.path
            }
    }
}
