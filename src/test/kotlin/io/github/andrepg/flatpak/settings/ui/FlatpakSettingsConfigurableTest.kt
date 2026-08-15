package io.github.andrepg.flatpak.settings.ui

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.UIUtil
import io.github.andrepg.flatpak.settings.FlatpakGlobalSettingsState

class FlatpakSettingsConfigurableTest : BasePlatformTestCase() {
    fun `test apply persists to the real service and reset restores`() {
        val settings = service<FlatpakGlobalSettingsState>()
        val previousFlatpak = settings.flatpakBinaryPath
        val previousBuilder = settings.flatpakBuilderBinaryPath
        try {
            val configurable = FlatpakSettingsConfigurable()
            val component = configurable.createComponent()
            val fields = UIUtil.findComponentsOfType(component, JBTextField::class.java)
            assertEquals("two text fields expected", 2, fields.size)
            val flatpakField = fields.first()
            val builderField = fields.last()

            configurable.reset()
            flatpakField.text = "/custom/flatpak"
            builderField.text = "/custom/flatpak-builder"
            assertTrue("isModified must reflect an edit", configurable.isModified)

            configurable.apply()

            assertEquals("/custom/flatpak", settings.flatpakBinaryPath)
            assertEquals("/custom/flatpak-builder", settings.flatpakBuilderBinaryPath)
            assertFalse("isModified must be false after apply", configurable.isModified)

            flatpakField.text = "reset-me"
            configurable.reset()
            assertEquals("/custom/flatpak", flatpakField.text)
            assertEquals("/custom/flatpak-builder", builderField.text)

            configurable.disposeUIResources()
        } finally {
            settings.flatpakBinaryPath = previousFlatpak
            settings.flatpakBuilderBinaryPath = previousBuilder
        }
    }
}
