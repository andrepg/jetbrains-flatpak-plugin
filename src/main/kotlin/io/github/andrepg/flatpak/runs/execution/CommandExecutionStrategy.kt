package io.github.andrepg.flatpak.runs.execution

import io.github.andrepg.flatpak.runs.InternalCommand
import io.github.andrepg.flatpak.runs.UserVisibleCommand

/**
 * Determines which commands to execute based on user configuration.
 * Handles command selection and ordering.
 */
class CommandExecutionStrategy {
    fun mapUserCommandToInternal(userCommand: UserVisibleCommand): InternalCommand {
        return when (userCommand) {
            UserVisibleCommand.BUILD -> InternalCommand.BUILD
            UserVisibleCommand.EXPORT -> InternalCommand.EXPORT
            UserVisibleCommand.RUN -> InternalCommand.RUN
            UserVisibleCommand.VALIDATE -> InternalCommand.VALIDATE
            UserVisibleCommand.CUSTOM -> InternalCommand.CUSTOM
        }
    }
}