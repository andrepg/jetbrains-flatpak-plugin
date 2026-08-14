package io.github.andrepg.flatpak.runs.execution

import io.github.andrepg.flatpak.runs.InternalCommand
import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.shared.log.Log

/**
 * Determines which commands to execute based on user configuration.
 * Handles command selection and ordering.
 */
class CommandSelectionStrategy {
    private val log = Log.getInstance(CommandSelectionStrategy::class.java)

    companion object {
        private val CLEANABLE_COMMANDS = setOf(
            InternalCommand.BUILD,
            InternalCommand.RUN,
            InternalCommand.EXPORT
        )
    }

    /**
     * Builds the execution plan for the configuration: the main command plus
     * any cleanup pre-steps, in the order they must run.
     *
     * @param config The run configuration
     * @return the ordered execution plan
     */
    fun plan(config: FlatpakRunSettings): CommandPlan {
        val main = mapUserCommandToInternal(config.command)
        val preSteps = mutableListOf<InternalCommand>()

        if (main in CLEANABLE_COMMANDS) {
            if (config.enableForceClean) {
                preSteps += InternalCommand.CLEAN
            }
            if (config.enableDeepClean) {
                preSteps += InternalCommand.DEEP_CLEAN
            }
        }

        val plan = CommandPlan(main, preSteps)
        log.info("Selected command plan: ${plan.all.joinToString(" -> ")}")
        return plan
    }

    /**
     * Maps user-visible commands to internal commands.
     *
     * @param userCommand The user-visible command
     * @return The corresponding internal command
     */
    private fun mapUserCommandToInternal(userCommand: UserVisibleCommand): InternalCommand {
        return when (userCommand) {
            UserVisibleCommand.BUILD -> InternalCommand.BUILD
            UserVisibleCommand.CLEAN -> InternalCommand.CLEAN
            UserVisibleCommand.EXPORT -> InternalCommand.EXPORT
            UserVisibleCommand.RUN -> InternalCommand.RUN
            UserVisibleCommand.VALIDATE -> InternalCommand.VALIDATE
            UserVisibleCommand.CUSTOM -> InternalCommand.CUSTOM
        }
    }
}

/**
 * The ordered set of commands for a run: cleanup [preSteps] executed before
 * the [main] command.
 */
data class CommandPlan(
    val main: InternalCommand,
    val preSteps: List<InternalCommand> = emptyList(),
) {
    /** All commands in execution order: pre-steps first, then the main command. */
    val all: List<InternalCommand> get() = preSteps + main
}
