package io.github.andrepg.flatpak.runs.configuration

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.andrepg.flatpak.exception.FlatpakConfigurationException
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
            val type = try {
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
        fun formatRunName(command: UserVisibleCommand, appId: String): String =
            Localization.message("runs.configuration.name", command.name.lowercase(), appId)

        /**
         * Creates a `[build] <app-id>` run configuration for [file], reusing the existing one when a
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
            findExisting(project, file)?.let { existing ->
                log.debug("Reusing existing run configuration for ${file.path}: ${existing.name}")
                return existing
            }

            val name = formatRunName(UserVisibleCommand.BUILD, appId)
            val settings = runManager.createConfiguration(
                name,
                factory()
            )
            val configuration = settings.configuration as FlatpakRunSettings
            configuration.command = UserVisibleCommand.BUILD
            configuration.manifestPath = file.path
            configuration.buildDir = FlatpakRunSettingsAttributes().buildDir ?: "_build"
            runManager.addConfiguration(settings)
            log.info("Created run configuration '$name' for ${file.path}")
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
