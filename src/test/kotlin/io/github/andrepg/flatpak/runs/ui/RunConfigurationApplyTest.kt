package io.github.andrepg.flatpak.runs.ui

import com.intellij.configurationStore.SerializableScheme
import com.intellij.execution.impl.ConfigurationSettingsEditorWrapper
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.SingleConfigurationConfigurable
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettingsFactory
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettingsType
import java.awt.Component
import javax.swing.JComponent

class RunConfigurationApplyTest : BasePlatformTestCase() {

    fun `test options serialize into the configuration scheme`() {
        val factory = FlatpakRunSettingsFactory(FlatpakRunSettingsType())
        val configuration = factory.createTemplateConfiguration(project) as FlatpakRunSettings
        configuration.manifestPath = "original.json"
        val runManager = RunManagerImpl.getInstanceImpl(project)
        val settings = runManager.createConfiguration(configuration, factory)
        runManager.addConfiguration(settings)

        val xml = JDOMUtil.writeElement((settings as SerializableScheme).writeScheme())

        assertTrue(
            "Run configuration options must be part of the scheme XML",
            xml.contains("flatpakManifest=\"original.json\""),
        )
    }

    fun `test apply is disabled initially and enabled after an edit`() {
        val factory = FlatpakRunSettingsFactory(FlatpakRunSettingsType())
        val configuration = factory.createTemplateConfiguration(project) as FlatpakRunSettings
        configuration.manifestPath = "original.json"
        val runManager = RunManagerImpl.getInstanceImpl(project)
        val settings = runManager.createConfiguration(configuration, factory)
        runManager.addConfiguration(settings)

        val configurable = SingleConfigurationConfigurable.editSettings<FlatpakRunSettings>(settings, null)
        try {
            val wrapper = configurable.getEditor() as ConfigurationSettingsEditorWrapper
            val component = wrapper.getComponent()
            val checkBox = findComponent(component) { it is JBCheckBox && it.text.contains("Force clean") } as JBCheckBox

            assertFalse("Apply should be disabled initially", configurable.isModified)

            checkBox.doClick()

            assertTrue("Apply should be enabled after an edit", configurable.isModified)
        } finally {
            configurable.disposeUIResources()
        }
    }

    private fun findComponent(root: JComponent, predicate: (Component) -> Boolean): Component? {
        for (child in root.components) {
            if (predicate(child)) return child
            if (child is JComponent) {
                findComponent(child, predicate)?.let { return it }
            }
        }
        return null
    }
}
