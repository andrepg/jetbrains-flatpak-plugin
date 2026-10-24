# AGENTS.md

## Plugin basics
- **Plugin ID**: `io.github.andrepg.flatpak-support`
- **Target IDE**: IntelliJ IDEA 2025.3.5
- **Language**: Kotlin
- **Build system**: Gradle with IntelliJ Platform Gradle Plugin

## Key files
- **Entry point**: `src/main/resources/META-INF/plugin.xml`
- **Flatpak runs**: `src/main/kotlin/io/github/andrepg/flatpak/runs/`
  - `FlatpakDefaults.kt`: single source of truth for default command-line values — `BUILD_DIR` (`_build`), `MANIFEST_FILE` (`flatpak.json`) — referenced by the config attributes, the detector, the generator and the command engine (I1)
  - `UserVisibleCommand.kt`: the command enum the run-config editor shows (BUILD, EXPORT, RUN, VALIDATE, CUSTOM); `InternalCommand` was merged into it — everything downstream (steps, engine, tests) uses `UserVisibleCommand`
  - `configuration/`: Run configuration machinery (type, configuration, factory, generator, manifest producer, project opener, settings defaults). `FlatpakRunGenerator.formatRunName(command, appId)` names configs `[build] <app-id>`; `FlatpakRunGenerator.configureForManifest` (shared with `RunManifestProducer`) sets command/manifest/buildDir/name explicitly. `FlatpakRunSettings` implements `LocatableConfiguration`: `suggestedName()` drives the *Run → Edit Configurations → New* name (`[command] <app-id>`), `isGeneratedName()` recognizes the generated pattern, `buildDirFile(basePath)` resolves the build dir (shared with `DeepCleanExecutor`/`FlatpakRunner`); `customArguments` round-trips newline-delimited so arguments with spaces survive the editor↔config round-trip; `RunConfigurationValidator` (pure JDK) collects errors for `checkConfiguration`
  - `commands/`: everything that renders a Flatpak CLI line.
    - `FlatpakCommandLine.kt`: the base `flatpak run org.flatpak.Builder` command (`flatpakBuilderCommand()`) plus the non-blank `effectiveBuildDir()`/`effectiveManifestPath()` guards and `buildSandboxOptions(config)`
    - `FlatpakSandboxRunner.kt`: generic `flatpak run` shape (`flatpakOptions` before the app ref, `refArguments` after) shared by the engine's RUN and the GTK SDK tooling
    - `CommandExecutionEngine.kt`: builds one command line per `UserVisibleCommand` with private per-command builders — **the five `CommandFactory` subclasses were collapsed into this class**; `toGeneralCommandLine` for the IDE process API; wraps command/process failures in `FlatpakExecutionException`. Constructor takes a `hostBusAvailable` predicate (default `CommandExecutionArguments::hostHasFlatpakBus`) so RUN's D-Bus sockets are testable
  - `commands/CommandExecutionArguments.kt`: sandbox flag sets — `DEFAULT_BUS` (`--socket=session-bus`, `--socket=system-bus`, added to `Run` only when `hostHasFlatpakBus()` — `/run/flatpak/bus` exists; otherwise skipped with a warning, GNOME Builder-style filtered default bus) + opt-in portals/themes/audio/wayland
  - `cleanup/`: blocking pre-step implementations — `DeepCleanExecutor.kt` deletes deep-clean targets through the IntelliJ VFS inside a `WriteCommandAction` (pooled-thread caller required); `StaleFuseMountCleaner.kt` (pure JDK) unmounts stale rofiles-fuse mounts inside flatpak-builder state dirs (project root + build dir — the state dir lives at the working directory, NOT under `_build`), fail-open
  - `steps/`: orchestration pipeline. `CommandChainProcessHandler.kt`: `ProcessHandler` that runs blocking pre-steps on a pooled thread — never the EDT — then runs the main `OSProcessHandler` and relays its output/termination. Every step is announced to the console as a named workflow step (`Running DEEP_CLEAN...`, `Running BUILD: <cmdline>`, `<label> finished with exit code N`) so the flow is visible, not just the build report
  - `steps/FlatpakRunner.kt`: `CommandLineState` that picks the command, labels the chain steps, routes deep clean through `CommandChainProcessHandler`, and attaches the console
  - `ui/FlatpakRunSettingsPanel.kt`: Settings editor form for the run configuration. Option groups are command-sensitive and toggle live with the command combo: cleanup for BUILD, portal permissions for RUN, *Custom arguments* (shown right below the command box) for CUSTOM
