# AGENTS.md

## Plugin basics
- **Plugin ID**: `io.github.andrepg.flatpak-support`
- **Target IDE**: IntelliJ IDEA 2025.3.5
- **Language**: Kotlin
- **Build system**: Gradle with IntelliJ Platform Gradle Plugin

## Key files
- **Entry point**: `src/main/resources/META-INF/plugin.xml`
- **Flatpak runs**: `src/main/kotlin/io/github/andrepg/flatpak/runs/`
  - `FlatpakCommand.kt`: Command enums (`InternalCommand`: BUILD, CLEAN, DEEP_CLEAN, EXPORT, RUN, VALIDATE, CUSTOM; `UserVisibleCommand`)
  - `configuration/`: Run configuration machinery (type, configuration, factory, generator, manifest producer, project opener, settings defaults). `FlatpakRunGenerator.formatRunName(command, appId)` names configs `[build] <app-id>`; `RunManifestProducer.setupConfigurationFromContext` sets command/manifest/buildDir/name explicitly
  - `execution/CommandSelectionStrategy.kt`: Picks commands from config flags (cleanup prepends for build-like commands)
  - `execution/CommandExecutionEngine.kt`: Maps one command to a flatpak-builder/flatpak CLI line; `toGeneralCommandLine` for the IDE process API; wraps command/process failures in `FlatpakExecutionException`
  - `execution/CommandExecutionArguments.kt`: sandbox flag sets — `DEFAULT_BUS` (`--socket=session-bus`, `--socket=system-bus`, always on for `Run`) + opt-in portals/themes/audio/wayland
  - `execution/commands/CommandFactory.kt`: base class with `getFlatpakCommand()`/`buildSandboxOptions()` and the `effectiveBuildDir()`/`effectiveManifestPath()` non-blank guards (I1)
  - `execution/CleanupThenProcessHandler.kt`: `ProcessHandler` that runs cleanup pre-steps (CLEAN/DEEP_CLEAN) as raw OS processes on a pooled thread (never the EDT — `OSProcessHandler.checkEdtAndReadAction` forbids blocking `runProcess` from `CommandLineState.startProcess`), then starts the main `OSProcessHandler` and relays its output/termination
  - `execution/FlatpakRunner.kt`: `CommandLineState` that picks the command list, routes cleanup steps through `CleanupThenProcessHandler`, and attaches the console
  - `ui/FlatpakRunSettingsPanel.kt`: Settings editor form for the run configuration; the *Custom arguments* row is hidden unless `flatpak.runs.show-custom-arguments=true` (I3)
