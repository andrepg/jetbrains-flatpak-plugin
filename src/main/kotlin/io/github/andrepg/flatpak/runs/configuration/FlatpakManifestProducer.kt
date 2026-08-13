package io.github.andrepg.flatpak.runs.configuration

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import io.github.andrepg.flatpak.detection.FlatpakProjectDetector
import io.github.andrepg.flatpak.runs.FlatpakCommand
import io.github.andrepg.shared.Localization

/**
 * Suggests a `Run 'Build <app-id>'` action when a Flatpak manifest is right-clicked.
 */
class FlatpakManifestProducer : LazyRunConfigurationProducer<FlatpakRunConfiguration>(), DumbAware {

    override fun getConfigurationFactory(): ConfigurationFactory = FlatpakRunConfigurator.factory()

    override fun setupConfigurationFromContext(
        configuration: FlatpakRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val file = findManifestFile(context, sourceElement) ?: return false
        val appId = FlatpakProjectDetector.isFlatpakManifest(file) ?: return false
        configuration.command = FlatpakCommand.BUILD
        configuration.manifestPath = file.path
        configuration.buildDir = FlatpakRunSettings.DEFAULT_OUTPUT
        configuration.setName(Localization.message("runs.configuration.build.name", appId))
        return true
    }

    override fun isConfigurationFromContext(
        configuration: FlatpakRunConfiguration,
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
}
