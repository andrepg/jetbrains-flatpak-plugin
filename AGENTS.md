# AGENTS.md

## Plugin basics
- **Plugin ID**: `io.github.andrepg.flatpak-support`
- **Target IDE**: IntelliJ IDEA 2025.3.5
- **Language**: Kotlin
- **Build system**: Gradle with IntelliJ Platform Gradle Plugin

## Key files
- **Entry point**: `src/main/resources/META-INF/plugin.xml`
- **Flatpak commands**: `src/main/kotlin/io/github/andrepg/flatpak/`
  - `FlatpakCommand.kt`: Command enum (BUILD, CLEAN, COMPILE, EXPORT, RUN)
  - `FlatpakExecutor.kt`: Maps commands to flatpak-builder/flatpak CLI calls
  - `FlatpakRunState.kt`: Executes commands via IntelliJ's RunConfiguration
  - `FlatpakRunConfigurationType.kt`: Registers "Flatpak" run configuration type

## Commands

### Development
```bash
./gradlew runIde              # Launch sandbox IDE with plugin loaded
./gradlew build              # Build plugin ZIP in build/distributions/
./gradlew verifyPlugin       # Check plugin compatibility
./gradlew runPluginVerifier  # Run IntelliJ Plugin Verifier (if configured)
```

### Publishing
```bash
./gradlew publishPlugin      # Publish to JetBrains Marketplace (requires PUBLISH_TOKEN)
```

### Testing
```bash
./gradlew test               # Run tests
```

## Flatpak integration notes
- Commands execute via `flatpak-builder` and `flatpak` CLI
- **Current limitation**: `FlatpakCommand.RUN` uses placeholder `your.app.id` - must be replaced with actual Flatpak app ID
- Configuration requires:
  - `manifestPath`: Path to flatpak manifest file
  - `buildDir`: Build directory for flatpak-builder

## GNOME/Adwaita UI support
- **Not currently implemented** - plugin only has basic tool window
- Future work: Add XML schema support for GNOME/Adwaita UI files
- LSP integration would require additional dependencies and configuration

## Configuration quirks
- Target IDE version is hardcoded in `build.gradle.kts:15`
- Configuration cache enabled in `gradle.properties:8`
- Kotlin stdlib opt-out in `gradle.properties:5`

## Architecture
- Plugin uses IntelliJ's `ConfigurationTypeBase` for run configurations
- Flatpak commands integrate with IntelliJ's `CommandLineState`
- Message bundles in `src/main/resources/messages/` for i18n

## Next steps for full implementation
1. Replace placeholder app ID in `FlatpakExecutor.kt:19`
2. Add GNOME/Adwaita XML schema support
3. Implement LSP for XML files
4. Add proper configuration validation
5. Implement full SettingsEditor UI with command selection dropdown
