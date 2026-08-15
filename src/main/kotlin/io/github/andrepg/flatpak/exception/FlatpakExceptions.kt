package io.github.andrepg.flatpak.exception

/**
 * Base type for every plugin-owned exception.
 *
 * Pure JDK (no IntelliJ imports) so it can be raised from the JDK-only cores and
 * caught uniformly by IDE glue.
 */
open class FlatpakPluginException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * A Flatpak manifest could not be read or parsed.
 *
 * The strict parser (FlatpakManifestReader.parseFields) throws this on malformed
 * content; the forgiving IO helpers catch it and degrade to an empty field map so
 * detection never crashes on an unreadable file.
 */
class FlatpakManifestException(message: String, cause: Throwable? = null) : FlatpakPluginException(message, cause)

/** Building or launching a flatpak-builder/flatpak command line failed. */
class FlatpakExecutionException(message: String, cause: Throwable? = null) : FlatpakPluginException(message, cause)

/** The run configuration's state is inconsistent with what a command needs. */
class FlatpakConfigurationException(message: String, cause: Throwable? = null) : FlatpakPluginException(message, cause)
