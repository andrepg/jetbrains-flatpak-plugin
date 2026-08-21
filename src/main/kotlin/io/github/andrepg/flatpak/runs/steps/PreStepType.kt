package io.github.andrepg.flatpak.runs.steps

/**
 * Workflow steps that run as blocking pre-steps before the main command of a
 * [CommandChainProcessHandler] chain.
 *
 * @property UNMOUNT_STALE Unmounts stale FUSE mounts left inside the build directory
 * @property DEEP_CLEAN Wipes the VFS cache and build directory before a BUILD
 */
enum class PreStepType {
    UNMOUNT_STALE,
    DEEP_CLEAN,
}
