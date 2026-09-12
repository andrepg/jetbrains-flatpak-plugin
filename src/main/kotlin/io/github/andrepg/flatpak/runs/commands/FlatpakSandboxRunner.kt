package io.github.andrepg.flatpak.runs.commands

/**
 * Renders `flatpak run` command lines for every sandboxed invocation (the
 * Flatpak Builder runs and the GNOME SDK tooling), so the shell shape stays
 * consistent across the plugin.
 *
 * [flatpakOptions] are passed to `flatpak run` itself (before the app ref),
 * while [refArguments] are forwarded to the sandboxed app verbatim.
 */
object FlatpakSandboxRunner {
    fun run(
        flatpakBinary: String,
        appRef: String,
        flatpakOptions: List<String> = emptyList(),
        refArguments: List<String> = emptyList(),
    ): List<String> =
        buildList {
            add(flatpakBinary)
            add("run")
            addAll(flatpakOptions)
            add(appRef)
            addAll(refArguments)
        }
}