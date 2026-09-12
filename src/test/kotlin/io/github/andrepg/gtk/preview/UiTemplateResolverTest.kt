package io.github.andrepg.gtk.preview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Path

class UiTemplateResolverTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun projectBase(): Path = tmp.root.toPath()

    private fun writeFile(
        relativePath: String,
        content: String,
    ): File {
        val file = File(tmp.root, relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
        return file
    }

    // -----------------------------------------------------------------
    // 1. No templates → passthrough
    // -----------------------------------------------------------------

    @Test
    fun `passthrough when project has no template definitions`() {
        val input =
            """<?xml version="1.0" encoding="UTF-8"?>
                <interface>
                  <object class="GtkBox"><property name="orientation">vertical</property></object>
                </interface>
                """
        val result = UiTemplateResolver.resolve(input, projectBase())
        assertTrue(result.contains("GtkBox"))
        assertFalse(result.contains("template"))
    }

    @Test
    fun `passthrough when target references only builtin classes`() {
        writeFile(
            "my-widgets.ui",
            """<?xml version="1.0" encoding="UTF-8"?>
                <interface>
                  <template class="MyWidget" parent="GtkBox">
                    <property name="spacing">4</property>
                    <child><object class="GtkLabel"/></child>
                  </template>
                </interface>
                """,
        )
        val input =
            """<?xml version="1.0" encoding="UTF-8"?>
                <interface>
                  <object class="GtkBox"><property name="orientation">vertical</property></object>
                </interface>
                """
        // Registry is non-empty, so content is re-serialized; must still contain
        // only Gtk* classes and no MyWidget expansion.
        val result = UiTemplateResolver.resolve(input, projectBase())
        assertTrue(result.contains("GtkBox"))
        assertFalse(result.contains("MyWidget"))
    }

    // -----------------------------------------------------------------
    // 2. Simple template expansion
    // -----------------------------------------------------------------

    @Test
    fun `simple template expands object to parent class with children inlined`() {
        writeFile(
            "my-widgets.ui",
            """<?xml version="1.0" encoding="UTF-8"?>
                <interface>
                  <template class="MyWidget" parent="GtkBox">
                    <property name="orientation">vertical</property>
                    <child>
                      <object class="GtkLabel"><property name="label">From template</property></object>
                    </child>
                  </template>
                </interface>
                """,
        )
        val input =
            """<?xml version="1.0" encoding="UTF-8"?>
                <interface>
                  <object class="MyWidget" id="widget">
                    <property name="margin">12</property>
                  </object>
                </interface>
                """
        val result = UiTemplateResolver.resolve(input, projectBase())

        assertFalse("template class must be gone", result.contains("class=\"MyWidget\""))
        assertTrue("expanded to parent class", result.contains("class=\"GtkBox\""))

        // Inherited property from the template root.
        assertTrue(result.contains("orientation"))
        assertTrue(result.contains("vertical"))

        // Target property preserved.
        assertTrue(result.contains("margin"))
        assertTrue(result.contains("12"))

        // Template child inlined.
        assertTrue(result.contains("From template"))
        assertTrue(result.contains("GtkLabel"))
    }

    // -----------------------------------------------------------------
    // 3. Nested templates + cycle guard
    // -----------------------------------------------------------------

    @Test
    fun `nested templates are expanded recursively`() {
        writeFile(
            "widgets1.ui",
            """<?xml version="1.0" encoding="UTF-8"?>
                <interface>
                  <template class="MyButton" parent="GtkButton">
                    <property name="label">nested</property>
                  </template>
                </interface>
                """,
        )
        writeFile(
            "widgets2.ui",
            """<?xml version="1.0" encoding="UTF-8"?>
                <interface>
                  <template class="MyRow" parent="GtkBox">
                    <child><object class="MyButton"/></child>
                  </template>
                </interface>
                """,
        )
        writeFile(
            "widgets3.ui",
            """<?xml version="1.0" encoding="UTF-8"?>
                <interface>
                  <template class="MyWindow" parent="GtkWindow">
                    <child><object class="MyRow"/></child>
                  </template>
                </interface>
                """,
        )
        val input =
            """<?xml version="1.0" encoding="UTF-8"?>
                <interface>
                  <object class="MyWindow"/>
                </interface>
                """
        val result = UiTemplateResolver.resolve(input, projectBase())

        assertFalse("MyWindow resolved", result.contains("class=\"MyWindow\""))
        assertFalse("MyRow resolved", result.contains("class=\"MyRow\""))
        assertFalse("MyButton resolved", result.contains("class=\"MyButton\""))
        assertTrue("parent class present", result.contains("class=\"GtkWindow\""))
        assertTrue("nested GtkBox present", result.contains("class=\"GtkBox\""))
        assertTrue("nested GtkButton present", result.contains("class=\"GtkButton\""))
        assertTrue("template label inlined", result.contains("nested"))
    }

    @Test
    fun `cyclic template definitions do not loop forever`() {
        // A cyclic template graph (A uses B, B uses A) must terminate.
        writeFile(
            "widgets-a.ui",
            """<?xml version="1.0" encoding="UTF-8"?>
                <interface>
                  <template class="AWidget" parent="GtkBox">
                    <child><object class="BWidget"/></child>
                  </template>
                </interface>
                """,
        )
        writeFile(
            "widgets-b.ui",
            """<?xml version="1.0" encoding="UTF-8"?>
                <interface>
                  <template class="BWidget" parent="GtkBox">
                    <child><object class="AWidget"/></child>
                  </template>
                </interface>
                """,
        )
        val input =
            """<?xml version="1.0" encoding="UTF-8"?>
                <interface>
                  <object class="AWidget"/>
                </interface>
                """
        // Must terminate within MAX_DEPTH and not throw.
        val result = UiTemplateResolver.resolve(input, projectBase())
        assertTrue("cycle halted, parent class present", result.contains("GtkBox"))
    }

    // -----------------------------------------------------------------
    // 4. Property override
    // -----------------------------------------------------------------

    @Test
    fun `target property overrides template property with same name`() {
        writeFile(
            "widgets.ui",
            """<?xml version="1.0" encoding="UTF-8"?>
                <interface>
                  <template class="Card" parent="GtkBox">
                    <property name="margin">12</property>
                    <property name="spacing">6</property>
                  </template>
                </interface>
                """,
        )
        val input =
            """<?xml version="1.0" encoding="UTF-8"?>
                <interface>
                  <object class="Card" id="card1">
                    <property name="margin">0</property>
                  </object>
                </interface>
                """
        val result = UiTemplateResolver.resolve(input, projectBase())

        // Non-overridden template property survives.
        assertTrue(result.contains("spacing"))
        assertTrue(result.contains("6"))

        // Overridden property: target wins, template value dropped.
        assertTrue("target margin remains", result.contains("margin"))
        assertTrue(result.contains(">0<"))
        assertFalse("template margin 12 dropped", result.contains(">12<"))
    }

    // -----------------------------------------------------------------
    // 5. Id stripping
    // -----------------------------------------------------------------

    @Test
    fun `inlined template inner objects have ids stripped`() {
        writeFile(
            "widgets.ui",
            """<?xml version="1.0" encoding="UTF-8"?>
                <interface>
                  <template class="Form" parent="GtkBox">
                    <child>
                      <object class="GtkEntry" id="templateEntry"/>
                    </child>
                  </template>
                </interface>
                """,
        )
        val input =
            """<?xml version="1.0" encoding="UTF-8"?>
                <interface>
                  <object class="Form" id="form1"/>
                </interface>
                """
        val result = UiTemplateResolver.resolve(input, projectBase())

        assertFalse("template id stripped", result.contains("templateEntry"))
        assertTrue("target ids preserved", result.contains("form1"))
        assertTrue("inner object present", result.contains("GtkEntry"))
    }

    // -----------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------

    @Test
    fun `malformed project ui files are skipped quietly`() {
        writeFile("bad.widgets.ui", "<interface><object class='GtkBox'") // unclosed
        writeFile("good.ui", "not even xml <<<")

        val registry = UiTemplateResolver.buildTemplateRegistry(projectBase())
        assertTrue(registry.isEmpty())
    }
}
