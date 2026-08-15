package io.github.andrepg.flatpak.runs.execution

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.currentOrDefaultProject
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.shared.log.Log

/**
 * Run state that executes the configured Flatpak command.
 *
 * Implements [CommandLineState] to compose the process command line and attach the console
 * output view while the command runs.
 */
class FlatpakRunner(
    environment: ExecutionEnvironment,
    private val config: FlatpakRunSettings
) : CommandLineState(environment) {
    private val log = Log.getInstance(FlatpakRunner::class.java)

    private val engine = CommandExecutionEngine(environment.project)
    private val strategy = CommandExecutionStrategy().create(config)

    /**
     * Composes the Flatpak command line and starts the underlying OS process.
     *
     * Cleanup pre-steps (CLEAN/DEEP_CLEAN) run as their own processes in the
     * background via [CleanupThenProcessHandler], never on the EDT, so they are
     * never flattened into a single command line.
     *
     * @return the handler managing the started process
     */
    override fun startProcess(): ProcessHandler {
        log.info(
            "Flatpak run started: command=${config.command}, manifest=${config.manifestPath}, " +
                    "buildDir=${config.buildDir}, forceClean=${config.enableForceClean}, " +
                    "deepClean=${config.enableDeepClean}, portals=${config.enablePortals}, " +
                    "themes=${config.enableThemes}, audio=${config.enableAudio}, wayland=${config.enableWayland}"
        )

        val commandLines = strategy.all.map { step ->
            engine.toGeneralCommandLine(engine.buildCommand(step, config))
        }

        return CommandChainProcessHandler(
            commandLines = commandLines,
            workDir = currentOrDefaultProject(environment.project).workspaceFile,
            engine = engine
        ).also {
            it.startNotify()
        }
    }
}
