package io.github.andrepg.flatpak.runs.execution

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import io.github.andrepg.shared.log.Log
import java.io.OutputStream

/**
 * A [ProcessHandler] that runs optional blocking pre-steps (e.g. the VFS deep
 * clean) on a pooled thread, then executes a chain of commands sequentially.
 * Each command runs as an [OSProcessHandler]; on success the next one starts.
 * Text and termination of the underlying handlers are relayed to the console.
 */
class CommandChainProcessHandler(
    private val commandLines: List<GeneralCommandLine>,
    private val engine: CommandExecutionEngine,
    private val preSteps: List<() -> Boolean> = emptyList(),
) : ProcessHandler() {
    private val log = Log.getInstance(CommandChainProcessHandler::class.java)

    @Volatile
    private var activeHandler: OSProcessHandler? = null

    @Volatile
    private var cancelled = false

    private var currentIndex = 0

    override fun startNotify() {
        super.startNotify()
        ApplicationManager.getApplication().executeOnPooledThread { runChain() }
    }

    /**
     * Runs the pre-steps (once, before the first command) and then starts the
     * current command, chaining the next one on success.
     */
    private fun runChain() {
        if (cancelled) {
            notifyProcessTerminated(-1)
            return
        }

        if (currentIndex == 0) {
            for (step in preSteps) {
                if (cancelled) {
                    notifyProcessTerminated(-1)
                    return
                }
                notifyTextAvailable("Running pre-step...\n", ProcessOutputTypes.SYSTEM)
                if (!step()) {
                    notifyTextAvailable("Pre-step failed; aborting.\n", ProcessOutputTypes.SYSTEM)
                    notifyProcessTerminated(1)
                    return
                }
            }
        }

        if (currentIndex >= commandLines.size) {
            notifyProcessTerminated(0)
            return
        }

        val handler = engine.executeCommand(commandLines[currentIndex])
        activeHandler = handler

        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                notifyTextAvailable(event.text, outputType)
            }

            override fun processTerminated(event: ProcessEvent) {
                activeHandler = null
                log.info("Command execution terminated with exit code ${event.exitCode}")

                if (cancelled) {
                    notifyProcessTerminated(-1)
                    return
                }

                if (event.exitCode == 0 && currentIndex + 1 < commandLines.size) {
                    currentIndex++
                    log.info("Chaining next command")
                    runChain()
                } else {
                    notifyProcessTerminated(event.exitCode)
                }
            }
        })

        handler.startNotify()
    }

    override fun destroyProcessImpl() {
        cancelled = true
        activeHandler?.destroyProcess()
        activeHandler = null
        notifyProcessTerminated(-1)
    }

    override fun detachProcessImpl() {
        cancelled = true
        activeHandler?.detachProcess()
        activeHandler = null
        notifyProcessTerminated(-1)
    }

    override fun detachIsDefault(): Boolean = false

    override fun getProcessInput(): OutputStream? = null
}
