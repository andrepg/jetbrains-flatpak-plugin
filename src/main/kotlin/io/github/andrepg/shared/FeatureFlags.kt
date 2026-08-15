package io.github.andrepg.shared

import com.intellij.util.SystemProperties

data object FeatureFlags {
    const val FEATURE_FLAG_ENABLE_GTK_PREVIEW = "flatpak.gtk.preview.enabled"

    fun getBoolean(feature: String, default: Boolean = false): Boolean =
        SystemProperties.getBooleanProperty(feature, default)
}
