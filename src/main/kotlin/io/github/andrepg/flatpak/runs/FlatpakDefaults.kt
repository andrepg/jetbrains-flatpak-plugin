package io.github.andrepg.flatpak.runs

/**
 * Default settings for Flatpak run configurations, used wherever a value must
 * not be blank (the I1 guardrails) or the user has not configured one yet.
 */
enum class FlatpakDefaults(
    val value: String,
) {
    /** Default build directory for `flatpak-builder` runs. */
    BUILD_DIR("_build"),

    /** Default manifest file name inside the project root. */
    MANIFEST_FILE("flatpak.json"),
}
