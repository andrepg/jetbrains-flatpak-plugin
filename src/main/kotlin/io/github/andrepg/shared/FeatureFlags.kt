package io.github.andrepg.shared

import com.intellij.util.SystemProperties

data object FeatureFlags {
    const val FEATURE_FLAG_ENABLE_GTK_PREVIEW = "flatpak.gtk.preview.enabled"

    /**
     * Shows the "Custom arguments" row in the run-configuration editor. Defaults
     * to off: the field is a legacy escape hatch that duplicates the command
     * selection dropdown, so it stays available but out of the default UI.
     */
    const val FEATURE_FLAG_SHOW_CUSTOM_ARGUMENTS = "flatpak.runs.show-custom-arguments"

    fun getBoolean(feature: String, default: Boolean = false): Boolean =
        SystemProperties.getBooleanProperty(feature, default)
}
