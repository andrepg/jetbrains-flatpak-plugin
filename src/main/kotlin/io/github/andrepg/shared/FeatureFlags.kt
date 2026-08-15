package io.github.andrepg.shared

import com.intellij.util.SystemProperties
import io.github.andrepg.shared.log.Log

data object FeatureFlags {

    private val log = Log.getInstance(FeatureFlags::class.java)

    const val FEATURE_FLAG_ENABLE_GTK_PREVIEW = "flatpak.gtk.preview.enabled"

    fun getBoolean(feature: String, default: Boolean = false): Boolean {
        val value = SystemProperties.getBooleanProperty(feature, default)
        if (value != default) {
            log.debug("Feature flag $feature=$value")
        }
        return value
    }
}
