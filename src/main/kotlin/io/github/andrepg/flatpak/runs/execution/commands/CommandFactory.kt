package io.github.andrepg.flatpak.runs.execution.commands

import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.flatpak.runs.execution.CommandExecutionArguments
import io.github.andrepg.flatpak.settings.FlatpakSettings

abstract class CommandFactory {
    /**
     * Builds the main Flatpak Builder command, to invoke the
     * Flatpak Builder bundle inside the sandbox. Usually this
     * command/function translates to `/usr/bin/flatpak run org.flatpak.Builder run`
     */
    protected fun getFlatpakCommand(): List<String> = listOf(
        FlatpakSettings.flatpakBinary,
        "run",
        FlatpakSettings.builderBinary
    )

    /**
     * Invokes the Command builder creation and returns the
     * actual Flatpak command line to run inside the IDE
     *
     * Each class should implement its own based on the
     * purpose and job to do. One command per builder.
     */
    abstract fun create(settings: FlatpakRunSettings): List<String>

    /**
     * Build dir guaranteed non-blank (defaults to `_build`), so the positional
     * `DIRECTORY` argument is never empty on the command line.
     */
    protected fun FlatpakRunSettings.effectiveBuildDir(): String = buildDir.ifBlank { "_build" }

    /**
     * Manifest path guaranteed non-blank (defaults to `flatpak.json`), so the
     * positional `MANIFEST` argument is never empty on the command line.
     */
    protected fun FlatpakRunSettings.effectiveManifestPath(): String = manifestPath.ifBlank { "flatpak.json" }

    /**
     * Sandbox options injected into the Run command so the app sees the requested
     * GNOME/portal integration. flatpak-builder's `--run` mode accepts the flatpak
     * context options (`--socket`, `--talk-name`, `--filesystem`, `--device`, `--env`),
     * which must be placed before the `DIRECTORY MANIFEST COMMAND` positional args.
     */
    protected fun buildSandboxOptions(config: FlatpakRunSettings): List<String> = buildList {
        if (config.enablePortals) addAll(CommandExecutionArguments.ENABLE_PORTALS)
        if (config.enableThemes) addAll(CommandExecutionArguments.ENABLE_THEMES)
        if (config.enableAudio) addAll(CommandExecutionArguments.ENABLE_AUDIO)
        if (config.enableWayland) addAll(CommandExecutionArguments.ENABLE_WAYLAND)
    }
}