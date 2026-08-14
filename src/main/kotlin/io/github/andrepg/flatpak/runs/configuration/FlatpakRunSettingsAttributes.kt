package io.github.andrepg.flatpak.runs.configuration

import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.execution.configurations.RunConfigurationOptions as BaseOptions

/**
 * Options for [FlatpakRunSettings], following the documented
 * `RunConfigurationOptions` scheme: fields are backed by `StoredProperty`
 * delegates so they serialize through the configuration store.
 */
class FlatpakRunSettingsAttributes : BaseOptions() {
    @get:Attribute("flatpakManifest") var flatpakManifest: String? by string("flatpak.json")
    @get:Attribute("buildDir") var buildDir: String? by string("_build")
    @get:Attribute("command") var command: String? by string("BUILD")
    @get:Attribute("enableForceClean") var enableForceClean: Boolean by property(false)
    @get:Attribute("enableDeepClean") var enableDeepClean: Boolean by property(false)
    @get:Attribute("enablePortals") var enablePortals: Boolean by property(false)
    @get:Attribute("enableThemes") var enableThemes: Boolean by property(false)
    @get:Attribute("enableAudio") var enableAudio: Boolean by property(false)
    @get:Attribute("enableWayland") var enableWayland: Boolean by property(false)
    @get:Attribute("customArguments") var customArguments: String? by string("")
}
