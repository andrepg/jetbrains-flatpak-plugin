package io.github.andrepg.flatpak.runs.configuration.validation

import io.github.andrepg.flatpak.runs.UserVisibleCommand
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings

/**
 * The CUSTOM command forwards its arguments verbatim as flatpak-builder's
 * COMMAND positional; running it with nothing typed would just print usage
 * help, so the configuration requires at least one argument up front.
 */
class CustomHasArgumentsRule : ValidationRule {
    override val id: String = "custom-has-arguments"

    override fun check(
        config: FlatpakRunSettings,
        basePath: String?,
    ): List<String> =
        if (config.command == UserVisibleCommand.CUSTOM && config.customArguments.isEmpty()) {
            listOf("Custom command requires at least one argument")
        } else {
            emptyList()
        }
}
