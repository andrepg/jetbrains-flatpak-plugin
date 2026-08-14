import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
    // Source-context upload for Sentry crash reporting.
    // Read more: https://docs.sentry.io/platforms/java/source-context/
    id("io.sentry.jvm.gradle") version "6.19.0"
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
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = listOf("stable")
    }
    // Paid plugins cannot be run headless to collect searchable options
    // (no valid license / modal UI), so the task must be disabled. See
    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
    buildSearchableOptions = false
}

// The sandbox IDE (./gradlew runIde) has no Marketplace license, so premium
// features would otherwise be locked during development. The dev override is a
// runtime system property — release builds never set it, and premium access is
// only a convenience, since the source is open anyway (see BILLING.md §6.4).
tasks.named("runIde") {
    if (this is JavaExec) {
        systemProperty("flatpak.devtools.development", "true")
    }
}
