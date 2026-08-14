package io.github.andrepg.flatpak.runs.configuration

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import io.github.andrepg.flatpak.detection.FlatpakProjectDetector

/**
 * Suggests a `Run 'Build <app-id>'` action when a Flatpak manifest is right-clicked.
 */
class RunManifestProducer : LazyRunConfigurationProducer<FlatpakRunSettings>(), DumbAware {
    override fun setupConfigurationFromContext(
        configuration: FlatpakRunSettings,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val file = findManifestFile(context, sourceElement) ?: return false
        val appId = FlatpakProjectDetector.isFlatpakManifest(file) ?: return false

        configuration.manifestPath = file.path

        return true
    }

    override fun isConfigurationFromContext(
        configuration: FlatpakRunSettings,
        context: ConfigurationContext
    ): Boolean {
        val file = findManifestFile(context, null) ?: return false
        return configuration.manifestPath == file.path
    }

    private fun findManifestFile(
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>?
    ): VirtualFile? {
        context.location?.virtualFile?.let { return it }
        context.dataContext.getData(CommonDataKeys.VIRTUAL_FILE)?.let { return it }
        return sourceElement?.get()?.containingFile?.virtualFile
    }

    override fun getConfigurationFactory(): ConfigurationFactory {
        return ConfigurationTypeUtil.findConfigurationType(FlatpakRunSettingsType::class.java)
            .configurationFactories
            .first()
    }
}
