package io.github.andrepg.flatpak.runs.execution

import com.intellij.openapi.project.Project
import io.github.andrepg.flatpak.runs.InternalCommand
import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class CommandSelectionStrategyTest {

    private val strategy = CommandSelectionStrategy()

    private fun config(configure: FlatpakRunSettings.() -> Unit = {}): FlatpakRunSettings {
        val configuration = FlatpakRunSettings(mock(Project::class.java), null, null)
        configuration.loadState(io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettingsAttributes())
        configure(configuration)
        return configuration
    }

    @Test
    fun `main command is always present`() {
        val plan = strategy.plan(
            config { command = UserVisibleCommand.VALIDATE }
        )
        assertEquals(CommandPlan(InternalCommand.VALIDATE), plan)
    }

    @Test
    fun `force clean prepends clean before build`() {
        val plan = strategy.plan(
            config {
                command = UserVisibleCommand.BUILD
                enableForceClean = true
            }
        )
        assertEquals(CommandPlan(InternalCommand.BUILD, listOf(InternalCommand.CLEAN)), plan)
    }

    @Test
    fun `deep clean prepends deep clean before run`() {
        val plan = strategy.plan(
            config {
                command = UserVisibleCommand.RUN
                enableDeepClean = true
            }
        )
        assertEquals(CommandPlan(InternalCommand.RUN, listOf(InternalCommand.DEEP_CLEAN)), plan)
    }

    @Test
    fun `cleanup only applies to build-like commands`() {
        for (userCommand in listOf(UserVisibleCommand.VALIDATE, UserVisibleCommand.CLEAN, UserVisibleCommand.CUSTOM)) {
            val plan = strategy.plan(
                config {
                    command = userCommand
                    enableForceClean = true
                    enableDeepClean = true
                }
            )
            assertEquals(CommandPlan(InternalCommand.valueOf(userCommand.name)), plan)
        }
    }

    @Test
    fun `force and deep clean both prepend in order`() {
        val plan = strategy.plan(
            config {
                command = UserVisibleCommand.EXPORT
                enableForceClean = true
                enableDeepClean = true
            }
        )
        assertEquals(
            CommandPlan(InternalCommand.EXPORT, listOf(InternalCommand.CLEAN, InternalCommand.DEEP_CLEAN)),
            plan
        )
    }

    @Test
    fun `clean command without flags stays a single command`() {
        val plan = strategy.plan(
            config { command = UserVisibleCommand.CLEAN }
        )
        assertEquals(CommandPlan(InternalCommand.CLEAN), plan)
    }

    @Test
    fun `all flattens pre-steps before the main command`() {
        val plan = CommandPlan(InternalCommand.BUILD, listOf(InternalCommand.CLEAN, InternalCommand.DEEP_CLEAN))
        assertEquals(listOf(InternalCommand.CLEAN, InternalCommand.DEEP_CLEAN, InternalCommand.BUILD), plan.all)
    }
}
