package io.github.andrepg.flatpak.runs.execution

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CleanupThenProcessHandlerTest : BasePlatformTestCase() {

    private fun runToTermination(
        cleanupCommandLines: List<List<String>> = emptyList(),
        mainCommandLine: GeneralCommandLine = GeneralCommandLine(listOf("sh", "-c", "exit 0")),
    ): Int {
        val handler = CleanupThenProcessHandler(cleanupCommandLines, mainCommandLine, null)
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

    fun `test cleanup failure terminates the run with exit code 1`() {
        val exitCode = runToTermination(
            cleanupCommandLines = listOf(listOf("sh", "-c", "exit 3")),
        )
        assertEquals(1, exitCode)
    }

    fun `test successful cleanup then main command yields main exit code`() {
        val marker = File(System.getProperty("user.home"), "cleanup-handler-${System.nanoTime()}.marker")
        try {
            val exitCode = runToTermination(
                cleanupCommandLines = listOf(listOf("sh", "-c", "exit 0")),
                mainCommandLine = GeneralCommandLine(listOf("sh", "-c", "touch '$marker.path'")),
            )
            assertEquals(0, exitCode)
            assertTrue("main command must have run after cleanup", marker.isFile)
        } finally {
            marker.delete()
        }
    }

    fun `test main command failure propagates its exit code`() {
        val exitCode = runToTermination(
            cleanupCommandLines = listOf(listOf("sh", "-c", "exit 0")),
            mainCommandLine = GeneralCommandLine(listOf("sh", "-c", "exit 7")),
        )
        assertEquals(7, exitCode)
    }

    fun `test destroy during cleanup cancels the chain and never runs the main command`() {
        val marker = File(System.getProperty("user.home"), "cleanup-handler-cancelled-${System.nanoTime()}.marker")
        try {
            val handler = CleanupThenProcessHandler(
                cleanupCommandLines = listOf(listOf("sh", "-c", "sleep 30")),
                mainCommandLine = GeneralCommandLine(listOf("sh", "-c", "touch '$marker.path'")),
                workDir = null,
            )
            val terminated = CountDownLatch(1)
            handler.addProcessListener(object : ProcessListener {
                override fun processTerminated(event: ProcessEvent) {
                    terminated.countDown()
                }
            })
            handler.startNotify()
            Thread.sleep(500)
            handler.destroyProcess()

            assertTrue(
                "handler must terminate after destroy",
                terminated.await(10, TimeUnit.SECONDS),
            )
            assertTrue("main command must never run after a cancelled cleanup", !marker.isFile)
        } finally {
            marker.delete()
        }
    }
}
