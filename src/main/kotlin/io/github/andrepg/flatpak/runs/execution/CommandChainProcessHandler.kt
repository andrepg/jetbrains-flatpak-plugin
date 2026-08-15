package io.github.andrepg.flatpak.runs.execution

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import io.github.andrepg.shared.log.Log

/**
 * A recursive [ProcessHandler] that executes a chain of commands sequentially.
 * Each command runs as an [OSProcessHandler], and on success, recursively starts the next.
 */
class CommandChainProcessHandler(
    private val commandLines: List<GeneralCommandLine>,
    private val workDir: VirtualFile?,
    private val engine: CommandExecutionEngine,
    private val index: Int = 0
) : ProcessHandler() {

    private val log = Log.getInstance(CommandChainProcessHandler::class.java)

    @Volatile
    private var activeHandler: OSProcessHandler? = null

    @Volatile
    private var cancelled = false

    override fun startNotify() {
        if (index >= commandLines.size) {
            notifyProcessTerminated(0)
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            executeCurrentCommand()
        }
    }

    private fun executeCurrentCommand() {
        if (cancelled) {
            notifyProcessTerminated(-1)
            return
        }

        val currentCommand = commandLines[index]
        val handler = engine.executeCommand(currentCommand)
        activeHandler = handler

        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                notifyTextAvailable(event.text, outputType)
            }

            override fun processTerminated(event: ProcessEvent) {
                activeHandler = null
                
                if (cancelled) {
                    notifyProcessTerminated(-1)
                    return
                }

                activeHandler?.destroyProcess()

                if (event.exitCode == 0 && index + 1 < commandLines.size) {
                    // Recursively chain to next command
                    CommandChainProcessHandler(
                        commandLines,
                        workDir,
                        engine,
                        index + 1
                    ).startNotify()
                }
                notifyProcessTerminated(event.exitCode)
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

    override fun getProcessInput(): java.io.OutputStream? = null
}