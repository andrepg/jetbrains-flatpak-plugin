package io.github.andrepg.flatpak.runs.commands

import io.github.andrepg.flatpak.runs.InternalCommand
import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.shared.log.Log

/**
 * Determines which commands to execute based on user configuration.
 * Handles command selection and ordering.
 */
class CommandExecutionStrategy {
    private val log = Log.getInstance(CommandExecutionStrategy::class.java)

    fun mapUserCommandToInternal(userCommand: UserVisibleCommand): InternalCommand {
        val internal =
            when (userCommand) {
                UserVisibleCommand.BUILD -> InternalCommand.BUILD
                UserVisibleCommand.EXPORT -> InternalCommand.EXPORT
                UserVisibleCommand.RUN -> InternalCommand.RUN
                UserVisibleCommand.VALIDATE -> InternalCommand.VALIDATE
                UserVisibleCommand.CUSTOM -> InternalCommand.CUSTOM
            }
        log.debug("Mapped user command $userCommand -> $internal")
        return internal
    }
}