- **Process layer**: `shared/process/` is the universal synchronous process runner for every domain — flatpak tooling, JSON schema detection, GTK schema locator and preview, cleanup commands (unevenly absorbed, not a copy). It is **IDE-backed**, not JDK-only: `DefaultProcessRunner` is a thin `CapturingProcessHandler`/`GeneralCommandLine` adapter (public platform APIs) that captures stdout/stderr and destroys the process on timeout — there is no hand-rolled JVM process stack, so the sync CLI layer shares the platform's process lifecycle. The `CommandRunner` seam keeps the logic cores (e.g. `GirSdkLocator`, `GtkBuilderToolRunner`) free of `com.intellij` imports: those files reference only the local `CommandRunner`/`DefaultProcessRunner` types, so they stay headless-testable (plain JUnit runs real subprocesses, e.g. the `FakeFlatpakCli` script). `FlatpakRuntimeRow`/`parseFlatpakRuntimeList` live here too. Live IDE-integrated runs (deep-clean, engine) go through `GeneralCommandLine`/`OSProcessHandler`/`WriteCommandAction`, not this layer.
- **Manifest reading** (IO policy, §3.2): single file `flatpak/utils/FlatpakManifestReaders.kt` with two objects. `FlatpakManifestReader` = pure-JDK parser (`parseFields(content, fileName, keys)`, throws `FlatpakManifestException`) + forgiving JDK conveniences (`readFields(path)`, `readAppId(path)`; shared `parseFieldsForgiving`/`pickAppId` internals) for tooling/hermetic tests. `FlatpakManifestVfsReader` = IDE glue reading `VirtualFile` (or a path via `LocalFileSystem`, with guarded JDK fallback for headless tests); API: `readFields(file|project,path)`, `readAppId(file|project,path)`, `readCommand(project,path)`. IDE-glue callers (`FlatpakProjectDetector`, `CommandExecutionEngine`, `GtkSdkHintResolver`) read through the VFS reader.
- **Exceptions**: `flatpak/exception/FlatpakExceptions.kt` — `FlatpakPluginException` base + `FlatpakManifestException`/`FlatpakExecutionException`/`FlatpakConfigurationException` (all pure JDK). Wrapped at boundaries (`CommandExecutionEngine`); platform contracts kept (`RuntimeConfigurationError` in `FlatpakRunSettings.checkConfiguration`).
- **Billing/licensing**: `src/main/kotlin/io/github/andrepg/shared/license/` (`LicenseCheck` = Kotlin port of JetBrains' `CheckLicense`, verifies `LicensingFacade` stamps; `PremiumFeatureGate` = the single premium/paid-feature decision point). Freemium plugin: `<product-descriptor code="PFLATPAKDEV" ... optional="true"/>` in `plugin.xml`, product versioned `2026.1.x` (calendar scheme). The gate also honors the dev system property `flatpak.devtools.development` (set automatically by `runIde`) so development is never locked out. See `BILLING.md`.
- **Diagnostics & error reporting**: `shared/log/LogConfiguration.kt` (JDK-only debug toggle for the `io.github.andrepg.*` JUL namespace), `shared/diagnostics/DiagnosticsSettingsState.kt` + `DiagnosticsSettings.kt` (persisted opt-in Sentry + debug-logging toggles, kept independent of the Flatpak binary settings), `shared/diagnostics/DiagnosticsInitializer.kt` (IDE glue, `com.intellij.ide.AppLifecycleListener`; applies the settings/system-property config at startup and logs a summary), `shared/sentry/SentryInitializer.kt` (DSN chain property → env → constant; `reconfigure()` is idempotent; environment = `development` when `PremiumFeatureGate.isDevelopmentBypass()`, else `production`) + `SentryLogBridge.kt` (`LogListener` mapping Log → Sentry events/breadcrumbs; no-ops when the client is off). The `Log` facade (`shared/log/Log.kt`) is JDK-only; `Log.listener` is a single global listener wired only when Sentry is enabled. See the Feature flags section below for the properties.

## Commands

### Development
```bash
./gradlew runIde              # Launch sandbox IDE with plugin loaded
./gradlew build              # Build plugin ZIP in build/distributions/
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
- `FlatpakCommand.RUN` executes the manifest's `command` field (read via the VFS reader in `CommandExecutionEngine`), falling back to the app-id; the deep-clean pre-step (DEEP_CLEAN) runs synchronously as a `PreStep` in `CommandChainProcessHandler` before the main command
- Run command sandbox includes the D-Bus sockets (`DEFAULT_BUS`: `--socket=session-bus`, `--socket=system-bus`) before the opt-in portal/theme/audio/wayland flags and the positional `DIRECTORY MANIFEST COMMAND` args (I2) — but only when `CommandExecutionArguments.hostHasFlatpakBus()` (`/run/flatpak/bus` exists); otherwise the sockets are skipped with a warning and the run relies on flatpak's filtered default session bus (GNOME Builder behaves the same way)
- The deep clean runs inside a `WriteCommandAction` on the pooled thread (`DeepCleanExecutor`), never a raw `runWriteAction` — the EDT would throw "Background write action is not permitted on this thread"
- Factories never emit a blank `buildDir`/`manifestPath` positional arg: `effectiveBuildDir()`/`effectiveManifestPath()` default to `_build`/`flatpak.json` (I1)
- EXPORT/VALIDATE (and I5) fail inside the sandbox IDE for a documented, non-fixable-in-plugin reason: `flatpak-node-generator`/`pip3`/`pipx` are missing from the Builder runtime (`flatpak-builder --run` module-lacks-python issue). The build report's export failure is a sandbox-IDE artifact, not a plugin bug — see README/CHANGELOG
- Configuration requires:
  - `manifestPath`: Path to flatpak manifest file
  - `BUILD_DIR`: Build directory for flatpak-builder

## GNOME/Adwaita UI support
- `.ui`/`.glade` are served a generated **XSD** (no target namespace, root `<interface>`); there is **no bundled schema** — the XSD is generated at runtime from the project's installed GNOME SDK and cached in the plugin config dir.
- The XSD is wired through the XML plugin's `com.intellij.xml.schemaProvider` EP (`XmlSchemaProvider`, see `src/main/kotlin/io/github/andrepg/gtk/schema/providers/GtkInterfaceXmlSchemaProvider.kt`); `.ui`/`.glade` are mapped to the XML file type via `<fileType name="XML" extensions="ui;glade"/>` in `plugin.xml`, so the files open as XML (highlighting, structure view) and get schema completion/validation. Plain `.xml` files are also served when their root element is `<interface>` (matches the schema) **and** the project is a recognized Flatpak project (gated in `getSchema` via the `SdkHint`).
- Until the first successful generation (or when no SDK can be located) **no schema is served**; failures surface through the existing warning balloon.
- LSP integration would require additional dependencies and configuration

## Configuration quirks
- Target IDE version is hardcoded in `build.gradle.kts:15`
- Configuration cache enabled in `gradle.properties:8`
- Kotlin stdlib opt-out in `gradle.properties:5`
- Gradle build cache enabled in `gradle.properties` (`org.gradle.caching=true`); it has served a stale `compileTestKotlin` ABI once after a visibility change — run local verification as `./gradlew clean build --no-build-cache` (CI is unaffected, fresh runner)

## Agent guardrails
- **No internal APIs**: never use `@ApiStatus.Internal` or `@IntellijInternalApi`-annotated platform classes (e.g., `PluginManagerCore`). Prefer pure-JVM alternatives (classpath resources, reflection-free patterns). The `verifyPlugin` task flags these as failures since IntelliJ Platform Gradle Plugin 2.15.0.
- **Save plans locally**: write request plans and task breakdowns into the `plans/` folder **only when the user explicitly asks**.

## Architecture
- Plugin uses IntelliJ's `ConfigurationTypeBase` for run configurations
- Flatpak commands integrate with IntelliJ's `CommandLineState`
- Message bundles in `src/main/resources/messages/` for i18n

## GTK schema namespace
- The GTK/Adwaita schema feature lives under `io.github.andrepg.gtk` (not the Flatpak namespace).
- Shared entry-point helpers at the `gtk` root: `isGtkUiFile(name)` (`.ui`/`.glade` predicate used by the preview factory, the render action and the schema provider) and `GtkNotifications` (balloon helper owning both the `PREVIEW_GROUP_ID` and `SCHEMA_GROUP_ID` notification groups).
- Core (`gtk/schema/`, `gtk/schema/gir/`, `gtk/schema/locator/`) is **JDK-only** (no IntelliJ imports) so it runs inside the IDE and stays unit-testable. `gtk/schema/providers/` is IDE glue and the composition root: it computes the `SdkHint` from `FlatpakManifestVfsReader.readFields(file, "sdk", "runtime")` via `FlatpakProjectDetector.findManifests()`, then delegates to `GtkSchemaManager`.
- `GtkSchemaManager` resolves the project SDK's GIR dir via `GirSdkLocator` (flatpak CLI only, no install-root fallback; discovery is gated by the curated `GirSdkLocator.supportedSdks` allowlist — currently `org.gnome.Sdk`, exposed as the `GirSdkLocator.SDK_APP_ID` constant) and generates `gtk-ui-<key>.xsd` into the plugin config dir (idempotent, background task). This runtime generation is the **only** schema source — there is no bundled fallback.
- The GIR→XSD pipeline is split: `gir/parser/GirParser` parses the GIR files into a `Registry` of types (missing optional GIR files like `GtkSource-5.gir` are skipped with a warning); `gir/builder/XsdBuilder` derives the name enums and applies `SchemaPatches` on top of the static skeleton `gir/builder/XsdSkeleton.RAW` (whose `gb-patch:*` markers must match `SchemaPatches.xsdPatches`); `gir/GirSchemaExtractor.generateXsd` orchestrates it all — callers pass the GIR dir and it fails fast without `Gtk-4.0.gir`. Never run schema generation during app lifecycle outside the background scheduling in `GtkInterfaceXmlSchemaProvider` (which maps progress text/fraction through `gir/GtkSchemaProgressUi`).
- The GTK snapshot preview downloads a precompiled headless renderer from the GitHub releases of `gtk-headless-renderer` (`GtkHeadlessRendererDownloader`, using `/releases/latest/download/` and `.sha256` validation) and caches it under the plugin config dir (`GtkBuilderToolRunner`). It runs headless under a private `MutterHeadlessCompositor` Wayland display. Renders are serialized through `GtkPreviewRenderScheduler` (one in-flight render, coalescing pending triggers on the EDT) and the SDK branch is cached across renders, reusing `GirSdkLocator.pickBranch`. Sandbox invocations go through the shared `FlatpakSandboxRunner`. Host `/tmp` is masked inside the flatpak sandbox, so test/preview files must live under `$HOME` (exposed via `--filesystem=host`). The cache marker is keyed by `GtkHeadlessRendererDownloader.RENDERER_VERSION`.

## Next steps for full implementation
1. Implement LSP for XML files
2. Add proper configuration validation
3. GResource integration and undeclared-file notifications
4. Runtime GTK schema/preview polish

## CI/CD
- `.github/workflows/ci.yml`: on PR/push — `./gradlew build` + `./gradlew test` (GTK tests run as plain JUnit in the same task — the schema integration tests execute a real `FakeFlatpakCli` subprocess).
- `.github/workflows/publish.yml`: on `v*` tag / manual — resolves the release name (`flatpak-devtools@<version>` from `gradle.properties`, fails on tag mismatch), `verifyPlugin`, creates the Sentry release via `getsentry/action-release` (unfinalized + auto commits), then `publishPlugin`, then finalizes the release with a `production` deploy record. Requires the `GH_JETBRAINS_PUBLISH_TOKEN` and `SENTRY_AUTH_TOKEN` secrets (the latter also enables the Gradle plugin's source-context upload).

## Feature flags (runtime system properties)
- `flatpak.gtk.preview.enabled` — enables the GTK preview/schema premium features (also the Marketplace `<with>` property).
- `flatpak.devtools.development` — dev-only premium unlock, set by `runIde`.
- `flatpak.debug` — plugin-wide debug logging: raises the JUL level to `FINE` on the `io.github.andrepg.*` categories (also available as *Settings → Languages & Frameworks → Flatpak DevTools → Diagnostics → Debug logging*). Enabled state lives in `LogConfiguration` (`shared/log/LogConfiguration.kt`).
- `flatpak.sentry.enabled` — opt-in Sentry error reporting (also the *Share anonymous error reports* checkbox in the Diagnostics group). When on, `SentryLogBridge` (a `LogListener`) forwards `Log.error` + throwable to `Sentry.captureException`, plain `Log.error` to `captureMessage(ERROR)`, and warnings to breadcrumbs. The opt-in balloon (`SentryOptInNotification`) Accept action writes `DiagnosticsSettingsState.sentryEnabled` — the same property the *Share anonymous error reports* checkbox uses — and its Forget action sets `sentryInvitationForgotten` so the balloon is never shown again.
- `flatpak.sentry.dsn` — overrides the Sentry DSN; falls back to the `SENTRY_DSN` env var, then the `SentryInitializer.DSN` constant (the production project DSN, SaaS US region — DSNs are public by design, so it is committed). Sentry is SaaS by default; for a self-hosted instance set `SENTRY_DSN` in CI/dev instead. Privacy: `sendDefaultPii=false`, `serverName` and user scrubbed in `beforeSend`, only `io.github.andrepg` frames in-app; environment is `development` under `flatpak.devtools.development`, else `production`; release tag is `flatpak-devtools@<pluginVersion>`. Reconfiguration happens in `DiagnosticsInitializer` (registered as `com.intellij.ide.AppLifecycleListener` in `plugin.xml`) and is re-triggered from `DevToolsSettingsConfigurable.apply()`.