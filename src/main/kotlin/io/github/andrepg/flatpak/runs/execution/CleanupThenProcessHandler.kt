package io.github.andrepg.flatpak.runs.execution

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import io.github.andrepg.shared.log.Log
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * A [ProcessHandler] that runs the cleanup pre-steps as raw OS processes on a
 * pooled thread — never on the EDT — and then starts the main command as an
 * [OSProcessHandler], relaying its output and termination to this handler.
 *
 * This avoids the "Synchronous execution on EDT" error thrown by
 * [OSProcessHandler#checkEdtAndReadAction] when cleanup steps were executed
 * with a blocking `runProcess()` from [com.intellij.execution.process.CommandLineState#startProcess].
 */
class CleanupThenProcessHandler(
    private val cleanupCommandLines: List<List<String>>,
    private val mainCommandLine: GeneralCommandLine,
    private val workDir: File?,
) : ProcessHandler() {

    private val log = Log.getInstance(CleanupThenProcessHandler::class.java)

    @Volatile
    private var cancelled = false

    @Volatile
    private var activeHandler: OSProcessHandler? = null

    @Volatile
    private var activeProcess: Process? = null

    override fun startNotify() {
        super.startNotify()
        ApplicationManager.getApplication().executeOnPooledThread { runChain() }
    }

    private fun runChain() {
        try {
            for (commandLine in cleanupCommandLines) {
                if (cancelled) return
                if (!runCleanup(commandLine)) {
                    if (!cancelled) notifyProcessTerminated(1)
                    return
                }
            }
            if (cancelled) return
            startMainCommand()
        } catch (e: Exception) {
            log.warn("Cleanup chain failed", e)
            if (!cancelled) {
                notifyTextAvailable("Flatpak command chain failed: ${e.message}\n", ProcessOutputTypes.STDERR)
                notifyProcessTerminated(1)
            }
        }
    }

    private fun runCleanup(commandLine: List<String>): Boolean {
        val started = System.nanoTime()
        log.info("Executing cleanup: ${commandLine.joinToString(" ")}")
        val process = try {
            ProcessBuilder(commandLine).directory(workDir).start()
        } catch (e: IOException) {
            log.warn("Cleanup process failed to start", e)
            notifyTextAvailable("Cleanup failed to start: ${e.message}\n", ProcessOutputTypes.STDERR)
            return false
        }

        activeProcess = process

        val stdoutThread = streamForwarder(process.inputStream, ProcessOutputTypes.STDOUT)
        val stderrThread = streamForwarder(process.errorStream, ProcessOutputTypes.STDERR)

        val completed = process.waitFor(2, TimeUnit.MINUTES)
        if (!completed) {
            process.destroyForcibly()
            log.warn("Cleanup timed out after 2 minutes: ${commandLine.joinToString(" ")}")
            notifyTextAvailable("Cleanup timed out: ${commandLine.joinToString(" ")}\n", ProcessOutputTypes.STDERR)
            return false
        }

        stdoutThread.join(5_000)
        stderrThread.join(5_000)
        activeProcess = null

        val exitCode = process.exitValue()
        val durationMs = (System.nanoTime() - started) / 1_000_000
        if (exitCode == 0) {
            log.info("Cleanup finished in ${durationMs}ms with exit code 0: ${commandLine.joinToString(" ")}")
        } else {
            log.warn("Cleanup failed with exit code $exitCode after ${durationMs}ms: ${commandLine.joinToString(" ")}")
        }
        return exitCode == 0
    }

    private fun streamForwarder(input: InputStream, outputType: Key<*>): Thread =
        Thread {
            input.bufferedReader().forEachLine { line ->
                if (!cancelled) notifyTextAvailable("$line\n", outputType)
            }
        }.apply {
            isDaemon = true
            start()
        }

    private fun startMainCommand() {
        val handler = try {
            OSProcessHandler(mainCommandLine)
        } catch (e: ExecutionException) {
            log.warn("Failed to start the main command", e)
            notifyTextAvailable("Failed to start: ${e.message}\n", ProcessOutputTypes.STDERR)
            notifyProcessTerminated(1)
            return
        }

        activeHandler = handler
        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                notifyTextAvailable(event.text, outputType)
            }

            override fun processTerminated(event: ProcessEvent) {
                log.info("Main flatpak command terminated with exit code ${event.exitCode}")
                notifyProcessTerminated(event.exitCode)
            }
        })
        handler.startNotify()
    }

    override fun destroyProcessImpl() {
        cancelled = true
        activeProcess?.destroy()
        activeHandler?.destroyProcess()
    }

    override fun detachProcessImpl() {
        cancelled = true
        activeProcess?.destroy()
        activeHandler?.detachProcess()
    }

    override fun detachIsDefault(): Boolean = false

    override fun getProcessInput(): OutputStream? = null
}
