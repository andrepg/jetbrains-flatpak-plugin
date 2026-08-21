package io.github.andrepg.flatpak.runs.steps

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.flatpak.runs.cleanup.DeepCleanExecutor
import io.github.andrepg.flatpak.runs.cleanup.StaleFuseMountCleaner
import io.github.andrepg.flatpak.runs.commands.CommandExecutionArguments
import io.github.andrepg.flatpak.runs.commands.CommandExecutionEngine
import io.github.andrepg.flatpak.runs.commands.CommandExecutionStrategy
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.shared.log.Log
import java.io.File

/**
 * Run state that executes the configured Flatpak command.
 *
 * Implements [CommandLineState] to compose the process command line and attach the console
 * output view while the command runs. When the deep clean flag is enabled for the BUILD
 * command, the VFS deep clean runs first as a pre-step of [CommandChainProcessHandler].
 */
class FlatpakRunner(
    environment: ExecutionEnvironment,
    private val config: FlatpakRunSettings,
) : CommandLineState(environment) {
    private val log = Log.getInstance(FlatpakRunner::class.java)

    private val engine = CommandExecutionEngine(environment.project)
    private val strategy = CommandExecutionStrategy().mapUserCommandToInternal(config.command)

    /**
     * Composes the Flatpak command line and starts the underlying process chain.
     *
     * @return the handler managing the process chain
     */
    override fun startProcess(): ProcessHandler {
        log.info(generateArgumentsLogString(config))

        val commandLine = engine.buildCommand(strategy, config)
        val generalCommandLine =
            engine
                .toGeneralCommandLine(commandLine)
                .withWorkDirectory(environment.project.basePath)

        val preSteps =
            buildList {
                add(
                    CommandChainProcessHandler.PreStep(PreStepType.UNMOUNT_STALE, quiet = true) { report ->
                        StaleFuseMountCleaner().clean(resolveBuildDir(), report)
                    },
                )
                if (config.enableDeepClean && config.command == UserVisibleCommand.BUILD) {
                    add(
                        CommandChainProcessHandler.PreStep(PreStepType.DEEP_CLEAN) { _ ->
                            DeepCleanExecutor().clean(environment.project, config)
                        },
                    )
                }
            }

        return CommandChainProcessHandler(
            commandLines = listOf(generalCommandLine),
            commandSteps = listOf(strategy),
            engine = engine,
            preSteps = preSteps,
        )
    }

    private fun generateArgumentsLogString(settings: FlatpakRunSettings): String {
        val extraConfig =
            buildList {
                if (config.enableForceClean) add(CommandExecutionArguments.FORCE_CLEAN)
                if (config.enableWayland) add("Wayland")
                if (config.enablePortals) add("Portals")
                if (config.enableThemes) add("Themes")
                if (config.enableAudio) add("Audio")
            }

        return "Flatpak run started. Running {%s} with command {%s}\nFeatures: {%s}".format(
            config.manifestPath,
            config.command,
            extraConfig.joinToString(" | "),
        )
    }

    /** Build dir resolved against the project root, mirroring [DeepCleanExecutor]. */
    private fun resolveBuildDir(): File {
        val buildDir = File(config.buildDir)
        return if (buildDir.isAbsolute) buildDir else File(environment.project.basePath, config.buildDir)
    }
}
