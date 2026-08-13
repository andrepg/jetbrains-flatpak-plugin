package io.github.andrepg.flatpak.runs

import io.github.andrepg.flatpak.enums.FlatpakCommand
import io.github.andrepg.flatpak.settings.FlatpakPaths

/**
 * Builds up the Flatpak's command and arguments
 * state machine to map what we will call
 */
class FlatpakRunStateMachine {
    /**
     * The current build dir output used in build
     */
    var buildDir: String

    /**
     * The manifeest path passed to Flatpak builder
     */
    var manifestPath: String

    private var flatpakBinaryPath = FlatpakPaths.MAIN_BINARY
    private var flatpakBuilderCommand = FlatpakPaths.BUILDER_BINARY

    constructor(
        buildTarget: String,
        appManifest: String
    ) {
        buildDir = buildTarget
        manifestPath = appManifest
    }

    private fun basicCommand(): List<String> = listOf(flatpakBinaryPath, "run", flatpakBuilderCommand)

    private fun getAppId(): String {
        // This explodes any path and get last JSON filepath, exploding to get only fileName
        val packageName = manifestPath.split('/').last().split('.').first()

        // TODO : Export current appId from manifest.json file
        return packageName
    }

    /**
     * Get Flatpak command to run based on current spec, using Flatpak Builder,
     * using [FlatpakPaths] declared, with defaults to `flatpak run org.flatpak.Builder`;
     *
     * 1. [FlatpakCommand.CLEAN] translates to `rm -rf {buildDir}`
     * 2. [FlatpakCommand.BUILD] translates to `{baseCommand} --force-clean {buildDir} {manifestPath}`
     * 3. [FlatpakCommand.EXPORT] translates to: `{baseCommand} --repo=repo --force-clean {buildDir} {manifestPath} && flatpak build-bundle repo {appId}.{version}.flatpak {appId}`
     * 4. [FlatpakCommand.RUN] translates to `{baseCommand} --force-clean --run {buildDir} {manifestPath}`
     * 5. [FlatpakCommand.VALIDATE] translates to ``
     *
     * @param command [FlatpakCommand] triggered by user
     */
    fun getCommand(command: FlatpakCommand): List<String> = when (command) {
        FlatpakCommand.CLEAN -> listOf("rm", "-rf", buildDir)

        FlatpakCommand.BUILD -> basicCommand()
            .plus(listOf(buildDir, manifestPath))

        FlatpakCommand.RUN -> basicCommand()
            .plus(listOf("--run", buildDir, manifestPath, getAppId()))

        FlatpakCommand.EXPORT -> basicCommand()
            .plus(listOf("--force-clean", "--repo=repo-build", buildDir, manifestPath, "&&"))
            .plus(listOf(flatpakBinaryPath, "build-bundle", "build-repo", "${getAppId()}.flatpak", getAppId()))

        FlatpakCommand.VALIDATE -> TODO()
    }
}