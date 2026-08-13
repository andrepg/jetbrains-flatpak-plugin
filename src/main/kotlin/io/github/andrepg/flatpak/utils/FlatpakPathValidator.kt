package io.github.andrepg.flatpak.utils

import io.github.andrepg.flatpak.settings.FlatpakPaths
import java.io.File

object FlatpakPathValidator {
    
    fun validateBinaryPath(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.canExecute()
    }
    
    fun getValidatedBinaryPath(path: String, defaultPath: String): String {
        return if (validateBinaryPath(path)) {
            path
        } else {
            defaultPath
        }
    }
    
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