package io.github.andrepg.gtk.schema

import io.github.andrepg.gtk.schema.gir.GirSchemaExtractor
import io.github.andrepg.gtk.schema.gir.GtkSchemaProgress
import io.github.andrepg.gtk.schema.gir.GtkSchemaStep
import io.github.andrepg.gtk.schema.locator.GirSdkLocator
import io.github.andrepg.shared.log.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Log message templates used by [GtkSchemaManager]. */
private object Messages {
    const val CACHED_SCHEMA = "Using cached generated GTK schema: %s"
    const val GIR_DIR_NOT_FOUND = "Could not locate GIR dir for %s; no schema will be served"
    const val GIR_DIR_LOCATED = "Located GIR dir for %s: %s"
    const val GENERATION_FAILED = "Failed to generate GTK schema from %s; no schema will be served"
    const val GENERATED_CACHED = "Generated and cached GTK schema: %s"
}

/**
 * Resolves the GtkBuilder XSD for the SDK declared by a project's manifest,
 * generating it from the user's installed GNOME SDK.
 *
 * Resolution order:
 *  1. cached generated XSD in the config dir (`gtk-ui-<key>.xsd`)
 *  2. locate the SDK GIR dir, generate and cache the XSD (idempotent)
 *  3. return null so the caller serves no schema
 *
 * Generation is non-fatal: any discovery/parsing failure results in null and
 * no schema is served until a later attempt succeeds. JDK-only, no IntelliJ
 * imports.
 *
 * @property configDir cache directory (e.g. the plugin config dir)
 */
class GtkSchemaManager(
    private val configDir: File,
) {
    private val requested = ConcurrentHashMap<String, Boolean>()
    private val log = Log.getInstance(GtkSchemaManager::class.java)

    /**
     * @return the cached generated XSD for [hint]'s key, or null when absent.
     */
    fun cachedSchema(hint: SdkHint?): File? {
        val key = hint?.key ?: return null
        return File(configDir, "gtk-ui-$key.xsd").takeIf { it.isFile }
    }

    /**
     * Locates the SDK GIR dir and generates the XSD into the cache. Idempotent:
     * returns the cached file when already generated; returns null when [hint]
     * is null or the SDK cannot be found/generated (caller serves no schema).
     *
     * @param hint the desired GNOME SDK
     * @param flatpakBinary path of the flatpak CLI binary used for discovery
     * @param onProgress optional progress reporter; returning `false` aborts
     *   the attempt (no schema is served)
     */
    fun generateSchema(
        hint: SdkHint?,
        flatpakBinary: String,
        onProgress: GtkSchemaProgress? = null,
    ): File? {
        if (hint == null) return null

        cachedSchema(hint)?.let { cached ->
            log.info(Messages.CACHED_SCHEMA.format(cached.absolutePath))
            return cached
        }

        if (onProgress?.report(GtkSchemaStep.Locating) == false) return null

        val girDir = locateGirDir(hint, flatpakBinary) ?: return null

        val xsd = extractXsd(girDir, onProgress) ?: return null

        if (onProgress?.report(GtkSchemaStep.Caching) == false) return null

        return cacheXsd(hint, xsd)
    }

    /**
     * Marks a generation attempt as requested for [hint]'s key.
     *
     * @return true the first time for this key, false on subsequent calls (so
     * the caller only schedules a single background generation).
     */
    fun markRequested(hint: SdkHint?): Boolean {
        val key = hint?.key ?: return false
        return requested.putIfAbsent(key, true) == null
    }

    /**
     * Locates the GIR dir for [hint], logging success or failure.
     */
    private fun locateGirDir(hint: SdkHint, flatpakBinary: String): File? {
        val girDir = GirSdkLocator.locate(hint.sdkAppId, hint.branch, flatpakBinary)
        if (girDir == null) {
            log.warn(Messages.GIR_DIR_NOT_FOUND.format(describe(hint)))
        } else {
            log.info(Messages.GIR_DIR_LOCATED.format(describe(hint), girDir.absolutePath))
        }
        return girDir
    }

    /**
     * Runs the GIR → XSD extraction, treating any failure as non-fatal.
     */
    private fun extractXsd(
        girDir: File,
        onProgress: GtkSchemaProgress?,
    ): String? =
        try {
            GirSchemaExtractor.generateXsd(girDir, onProgress)
        } catch (e: Exception) {
            log.warn(Messages.GENERATION_FAILED.format(girDir.absolutePath), e)
            null
        }

    /**
     * Writes [xsd] into the cache dir under [hint]'s key.
     */
    private fun cacheXsd(hint: SdkHint, xsd: String): File {
        configDir.mkdirs()
        val target = File(configDir, "gtk-ui-${hint.key}.xsd")
        target.writeText(xsd)

        log.info(Messages.GENERATED_CACHED.format(target.absolutePath))
        return target
    }

    private fun describe(hint: SdkHint): String = "${hint.sdkAppId}@${hint.branch}"
}