- **Manifest reading** (IO policy, §3.2): `flatpak/utils/FlatpakManifestReader.kt` = pure-JDK parser (`parseFields(content, fileName, keys)`, throws `FlatpakManifestException`) + JDK `readXxx(path)` conveniences (forgiving, for tooling/hermetic tests). `flatpak/utils/FlatpakManifestVfsReader.kt` = IDE glue reading `VirtualFile` (or a path via `LocalFileSystem`, with guarded JDK fallback for headless tests). IDE-glue callers (`FlatpakProjectDetector`, `RunCommandFactory`, `GtkSdkHintResolver`) read through the VFS reader.
- **Exceptions**: `flatpak/exception/FlatpakExceptions.kt` — `FlatpakPluginException` base + `FlatpakManifestException`/`FlatpakExecutionException`/`FlatpakConfigurationException` (all pure JDK). Wrapped at boundaries (`CommandExecutionEngine`); platform contracts kept (`RuntimeConfigurationError` in `FlatpakRunSettings.checkConfiguration`).
- **GTK preview**: `src/main/kotlin/io/github/andrepg/gtk/preview/` (JDK-only `GtkBuilderToolRunner` + `AdwShimManager`; IDE glue `ui/GtkPreviewService`, `GtkPreviewPanel`, `GtkPreviewToolWindowFactory`, `GtkPreviewEditorNotificationProvider`). All gates go through `FeatureFlags.FEATURE_FLAG_ENABLE_GTK_PREVIEW` (§3.1).
- **Billing/licensing**: `src/main/kotlin/io/github/andrepg/shared/license/` (`LicenseCheck` = Kotlin port of JetBrains' `CheckLicense`, verifies `LicensingFacade` stamps; `PremiumFeatureGate` = the single premium/paid-feature decision point). Freemium plugin: `<product-descriptor code="PFLATPAKDEV" ... optional="true"/>` in `plugin.xml`, product versioned `2026.1.x` (calendar scheme). Premium = GTK Preview only; the gate also honors the dev system property `flatpak.devtools.development` (set automatically by `runIde`) so development is never locked out. See `BILLING.md`.

## Commands

### Development
```bash
./gradlew runIde              # Launch sandbox IDE with plugin loaded
./gradlew build              # Build plugin ZIP in build/distributions/
./gradlew generateBundledGtkSchema  # Regenerate bundled GTK schema (JSON + XSD) from GIR
```

**Do NOT run `verifyPlugin` or `runPluginVerifier` unless explicitly asked.**

### Compatibility checks (only when asked)
```bash
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
- `FlatpakCommand.RUN` executes the manifest's `command` field (read via the VFS reader in `RunCommandFactory`), falling back to the app-id; cleanup (CLEAN/DEEP_CLEAN) pre-steps run synchronously as separate processes before the main command
- Run command sandbox always includes the D-Bus sockets (`DEFAULT_BUS`: `--socket=session-bus`, `--socket=system-bus`) before the opt-in portal/theme/audio/wayland flags and the positional `DIRECTORY MANIFEST COMMAND` args (I2)
- Factories never emit a blank `buildDir`/`manifestPath` positional arg: `CommandFactory.effectiveBuildDir()`/`effectiveManifestPath()` default to `_build`/`flatpak.json` (I1)
- Configuration requires:
  - `manifestPath`: Path to flatpak manifest file
  - `BUILD_DIR`: Build directory for flatpak-builder

## GNOME/Adwaita UI support
- `.ui`/`.glade` are served the generated **XSD** (`src/main/resources/schemas/gtk-ui.xsd`, no target namespace, root `<interface>`) — NOT the JSON schema: the bundled JSON schema feature (`com.jetbrains.jsonSchema`, EP `JavaScript.JsonSchema.ProviderFactory`) has **no XML support**, so it can never drive completion/validation in XML files.
- The XSD is wired through the XML plugin's `com.intellij.xml.schemaProvider` EP (`XmlSchemaProvider`, see `src/main/kotlin/io/github/andrepg/gtk/schema/providers/GtkInterfaceXmlSchemaProvider.kt`); `.ui`/`.glade` are mapped to the XML file type via `<fileType name="XML" extensions="ui;glade"/>` in `plugin.xml`, so the files open as XML (highlighting, structure view) and get schema completion/validation. Plain `.xml` files are also served when their root element is `<interface>` (matches the schema) **and** the project is a recognized Flatpak project (gated in `getSchema` via the `SdkHint`).
- `gtk-ui.xsd` is also registered as a **standard resource** in `plugin.xml`:
  ```xml
  <standardResource url="urn:io.github.andrepg:flatpak-support:schemas:gtk-ui" path="schemas/gtk-ui.xsd" version="1"/>
  ```
  `url` = canonical identifier, `path` = bundled resource path (both required). Note: standard resources only resolve documents that reference `url` — GtkBuilder `.ui` files carry no namespace/URL, so this registration is auxiliary; auto-association must come from the `XmlSchemaProvider`.
- `gtk-ui-schema.json` (JSON Schema draft-07, `$defs`) stays on the classpath as an artifact of the generator but is **not** registered for `.ui` files.
- LSP integration would require additional dependencies and configuration

## Configuration quirks
- Target IDE version is hardcoded in `build.gradle.kts:15`
- Configuration cache enabled in `gradle.properties:8`
- Kotlin stdlib opt-out in `gradle.properties:5`

## Architecture
- Plugin uses IntelliJ's `ConfigurationTypeBase` for run configurations
- Flatpak commands integrate with IntelliJ's `CommandLineState`
- Message bundles in `src/main/resources/messages/` for i18n

## GTK schema namespace
- The GTK/Adwaita schema feature lives under `io.github.andrepg.gtk` (not the Flatpak namespace).
- Core (`gtk/schema/`, `gtk/schema/gir/`, `gtk/schema/locator/`) is **JDK-only** (no IntelliJ/Flatpak imports) so it can run from the `generateBundledGtkSchema` Gradle task and from inside the IDE. `gtk/schema/providers/` is IDE glue and the composition root: it computes the `SdkHint` from `FlatpakManifestVfsReader.readSdk()`/`readRuntime()` via `FlatpakProjectDetector.findManifests()`, then delegates to `GtkSchemaManager`.
- `GtkSchemaManager` resolves the project SDK's GIR dir via `GirSdkLocator` (flatpak CLI first, install-root glob fallback), generates `gtk-ui-<key>.xsd` into the plugin config dir (idempotent, background `executeOnPooledThread`), and falls back to the bundled classpath `/schemas/gtk-ui.xsd`.
- Regenerate bundled artifacts (JSON + XSD, incl. GtkSource-5) with `./gradlew generateBundledGtkSchema` (provisions the bundled fallback — GNOME 50 basic support — for the phase-2 runtime schema feature; runtime per-project generation is the primary path). The extractor auto-detects the installed GNOME SDK or takes `-PgirDir=`/`-PschemaOut=`. CI pre-publish (`pre-publish.yml`) runs it and auto-commits drift; never run it during app lifecycle.
- The GTK snapshot preview renders `.ui` files via `gtk4-builder-tool` inside the GNOME SDK: `GtkBuilderToolRunner` (validate/render, JDK-only) + `AdwShimManager` (per-branch `adw_init()` constructor shim compiled with `cc`/`pkg-config`, cached in the config dir). Host `/tmp` is masked inside the flatpak sandbox, so test/preview files must live under `$HOME` (exposed via `--filesystem=host`).

## Next steps for full implementation
1. Implement LSP for XML files
2. Add proper configuration validation
3. Implement full SettingsEditor UI with command selection dropdown
4. GResource integration and undeclared-file notifications

## CI/CD
- `.github/workflows/ci.yml`: on PR/push — `./gradlew build` + `./gradlew test` (GTK tests are `@Ignore`'d, so this is the non-GTK gate).
- `.github/workflows/pre-publish.yml`: on `v*` tag / manual — regenerates the bundled GTK schema in a pinned Fedora + GNOME SDK 50 container, auto-commits drift, then `verifyPlugin` + `publishPlugin` (requires the `PUBLISH_TOKEN` secret).

## Feature flags (runtime system properties)
- `flatpak.gtk.preview.enabled` — enables the GTK preview/schema premium features (also the Marketplace `<with>` property).
- `flatpak.runs.show-custom-arguments` — shows the legacy *Custom arguments* row in the run-configuration editor (hidden by default).
- `flatpak.devtools.development` — dev-only premium unlock, set by `runIde`.