package io.github.andrepg.flatpak.runs.commands

import io.github.andrepg.flatpak.runs.FlatpakDefaults
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.flatpak.settings.FlatpakSettings

/**
 * Flatpak CLI command-line fragments shared by every command the plugin
 * renders. The base command is the bundled Flatpak Builder inside a sandbox
 * (`flatpak run org.flatpak.Builder`); each command appends its own
 * subcommand, flags and positionals on top of [flatpakBuilderCommand].
 */
object FlatpakCommandLine {
    /**
     * The base command, `flatpak run org.flatpak.Builder`, which runs the
     * Flatpak Builder bundle inside the sandbox.
     */
    fun flatpakBuilderCommand(): List<String> =
        listOf(
            FlatpakSettings.flatpakBinary,
            "run",
            FlatpakSettings.builderBinary,
        )
}

/**
 * Build dir guaranteed non-blank (defaults to `_build`), so the positional
 * `DIRECTORY` argument is never empty on the command line.
 */
fun FlatpakRunSettings.effectiveBuildDir(): String = buildDir.ifBlank { FlatpakDefaults.BUILD_DIR.value }

/**
 * Manifest path guaranteed non-blank (defaults to `flatpak.json`), so the
 * positional `MANIFEST` argument is never empty on the command line.
 */
fun FlatpakRunSettings.effectiveManifestPath(): String = manifestPath.ifBlank { FlatpakDefaults.MANIFEST_FILE.value }

/**
 * Sandbox options injected into the Run command so the app sees the requested
 * GNOME/portal integration. flatpak-builder's `--run` mode accepts the flatpak
 * context options (`--socket`, `--talk-name`, `--filesystem`, `--device`, `--env`),
 * which must be placed before the `DIRECTORY MANIFEST COMMAND` positional args.
 */
fun buildSandboxOptions(config: FlatpakRunSettings): List<String> =
    buildList {
        if (config.enablePortals) addAll(CommandExecutionArguments.ENABLE_PORTALS)
        if (config.enableThemes) addAll(CommandExecutionArguments.ENABLE_THEMES)
        if (config.enableAudio) addAll(CommandExecutionArguments.ENABLE_AUDIO)
        if (config.enableWayland) addAll(CommandExecutionArguments.ENABLE_WAYLAND)
    }
