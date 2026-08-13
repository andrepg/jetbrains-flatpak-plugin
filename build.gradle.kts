import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.3.5")

        testFramework(TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here, for example:
        bundledPlugin("com.intellij.modules.json")
    }
}

// The GtkBuilder schema extractor lives in the main module (JDK-only core).
// The root project opts out of the default Kotlin stdlib dependency (see
// gradle.properties), so the extractGtkSchema task needs stdlib explicitly.
// It is wired through a dedicated non-consumable configuration so the stdlib
// never leaks into the plugin distribution.
val extractGtkSchemaClasspath = configurations.create("extractGtkSchemaClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    "extractGtkSchemaClasspath"(kotlin("stdlib"))
}

/**
 * Regenerates `src/main/resources/schemas/gtk-ui-schema.json` and `gtk-ui.xsd`
 * from the GObject Introspection (GIR) files of the GNOME SDK.
 *
 * Usage:
 *   ./gradlew extractGtkSchema                              # auto-detect the installed GNOME SDK
 *   ./gradlew extractGtkSchema -PgirDir=/usr/share/gir-1.0
 *   ./gradlew extractGtkSchema -PschemaOut=/tmp/gtk-ui-schema.json
 */
val extractGtkSchema = tasks.register<JavaExec>("extractGtkSchema") {
    group = "flatpak"
    description = "Extracts the GtkBuilder UI JSON schema + XSD from Gtk/Adw/GtkSource GIR files (GNOME SDK)"
    classpath = sourceSets["main"].runtimeClasspath + configurations.getByName("extractGtkSchemaClasspath")
    mainClass = "io.github.andrepg.gtk.schema.gir.GirSchemaExtractor"

    providers.gradleProperty("girDir").orNull?.let { args("--gir-dir", it) }
    providers.gradleProperty("schemaOut").orNull?.let { args("--schema-out", it) }
}
