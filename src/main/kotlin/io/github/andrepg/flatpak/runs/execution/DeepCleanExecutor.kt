package io.github.andrepg.flatpak.runs.execution

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.shared.log.Log
import java.io.File
import java.io.IOException

/**
 * Deletes the deep-clean targets (the build directory and the flatpak-builder
 * cache) through the IntelliJ VFS instead of spawning a `rm` process.
 *
 * Must be invoked off the EDT: refreshing and deleting large trees can block.
 * The caller ([CommandChainProcessHandler]) runs this on a pooled thread.
 */
class DeepCleanExecutor {
    private val log = Log.getInstance(DeepCleanExecutor::class.java)

    /**
     * Deletes every deep-clean target. Missing targets are skipped silently.
     *
     * @return true when all existing targets were deleted, false on any failure
     */
    fun clean(project: Project, settings: FlatpakRunSettings): Boolean =
        deepCleanTargets(project, settings).fold(true) { success, target ->
            delete(target) && success
        }

    /** The absolute paths cleaned by a deep clean, in deletion order. */
    fun deepCleanTargets(project: Project, settings: FlatpakRunSettings): List<File> {
        val buildDir = File(settings.buildDir)
        val resolvedBuildDir = if (buildDir.isAbsolute) buildDir else File(project.basePath, settings.buildDir)
        return listOf(resolvedBuildDir, flatpakBuilderCache())
    }

    private fun flatpakBuilderCache(): File {
        val home = System.getProperty("user.home").orEmpty()
        val normalized = if (home.endsWith("/") || home.isEmpty()) home else "$home/"
        return File("${normalized}.cache/flatpak-builder/")
    }

    private fun delete(target: File): Boolean {
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target) ?: return true
        return try {
            ApplicationManager.getApplication().runWriteAction {
                if (virtualFile.exists()) {
                    virtualFile.delete(this)
                }
            }
            log.info("Deep clean deleted: ${target.path}")
            true
        } catch (e: IOException) {
            log.warn("Deep clean failed to delete: ${target.path}", e)
            false
        }
    }
}
