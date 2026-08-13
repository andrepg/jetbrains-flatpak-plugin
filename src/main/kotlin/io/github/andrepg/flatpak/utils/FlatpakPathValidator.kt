package io.github.andrepg.flatpak.utils

import java.io.File

/**
 * Validates and resolves the paths of the Flatpak executables.
 */
object FlatpakPathValidator {
    
    /**
     * Checks whether the given path points to an existing, executable binary.
     *
     * @param path the path to check
     * @return true if the path exists and the file is executable
     */
    fun validateBinaryPath(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.canExecute()
    }
    
    /**
     * Returns the given path if it is a valid binary, otherwise the default path.
     *
     * @param path the configured binary path
     * @param defaultPath the fallback path used when the configured one is invalid
     * @return the valid path or the default fallback
     */
    fun getValidatedBinaryPath(path: String, defaultPath: String): String {
        return if (validateBinaryPath(path)) {
            path
        } else {
            defaultPath
        }
    }
    
    /**
     * Returns the given path if it is a valid binary; otherwise searches for the binary on the
     * system PATH, falling back to the default path when it cannot be located.
     *
     * @param path the configured binary path
     * @param defaultPath the fallback path used when the binary cannot be found
     * @return the valid path, a binary found on PATH, or the default fallback
     */
    fun getBinaryPathWithFallback(path: String, defaultPath: String): String {
        return if (validateBinaryPath(path)) {
            path
        } else {
            // Try to find the binary in PATH
            val pathEnv = System.getenv("PATH")
            val pathDirs = pathEnv.split(File.pathSeparator)
            
            for (dir in pathDirs) {
                val binaryFile = File(dir, path.substringAfterLast(File.separator))
                if (binaryFile.exists() && binaryFile.canExecute()) {
                    return binaryFile.absolutePath
                }
            }
            
            defaultPath
        }
    }
}