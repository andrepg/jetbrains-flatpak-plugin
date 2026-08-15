package io.github.andrepg.flatpak.runs.execution

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CommandChainProcessHandlerTest : BasePlatformTestCase() {

    private fun runToTermination(
        commandLines: List<GeneralCommandLine>,
        commandLabels: List<String> = commandLines.mapIndexed { i, _ -> "COMMAND_$i" },
        preSteps: List<CommandChainProcessHandler.PreStep> = emptyList(),
    ): Int {
        val handler = CommandChainProcessHandler(
            commandLines = commandLines,
            commandLabels = commandLabels,
            engine = CommandExecutionEngine(project),
            preSteps = preSteps
        )
        val terminated = CountDownLatch(1)
        var exitCode = Int.MIN_VALUE
        handler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                exitCode = event.exitCode
                terminated.countDown()
            }
        })
        handler.startNotify()
        assertTrue(
            "handler must terminate within 30s",
            terminated.await(30, TimeUnit.SECONDS),
        )
        return exitCode
    }

    fun `test pre-step failure terminates the run with exit code 1`() {
        val exitCode = runToTermination(
            commandLines = listOf(GeneralCommandLine(listOf("sh", "-c", "exit 0"))),
            preSteps = listOf(CommandChainProcessHandler.PreStep("DEEP_CLEAN") { false }),
        )
        assertEquals(1, exitCode)
    }

    fun `test successful pre-step then main command yields main exit code`() {
        val marker = File(System.getProperty("user.home"), "chain-handler-${System.nanoTime()}.marker")
        try {
            var preStepRan = false
            val exitCode = runToTermination(
                commandLines = listOf(GeneralCommandLine(listOf("sh", "-c", "touch '${marker.path}'"))),
                preSteps = listOf(
                    CommandChainProcessHandler.PreStep("DEEP_CLEAN") {
                        preStepRan = true
                        true
                    }
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
        val exitCode = runToTermination(
            commandLines = listOf(GeneralCommandLine(listOf("sh", "-c", "exit 7"))),
        )
        assertEquals(7, exitCode)
    }

    fun `test destroy during pre-step cancels the chain and never runs the main command`() {
        val marker = File(System.getProperty("user.home"), "chain-handler-cancelled-${System.nanoTime()}.marker")
        try {
            val handler = CommandChainProcessHandler(
                commandLines = listOf(GeneralCommandLine(listOf("sh", "-c", "touch '${marker.path}'"))),
                commandLabels = listOf("BUILD"),
                engine = CommandExecutionEngine(project),
                preSteps = listOf(CommandChainProcessHandler.PreStep("DEEP_CLEAN") { Thread.sleep(500); true }),
            )
            val terminated = CountDownLatch(1)
            handler.addProcessListener(object : ProcessListener {
                override fun processTerminated(event: ProcessEvent) {
                    terminated.countDown()
                }
            })
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
}
