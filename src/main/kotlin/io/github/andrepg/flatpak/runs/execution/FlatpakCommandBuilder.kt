package io.github.andrepg.flatpak.runs.execution

import com.intellij.openapi.diagnostic.Logger
import io.github.andrepg.flatpak.runs.FlatpakCommand
import io.github.andrepg.flatpak.settings.DefaultFlatpakPaths
import io.github.andrepg.flatpak.utils.FlatpakManifestReader

/**
 * Builds the Flatpak command line and its arguments for a given [FlatpakCommand].
 */
class FlatpakCommandBuilder {
    private val logger = Logger.getInstance(FlatpakCommandBuilder::class.java)

    /**
     * The current build dir output used in build
     */
    var buildDir: String

    /**
     * The manifest path passed to Flatpak builder
     */
    var manifestPath: String

    private var flatpakBinaryPath = DefaultFlatpakPaths.MAIN_BINARY
    private var flatpakBuilderCommand = DefaultFlatpakPaths.BUILDER_BINARY

    /**
     * Creates a builder for the given build settings.
     *
     * @param buildTarget the build directory used by flatpak-builder
     * @param appManifest the path to the Flatpak manifest file
     */
    constructor(
        buildTarget: String,
        appManifest: String
    ) {
        buildDir = buildTarget
        manifestPath = appManifest
    }

    private fun basicCommand(): List<String> = listOf(flatpakBinaryPath, "run", flatpakBuilderCommand)

    private fun getAppId(): String {
        FlatpakManifestReader.readAppId(manifestPath)?.let { return it }

        logger.warn("Could not read app-id from manifest at $manifestPath; falling back to filename-derived app-id")

        return manifestPath.split('/').last().split('.').first()
    }

    /**
     * Get Flatpak command to run based on current spec, using Flatpak Builder,
     * using [DefaultFlatpakPaths] declared, with defaults to `flatpak run org.flatpak.Builder`;
     *
     * 1. [FlatpakCommand.CLEAN] translates to `rm -rf {buildDir}`
     * 2. [FlatpakCommand.BUILD] translates to `{baseCommand} --force-clean {buildDir} {manifestPath}`
     * 3. [FlatpakCommand.EXPORT] translates to: `{baseCommand} --repo=repo-build --force-clean {buildDir} {manifestPath}`
     * 4. [FlatpakCommand.RUN] translates to `{baseCommand} --force-clean --run {buildDir} {manifestPath}`
     * 5. [FlatpakCommand.VALIDATE] translates to ``
     *
     * @param command [FlatpakCommand] triggered by user
     */
    fun getCommand(command: FlatpakCommand, customArgs: List<String> = emptyList()): List<String> = when (command) {
        FlatpakCommand.CUSTOM -> basicCommand()
            .plus(listOf(buildDir, manifestPath))
            .plus(customArgs)

        FlatpakCommand.CLEAN -> listOf("rm", "-rf", buildDir)

        FlatpakCommand.BUILD -> basicCommand()
            .plus(listOf(buildDir, manifestPath))

        FlatpakCommand.RUN -> basicCommand()
            .plus(listOf("--run", buildDir, manifestPath, getAppId()))

        FlatpakCommand.EXPORT -> basicCommand()
            .plus(listOf("--repo=repo-build", "--force-clean", buildDir, manifestPath))

        FlatpakCommand.VALIDATE -> emptyList()
    }
}