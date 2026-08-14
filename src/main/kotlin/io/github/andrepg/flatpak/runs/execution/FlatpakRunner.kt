package io.github.andrepg.flatpak.runs.execution

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
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

        val plan = CommandSelectionStrategy().plan(config)
        val engine = CommandExecutionEngine(environment.project)

        val cleanupCommandLines = plan.preSteps.map { engine.buildCommand(it, config) }
        val mainCommandLine = engine.buildCommand(plan.main, config)

        val handler = when (cleanupCommandLines.isEmpty()) {
            true -> engine.executeCommandSequence(mainCommandLine)
            false -> CleanupThenProcessHandler(
                cleanupCommandLines = cleanupCommandLines,
                mainCommandLine = engine.toGeneralCommandLine(mainCommandLine),
                workDir = environment.project.workspaceFile
            )
        }

        handler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                event.processHandler.detachProcess()
                log.info("Flatpak command terminated with exit code ${event.exitCode}")
            }
        })

        handler.startNotify()

        return handler
    }
}
