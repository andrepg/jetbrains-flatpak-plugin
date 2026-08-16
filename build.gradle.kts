import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
    // Source-context upload for Sentry crash reporting.
    // Read more: https://docs.sentry.io/platforms/java/source-context/
    id("io.sentry.jvm.gradle") version "6.19.0"
    // Kotlin code style checking
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

sentry {
    // Generates a JVM (Java, Kotlin, etc.) source bundle and uploads your source code to Sentry.
    // This enables source context, allowing you to see your source code as part of your stack
    // traces in Sentry. The upload only runs when SENTRY_AUTH_TOKEN is set.
    includeSourceContext = true

    org = "startap"
    projectName = "jetbrains-flatpak-plugin"
    authToken = System.getenv("SENTRY_AUTH_TOKEN")
}

// The upload only runs when SENTRY_AUTH_TOKEN is set (see the sentry {} block above);
// disable the task otherwise so local builds without the token do not fail.
tasks.matching { it.name.startsWith("sentryUploadSourceBundle") }.configureEach {
    enabled = System.getenv("SENTRY_AUTH_TOKEN") != null
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    implementation(libs.sentry)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.3.5")

        testFramework(TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here, for example:
        bundledPlugin("com.intellij.modules.json")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253.*"
        }
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = listOf("stable")
    }
    // Paid plugins cannot be run headless to collect searchable options
    // (no valid license / modal UI), so the task must be disabled. See
    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
    buildSearchableOptions = false
}

// Configure ktlint to use standard Kotlin style guide
ktlint {
    android.set(false)
    ignoreFailures.set(true) // Set to false once formatting issues are resolved
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
    filter {
        exclude("**/generated/**")
        exclude("**/test/**")
    }
}

// Disable ktlint for test source sets to avoid blocking on existing test file formatting
tasks.matching { it.name.contains("ktlintTestSourceSet") }.configureEach {
    enabled = false
}

// The sandbox IDE (./gradlew runIde) has no Marketplace license, so premium
// features would otherwise be locked during development. The dev override is a
// runtime system property — release builds never set it, and premium access is
// only a convenience, since the source is open anyway
tasks.named("runIde") {
    if (this is JavaExec) {
        systemProperty("flatpak.devtools.development", "true")
    }
}

// Regenerates the BUNDLED GTK/Adwaita schema artifacts
// (src/main/resources/schemas/gtk-ui-schema.json + gtk-ui.xsd) from GIR data.
// This provisions the bundled fallback (GNOME 50 basic support) shipped with the
// plugin; the phase-2 runtime per-project schema generation (GtkSchemaManager) is
// the primary path. Run it from CI pre-deploy (never during app lifecycle).
//   ./gradlew generateBundledGtkSchema
//   ./gradlew generateBundledGtkSchema -PgirDir=/path/to/gir-1.0 -PschemaOut=/tmp/gtk-ui-schema.json
tasks.register<JavaExec>("generateBundledGtkSchema") {
    description = "Regenerates the bundled GTK/Adwaita UI schema (gtk-ui-schema.json + gtk-ui.xsd) from GIR data"
    group = "build"
    mainClass.set("io.github.andrepg.gtk.schema.gir.GirSchemaExtractor")
    classpath = sourceSets.main.get().runtimeClasspath
    providers.gradleProperty("girDir").orNull?.let { args("--gir-dir", it) }
    providers.gradleProperty("schemaOut").orNull?.let { args("--schema-out", it) }
}
