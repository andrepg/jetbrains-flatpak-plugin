package io.github.andrepg.flatpak.runs.execution

import com.intellij.openapi.project.Project
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettings
import io.github.andrepg.flatpak.runs.configuration.FlatpakRunSettingsAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File

class DeepCleanExecutorTest {

    private val project = mock(Project::class.java)

    private fun config(configure: FlatpakRunSettings.() -> Unit = {}): FlatpakRunSettings {
        val configuration = FlatpakRunSettings(mock(Project::class.java), null, null)
        configuration.loadState(FlatpakRunSettingsAttributes())
        configure(configuration)
        return configuration
    }

    @Test
    fun `absolute build dir is used as-is`() {
        val targets = DeepCleanExecutor().deepCleanTargets(project, config { buildDir = "/tmp/my-build" })
        assertEquals(File("/tmp/my-build"), targets[0])
    }

    @Test
    fun `relative build dir resolves against project base path`() {
        `when`(project.basePath).thenReturn("/mock/base")
        val targets = DeepCleanExecutor().deepCleanTargets(project, config { buildDir = "_build" })
        assertEquals(File("/mock/base/_build"), targets[0])
    }

    @Test
    fun `flatpak builder cache is always included`() {
        val targets = DeepCleanExecutor().deepCleanTargets(project, config { buildDir = "_build" })
        assertTrue(targets[1].path.endsWith(".cache/flatpak-builder"))
    }
}
