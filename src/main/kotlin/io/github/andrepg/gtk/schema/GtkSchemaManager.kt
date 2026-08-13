package io.github.andrepg.gtk.schema

import io.github.andrepg.gtk.schema.gir.GirSchemaExtractor
import io.github.andrepg.gtk.schema.locator.GirSdkLocator
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves the GtkBuilder XSD for the SDK declared by a project's manifest,
 * preferring a locally generated schema over the bundled one.
 *
 * Resolution order:
 *  1. cached generated XSD in the config dir (`gtk-ui-<key>.xsd`)
 *  2. locate the SDK GIR dir, generate and cache the XSD (idempotent)
 *  3. return null so the caller falls back to the bundled classpath schema
 *
 * Generation is non-fatal: any discovery/parsing failure results in null and
 * the bundled schema keeps serving. JDK-only, no IntelliJ imports.
 *
 * @property configDir cache directory (e.g. the plugin config dir)
 * @property baseDirs Flatpak install roots used by [GirSdkLocator] (injectable for tests)
 */
class GtkSchemaManager(
    private val configDir: File,
    private val baseDirs: List<File> = GirSdkLocator.defaultBaseDirs(),
) {

    private val requested = ConcurrentHashMap<String, Boolean>()

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
     * is null or the SDK cannot be found/generated (caller keeps the bundled schema).
     *
     * @param hint the desired GNOME SDK
     * @param flatpakBinary path of the flatpak CLI binary used for discovery
     */
    fun generateSchema(hint: SdkHint?, flatpakBinary: String): File? {
        if (hint == null) return null
        cachedSchema(hint)?.let { return it }
        val girDir = GirSdkLocator.locate(hint.sdkAppId, hint.branch, flatpakBinary, baseDirs) ?: return null
        val xsd = try {
            GirSchemaExtractor.generateXsd(girDir)
        } catch (e: Exception) {
            return null
        }
        configDir.mkdirs()
        val target = File(configDir, "gtk-ui-${hint.key}.xsd")
        target.writeText(xsd)
        return target
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
}
