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

// Standalone Kotlin source set for the GtkBuilder schema extractor.
// It only relies on the JDK's built-in XML parser, so it runs on any machine
// that can run Gradle (including CI agents in GitHub Actions).
sourceSets {
    create("scripts") {
        kotlin.srcDir("scripts/src/main/kotlin")
    }
}

// The root project opts out of the default Kotlin stdlib dependency (see
// gradle.properties), so the standalone extractor needs it explicitly.
dependencies {
    "scriptsImplementation"(kotlin("stdlib"))
}

/**
 * Generates `src/main/resources/schemas/gtk-ui-schema.json` from the GObject
 * Introspection (GIR) files of the GNOME 50 SDK.
 *
 * Usage:
 *   ./gradlew extractGtkSchema
 *   ./gradlew extractGtkSchema -PgirDir=/usr/share/gir-1.0
 *   ./gradlew extractGtkSchema -PschemaOut=/tmp/gtk-ui-schema.json
 */
val extractGtkSchema = tasks.register<JavaExec>("extractGtkSchema") {
    group = "flatpak"
    description = "Extracts the GtkBuilder UI JSON schema from Gtk/Adw GIR files (GNOME 50 SDK)"
    classpath = sourceSets["scripts"].runtimeClasspath
    mainClass = "io.github.andrepg.flatpak.schema.GirSchemaExtractor"

    // Hardcoded GNOME 50 SDK base path for now; the extractor resolves the
    // SDK commit sub-directory that actually contains the .gir files.
    val girDirArg = providers.gradleProperty("girDir").orElse(
        "/var/home/apg/.local/share/flatpak/runtime/org.gnome.Sdk/x86_64/50"
    ).get()
    val outputArg = providers.gradleProperty("schemaOut").orElse(
        layout.projectDirectory.file("src/main/resources/schemas/gtk-ui-schema.json").asFile.absolutePath
    ).get()
    args(girDirArg, outputArg)
}
