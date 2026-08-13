package io.github.andrepg.flatpak.runs

import com.intellij.openapi.diagnostic.Logger
import io.github.andrepg.flatpak.runs.enums.FlatpakCommand

/**
 * Builds the effective command line used to run Flatpak commands.
 *
 * Delegates to [FlatpakRunStateMachine] to translate a [FlatpakCommand] into the underlying
 * `flatpak` / `flatpak-builder` invocation.
 */
class FlatpakRunExecutor {
    private val logger = Logger.getInstance(FlatpakRunExecutor::class.java)
    
    /**
     * Mounts the effective command line to run Flatpak
     * based on user preferences and command state machine
     */
    fun commandLine(
        flatpakCommand: FlatpakCommand,
        manifest: String,
        buildDir: String
    ): List<String> {
        val stateMachine = FlatpakRunStateMachine(buildDir, manifest)
        val command = stateMachine.getCommand(flatpakCommand)

        logger.info("Executing Flatpak command: ${flatpakCommand.name}")
        logger.info("Command: ${command.joinToString(" ")}")
        logger.info("Manifest: $manifest")
        logger.info("Build directory: $buildDir")

        return command
    }
}