package io.github.andrepg.flatpak.detection

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import io.github.andrepg.shared.log.Log

/**
 * Caches [FlatpakProjectDetector.findManifests] results per project.
 *
 * The walk over the content-root tree is O(files) and was repeated on every
 * schema request / editor notification. The result is invalidated by VFS
 * events under a content root (file added/removed/changed) and by content-root
 * changes, so it never goes stale while staying event-driven.
 */
@Service(Service.Level.PROJECT)
class FlatpakManifestCacheService(private val project: Project) : Disposable {
    private val log = Log.getInstance(FlatpakManifestCacheService::class.java)

    @Volatile
    private var cached: List<Pair<VirtualFile, String>>? = null

    private val connection = project.messageBus.connect(this)

    init {
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val roots = ProjectRootManager.getInstance(project).contentRoots
                if (roots.isEmpty()) return
                val anyUnderRoot = events.any { event ->
                    event.file?.let { file ->
                        roots.any { root -> VfsUtilCore.isAncestor(root, file, false) }
                    } ?: false
                }
                if (anyUnderRoot) invalidate()
            }
        })

        connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
            override fun rootsChanged(event: com.intellij.openapi.roots.ModuleRootEvent) {
                invalidate()
            }
        })
    }

    /** Returns the cached manifest list, walking the project on first call. */
    fun findManifests(): List<Pair<VirtualFile, String>> {
        cached?.let { return it }
        val result = walkContentRoots()
        cached = result
        log.info("Cached ${result.size} Flatpak manifest(s) for ${project.name}")
        return result
    }

    private fun invalidate() {
        if (cached != null) {
            log.info("Invalidating Flatpak manifest cache for ${project.name}")
        }
        cached = null
    }

    private fun walkContentRoots(): List<Pair<VirtualFile, String>> =
        FlatpakProjectDetector.findManifestsUncached(project)

    override fun dispose() = Unit
}
