package io.github.andrepg.flatpak.runs.steps

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.util.Key
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.andrepg.flatpak.runs.InternalCommand
import io.github.andrepg.flatpak.runs.commands.CommandExecutionEngine
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CommandChainProcessHandlerTest : BasePlatformTestCase() {
    private fun runToTermination(
        commandLines: List<GeneralCommandLine>,
        commandSteps: List<InternalCommand> = commandLines.map { InternalCommand.CUSTOM },
        preSteps: List<CommandChainProcessHandler.PreStep> = emptyList(),
    ): Int {
        val handler =
            CommandChainProcessHandler(
                commandLines = commandLines,
                commandSteps = commandSteps,
                engine = CommandExecutionEngine(project),
                preSteps = preSteps,
            )
        val terminated = CountDownLatch(1)
        var exitCode = Int.MIN_VALUE
        handler.addProcessListener(
            object : ProcessListener {
                override fun processTerminated(event: ProcessEvent) {
                    exitCode = event.exitCode
                    terminated.countDown()
                }
            },
        )
        handler.startNotify()
        assertTrue(
            "handler must terminate within 30s",
            terminated.await(30, TimeUnit.SECONDS),
        )
        return exitCode
    }

    fun `test pre-step failure terminates the run with exit code 1`() {
        val exitCode =
            runToTermination(
                commandLines = listOf(GeneralCommandLine(listOf("sh", "-c", "exit 0"))),
                preSteps = listOf(CommandChainProcessHandler.PreStep(PreStepType.DEEP_CLEAN) { _ -> false }),
            )
        assertEquals(1, exitCode)
    }

    fun `test successful pre-step then main command yields main exit code`() {
        val marker = File(System.getProperty("user.home"), "chain-handler-${System.nanoTime()}.marker")
        try {
            var preStepRan = false
            val exitCode =
                runToTermination(
                    commandLines = listOf(GeneralCommandLine(listOf("sh", "-c", "touch '${marker.path}'"))),
                    preSteps =
                        listOf(
                            CommandChainProcessHandler.PreStep(PreStepType.DEEP_CLEAN) { _ ->
                                preStepRan = true
                                true
                            },
                        ),
                )
            assertEquals(0, exitCode)
            assertTrue("pre-step must have run", preStepRan)
            assertTrue("main command must have run after pre-step", marker.isFile)
        } finally {
            marker.delete()
        }
    }

    fun `test main command failure propagates its exit code`() {
        val exitCode =
            runToTermination(
                commandLines = listOf(GeneralCommandLine(listOf("sh", "-c", "exit 7"))),
            )
        assertEquals(7, exitCode)
    }

    fun `test destroy during pre-step cancels the chain and never runs the main command`() {
        val marker = File(System.getProperty("user.home"), "chain-handler-cancelled-${System.nanoTime()}.marker")
        try {
            val handler =
                CommandChainProcessHandler(
                    commandLines = listOf(GeneralCommandLine(listOf("sh", "-c", "touch '${marker.path}'"))),
                    commandSteps = listOf(InternalCommand.BUILD),
                    engine = CommandExecutionEngine(project),
                    preSteps =
                        listOf(
                            CommandChainProcessHandler.PreStep(PreStepType.DEEP_CLEAN) { _ ->
                                Thread.sleep(500)
                                true
                            },
                        ),
                )
            val terminated = CountDownLatch(1)
            handler.addProcessListener(
                object : ProcessListener {
                    override fun processTerminated(event: ProcessEvent) {
                        terminated.countDown()
                    }
                },
            )
            handler.startNotify()
            Thread.sleep(200)
            handler.destroyProcess()

            assertTrue(
                "handler must terminate after destroy",
                terminated.await(10, TimeUnit.SECONDS),
            )
            assertTrue("main command must never run after a cancelled pre-step", !marker.isFile)
        } finally {
            marker.delete()
        }
    }

    fun `test quiet pre-step reports without announcement`() {
        val handler =
            CommandChainProcessHandler(
                commandLines = listOf(GeneralCommandLine(listOf("sh", "-c", "exit 0"))),
                commandSteps = listOf(InternalCommand.BUILD),
                engine = CommandExecutionEngine(project),
                preSteps =
                    listOf(
                        CommandChainProcessHandler.PreStep(PreStepType.UNMOUNT_STALE, quiet = true) { report ->
                            report("${PreStepType.UNMOUNT_STALE}: removed stale FUSE mount at /tmp/rofiles-x\n")
                            true
                        },
                    ),
            )
        val terminated = CountDownLatch(1)
        val texts = StringBuilder()
        handler.addProcessListener(
            object : ProcessListener {
                override fun onTextAvailable(
                    event: ProcessEvent,
                    outputType: Key<*>,
                ) {
                    texts.append(event.text)
                }

                override fun processTerminated(event: ProcessEvent) {
                    terminated.countDown()
                }
            },
        )
        handler.startNotify()
        assertTrue("handler must terminate within 30s", terminated.await(30, TimeUnit.SECONDS))
        assertTrue("quiet step report must reach the console", texts.contains("removed stale FUSE mount"))
        assertFalse("quiet step must not be announced", texts.contains("Running UNMOUNT_STALE"))
    }
}
