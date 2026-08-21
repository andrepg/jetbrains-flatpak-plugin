package io.github.andrepg.flatpak.runs.commands

import io.github.andrepg.flatpak.runs.InternalCommand
import io.github.andrepg.flatpak.runs.UserVisibleCommand
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandExecutionStrategyTest {
    private val strategy = CommandExecutionStrategy()

    @Test
    fun `maps each user command to its internal command`() {
        assertEquals(InternalCommand.BUILD, strategy.mapUserCommandToInternal(UserVisibleCommand.BUILD))
        assertEquals(InternalCommand.EXPORT, strategy.mapUserCommandToInternal(UserVisibleCommand.EXPORT))
        assertEquals(InternalCommand.RUN, strategy.mapUserCommandToInternal(UserVisibleCommand.RUN))
        assertEquals(InternalCommand.VALIDATE, strategy.mapUserCommandToInternal(UserVisibleCommand.VALIDATE))
        assertEquals(InternalCommand.CUSTOM, strategy.mapUserCommandToInternal(UserVisibleCommand.CUSTOM))
    }
}
