package io.github.andrepg.gtk.schema

/**
 * Describes the GNOME SDK a project expects, derived from its Flatpak manifest
 * (`sdk`/`runtime` fields). Manifest-agnostic: the Flatpak domain builds this
 * hint and the GTK schema domain consumes it.
 *
 * @property sdkAppId the SDK app-id (e.g. `org.gnome.Sdk`)
 * @property branch the SDK branch (e.g. `50`), when the manifest pins one
 */
data class SdkHint(
    val sdkAppId: String,
    val branch: String?,
) {
    /** Stable cache key: `<sdkAppId>-<branch>` when known, otherwise the app-id. */
    val key: String get() = branch?.let { "$sdkAppId-$it" } ?: sdkAppId
}
