# Flatpak DevTools — Touches Up Plan (v1.0 Flatpak module)

**Date:** 2026-08-15
**Status:** Phase 0 + Phase B (hardening) implemented and committed (`6220f8a` baseline → `4842271`). Part A runtime verification (sandbox IDE) done; its findings are fixed in §3.11 and pending one final commit. GTK phase 2 is deferred to `GTK_BUILDING_PLAN.md` (§4). This document is the single source of truth for the v1.0 hardening pass. It may be handed to another agent; every step references exact files.

---

## 1. Goal & strategy

- **Goal:** establish the **entire Flatpak module** as a clean, testable, loosely coupled domain and **release v1.0**. GTK/Adwaita work is explicitly deferred to **phase 2**.
- **Strategy principles (agreed):**
  1. **IO policy:** IDE-glue code uses the **IntelliJ VFS**; **JDK-only cores keep `java.io.File`** (VFS needs the platform, which would break `extractGtkSchema`/standalone tooling and hermetic tests). JDK-only cores today: `gtk/schema/gir|locator`, `GtkSchemaManager`, `SdkHint`, `shared/process`, `shared/log`, `shared/license`.
  2. **Custom plugin exceptions:** small domain hierarchy, wrapped at module boundaries, user-friendly messages.
  3. **GTK is behind a feature flag** (`shared/FeatureFlags.kt` → `FEATURE_FLAG_ENABLE_GTK_PREVIEW`), consolidated — no duplicated inline checks.
  4. **Bundled GTK schema provisioning** (`extractGtkSchema`) is restored, renamed `generateBundledGtkSchema`, documented, and run in **CI pre-deploy (auto-regenerate + commit)** — never in app lifecycle. Runtime per-project schema generation (`GtkSchemaManager`) stays as the phase-2 primary path.
  5. **README + plugin.xml description** rewritten to reflect this strategy.
  6. **Git tree committed as one coherent change** first (mixed staged `D`/`R`/`AD` states are dangerous).

---

## 2. Phase 0 — Stabilize baseline (do first)

### 0.1 `Log.kt` small touches
`src/main/kotlin/io/github/andrepg/shared/log/Log.kt`

- Remove `import com.intellij.util.applyIf` (line 3) — it is the **only IntelliJ import remaining in the JDK-only cores** and violates the class's documented JDK-only contract (`GirSdkLocator`, `GtkSchemaManager`, `CertificateGenerator` use `Log`).
- Simplify the listener dispatch in `log()` (lines 37-52): replace the `listener?.applyIf(listener != null, { return try { this.onLog(...) } catch ... })` construction (double null-check + non-local `return try`; `this` is the `LogListener` receiver, not `Log`) with:
  ```kotlin
  private fun log(level: Level, message: String, throwable: Throwable?) {
      if (!jdk.isLoggable(level)) return
      jdk.log(level, message, throwable)
      val l = listener ?: return
      try {
          l.onLog(category, level, message, throwable)
      } catch (e: Exception) {
          if (jdk.isLoggable(Level.FINE)) {
              jdk.log(Level.FINE, "LogListener failed for $category", e)
          }
      }
  }
  ```
- Public API unchanged (`Log.getInstance`, `Log.listener`, `isDebugEnabled`, `info/warn/error/debug`). `LogListener` (`shared/log/LogListener.kt`) unchanged.

### 0.2 Settings-delegate test fixes
Root cause: SDK `NormalizedStringStoredProperty` normalizes `"" → null`; getters fall back to defaults (`flatpak.json`, `_build`). Two stale test classes:

- **Delete** `src/test/kotlin/io/github/andrepg/flatpak/runs/ui/RunConfigurationOptionsProbeTest.kt` — leftover debug probe (PRINTs `PROBE`, asserts `""` stays `""`).
- **Rewrite** `src/test/kotlin/io/github/andrepg/flatpak/runs/execution/RunConfigurationValidatorTest.kt`:
  - `blank manifest path is reported` → `default manifest path is reported`: config without `manifestPath` set → assert `errors.any { it.contains("Manifest file not found") }`.
  - `all errors are collected in one call` → reachable 2-error combo: `manifestPath = "does-not-exist.json"` + `buildDir = File(File.createTempFile(...), "sub").path` (parent is a file → `mkdirs()` fails deterministically) → assert `errors.size == 2` and both messages.
  - Remove the `System.err.println("DEBUG blank-test errors=...")` line (line 45).
  - Keep `valid configuration has no errors` and `missing manifest file is reported` unchanged.

### 0.3 Clean build + test gate
- `./gradlew clean test --console=plain` — the `NoSuchMethodError` GTK failures (`AdwShimManagerTest` ×3, `GtkBuilderToolRunnerTest` ×6, `AdwShimManagerHermeticTest` ×1) are stale incremental-build artifacts; a clean rebuild should clear most.
- Any GTK failure that **still** fails after clean → tag `@org.junit.Ignore("phase 2: ...")` with a reason. Non-GTK suite must be green.
- Then `./gradlew build`.

### 0.4 Coherent git commit
- Commit the refactored tree as **one** change. Mixed staged states present (e.g. `AD` `CommandExecutor.kt`, `AD` `XmlPreviewPanel.kt`, `R` `SystemProperties.kt → FeatureFlags.kt`, `D` `DeepCleanCommandFactory.kt`, `D` `CleanupThenProcessHandlerTest.kt`). Stage deliberately (`git add` specific paths), never `git add -A` blindly.

---

## 3. Phase B — Flatpak v1.0 hardening

### 3.1 Feature-flag consolidation
- All GTK gates must go through `FeatureFlags.getBoolean(FeatureFlags.FEATURE_FLAG_ENABLE_GTK_PREVIEW, default)`.
- Replace the duplicated inline checks:
  - `gtk/preview/ui/GtkPreviewService.kt:43-45` (`System.getProperty("flatpak.gtk.preview.enabled", "false").toBoolean()`)
  - `gtk/preview/ui/GtkPreviewEditorNotificationProvider.kt:47-49` (same inline re-implementation)
  - `gtk/preview/ui/GtkPreviewToolWindowFactory.kt:51-52` already uses `FeatureFlags` — keep.
- `plugin.xml` `<with><property name="flatpak.gtk.preview.enabled" value="true"/>` (line 100) stays — that is how the Marketplace enables the premium feature.
- Keep billing descriptor (`product-descriptor code="PFLATPAKDEV" optional="true"`) consistent with BILLING.md (premium = GTK Preview only).

### 3.2 IO policy — VFS in IDE glue
- **Split `FlatpakManifestReader`** (`flatpak/utils/FlatpakManifestReader.kt`, uses `java.io.File` readText/exists/isDirectory at lines 61-64):
  - **Pure parser** (JDK-only): take `content: String` + extension, return fields map. Unit-testable without platform. Keep `Gson`/`snakeyaml`.
  - **VFS loader** (IDE glue): resolve `VirtualFile` via `LocalFileSystem`/`VfsUtil`, read bytes/`InputStream` (or `FileDocumentManager`), delegate to the parser. Needs a `Project` or `VirtualFile` argument.
  - Update callers: `detection/FlatpakProjectDetector.kt`, `runs/execution/commands/RunCommandFactory.kt`, `gtk/schema/providers/GtkSdkHintResolver.kt`.
- `DeepCleanExecutor.kt` already deletes via VFS (`LocalFileSystem` + `runWriteAction { virtualFile.delete(this) }`) — keep.
- `runs/execution/RunConfigurationValidator.kt` stays **pure JDK** (deliberate, documented exception — VFS would force platform tests for pure existence/writability checks).
- `shared/process/ProcessRunner.kt` keeps `java.io.File` for `ProcessBuilder.directory` (JDK requirement).
- Document the policy in `AGENTS.md`.

### 3.3 Custom plugin exceptions
- New hierarchy under `io.github.andrepg.flatpak.exception` (or `shared.exception`):
  - `FlatpakPluginException : RuntimeException` (base)
  - `FlatpakManifestException` — manifest read/parse failures
  - `FlatpakExecutionException` — command/process failures (wrap `ExecutionException` at `CommandExecutionEngine`)
  - `FlatpakConfigurationException` — invalid run-configuration state
- Wrap at boundaries with user-facing messages; keep platform contracts (`RuntimeConfigurationError` in `FlatpakRunSettings.checkConfiguration`, line 90) intact. Do NOT throw IntelliJ exceptions from JDK-only cores.

### 3.4 User-reported issue list (v1.0 blockers — all in the Flatpak module) — DONE, except live panel rename (deferred, §3.11 B7 covers the dialog)

#### I1. BUILD does not pass `buildDir` → "missing manifest" error — DONE (effective* guards)
Reported: the BUILD run fails with "missing manifest" because `buildDir` arrives null/empty.
Code path today:
- `FlatpakRunner.startProcess()` (`runs/execution/FlatpakRunner.kt:39`) → `engine.buildCommand(strategy, config)`.
- `CommandExecutionEngine.buildCommand` (`:32-38`) → `BuildCommandFactory.create` → `buildList { getFlatpakCommand(); if(forceClean && command==BUILD) FORCE_CLEAN; add(settings.buildDir); add(settings.manifestPath) }` → `[flatpak, run, org.flatpak.Builder, <buildDir>, <manifestPath>]`.
- `settings.buildDir` getter (`FlatpakRunSettings.kt:34-36`) returns `flatpakState.buildDir ?: "_build"` — attribute default is `"_build"` (`FlatpakRunSettingsAttributes.kt:13`), so it should never be literally null.
- `FlatpakRunGenerator.createForManifest` sets `buildDir = FlatpakRunSettingsAttributes().buildDir ?: "build"` (`FlatpakRunGenerator.kt:55`).
Diagnosis steps:
1. Run `./gradlew runIde`, create a BUILD config, run it, and read `FlatpakRunner`'s info log ("Flatpak run started: ... buildDir=...") + the generated command line (`CommandExecutionEngine` debug line).
2. Check the `RunManifestProducer` path (`setupConfigurationFromContext` only sets `manifestPath`) — the LazyRunConfigurationProducer-created config may bypass `createForManifest` defaults.
3. Verify positional order accepted by `org.flatpak.Builder` (`flatpak-builder DIRECTORY MANIFEST`).
Acceptance: BUILD runs with an explicit build dir; console shows `--force-clean`/dir/manifest correctly positioned.

#### I2. D-Bus not available in the app at Run time — DONE (DEFAULT_BUS, host-bus-guarded, §3.11 B2)
Reported: app started via **RUN** cannot connect to D-Bus.
Code path: `RunCommandFactory.create` (`runs/execution/commands/RunCommandFactory.kt`) → `buildSandboxOptions` (`commands/CommandFactory.kt:34-39`) only adds portals/themes/audio/wayland flags (`CommandExecutionArguments`, all opt-in). **No bus sockets are ever added.**
Fix direction: add default bus wiring to the RUN sandbox, e.g. `--socket=session-bus` (+ `--socket=system-bus` if desired) in `CommandExecutionArguments` (new `DEFAULT_BUS` set) added unconditionally by `RunCommandFactory` before the opt-in flags (must appear before the `DIRECTORY MANIFEST COMMAND` positional args). Verify against the README's sandbox feature list and update docs/messages accordingly.
Acceptance: app launched via RUN sees the session bus (e.g. `dbus-run-session` / `gdbus` probe in the manifest command).

#### I3. Hide the `CustomArguments` field behind a new feature flag (hidden by default) — DONE, superseded by §3.11 B4/B6 (flag removed; field is now command-sensitive)
Files:
- `runs/ui/FlatpakRunSettingsPanel.kt`: `customArgumentsField` row is `expandableTextFieldRow(...)` at lines 126-129 inside the `advanced` group; also referenced at line 30 (field), 42 (`textFields`), 168 (`resetEditorFrom`), 188-191 (`applyEditorTo`).
- New flag in `shared/FeatureFlags.kt`, e.g. `FEATURE_FLAG_SHOW_CUSTOM_ARGUMENTS = "flatpak.runs.show-custom-arguments"` (default false).
- Hide the row unless the flag is on; keep the field wired (write path) so toggling the flag reveals an already-working editor. Simplest: skip adding the row when flag off, keep `customArgumentsField` lateinit satisfied for the write path (or guard reads/writes).
Acceptance: field invisible by default; visible when `-Dflatpak.runs.show-custom-arguments=true`.

#### I4. Suggest run-configuration name from `[command] appId` — DONE (generator/producer naming), extended to the New-config dialog in §3.11 B7
Requested template: `[build] io.github.andrepg.Doit`, `[run] io.github.andrepg.Doit` → `[<command>] <appId>`.
Files:
- `runs/configuration/FlatpakRunGenerator.kt:50` names configs via `Localization.message("runs.configuration.build.name", appId)` (`createForManifest`).
- `runs/configuration/RunManifestProducer.kt` sets only `manifestPath` (producer path).
- `runs/ui/FlatpakRunSettingsPanel.kt` `commandComboBox` listener (line 142) — option to update the suggested name live.
- Message bundle: `src/main/resources/messages/Messages.properties` (`runs.configuration.build.name`; add e.g. `runs.configuration.name=[{0}] {1}`).
Fix direction: introduce a name factory (`formatRunName(command, appId)`) used by the generator (name = `[build] <appId>`) and, if cheap, update the settings' name on command change in the panel via `fireEditorStateChanged` + a name suggestion mechanism (`SettingsEditor` has no built-in rename; use the run manager rename when editing — evaluate scope; minimal viable = generator + producer naming only).
Acceptance: right-click create yields `[build] org.example.App`; produced names follow the template.

#### I5. VALIDATE requires pip3/pipx — DONE (documented sandbox artifact, no code change; EXPORT exhibits the same via §3.11 B3)
Reported: VALIDATE (`--show-manifest`) errors demanding pip3/pipx — **likely a JetBrains test-IDE sandbox artifact** (the sandbox IDE lacks a full flatpak/Builder SDK). Code path: `ValidateManifestCommandFactory` → `flatpak run org.flatpak.Builder --show-manifest <manifest>`.
Action: investigate once (try in the dev sandbox); if it is purely the sandbox, document in README/AGENTS and do **not** change code. No code fix expected unless the error reproduces on a normal IDE.

### 3.5 Docs hygiene
- Remove the stale **"RUN Command: build output path is incorrect / app does not run"** known issue from `README.md` (line 95) and `plugin.xml` `<description>` (lines 35-36). Confirmed stale — no longer true.
- Fix all stale `extractGtkSchema` references (`README.md:73`, `AGENTS.md`, `GirSchemaExtractor.kt` comments) → `generateBundledGtkSchema`.

### 3.6 i18n + domain polish
- Localize the remaining hardcoded run-panel strings via the existing bundle (`src/main/resources/messages/Messages.properties`, `shared/Localization.kt`): check `FlatpakRunSettingsPanel.kt` (group/checkbox labels already use keys — verify none are literal) and `UpgradePanel.kt` (GTK phase-2, defer).
- Dedup the two manifest-name heuristics: `FlatpakProjectDetector.kt:19-24` (≥3 dotted segments) vs `FlatpakJsonSchemaProvider.kt:26-33` (≥2) — share one predicate (affects what is detected as a manifest).
- Cache `FlatpakProjectDetector.findManifests(project)` (full content-root walk per call; called by `RunPostStartupDetection`, producer, GTK provider) — simple project-level cache keyed by manifest VFS stamps. (Optional P2 if time-boxed.)
- Remove the stale "Executing command sequence" log in `CommandExecutionEngine` if still misleading.

### 3.7 `generateBundledGtkSchema` task (restored + renamed)
- CLI exists: `GirSchemaExtractor.main` (`gtk/schema/gir/GirSchemaExtractor.kt:799-800`) — `--gir-dir`/`--schema-out`, auto-detect installed GNOME SDK, writes `gtk-ui-schema.json` + sibling `gtk-ui.xsd` (defaults `src/main/resources/schemas/`). `extract(girDir, output)` at line 773.
- Register the Gradle task in `build.gradle.kts`: `JavaExec` calling the CLI `main`, properties `-PgirDir=`, `-PschemaOut=`; output check that the committed bundled artifacts are in sync.
- Update README Development (`./gradlew generateBundledGtkSchema`) + AGENTS.md with "where and how it is used": it provisions the **bundled fallback** (GNOME 50 basic support) for the phase-2 runtime schema feature; runtime per-project generation is the primary path.

### 3.8 CI/CD (new — GitHub Actions)
- `.github/workflows/ci.yml`: on PR/push — `./gradlew build`, `./gradlew test` (non-GTK gate; tagged GTK tests excluded), optional detekt/ktlint later.
- `.github/workflows/pre-publish.yml`: on tag/publish — job in an SDK container (Debian/Fedora + flatpak + `org.gnome.Sdk//50`): run `generateBundledGtkSchema`, **auto-commit** any drift in `src/main/resources/schemas/`; then `verifyPlugin` + `publishPlugin` (needs `PUBLISH_TOKEN`). Per agreed decision: auto-regenerate, not drift-fail.
- Remove `SNAPSHOT` versioning before publish (check `gradle.properties`/`CHANGELOG.md`).

### 3.9 README rewrite
- v1.0 strategy section: Flatpak module core; GTK gated behind feature flag and slated for phase 2 (schema completion for `.ui` files + GTK preview listed as "in development / feature-flagged").
- Roadmap split: v1.0 (Flatpak) vs phase 2 (GTK: preview polish, GResource, LSP).
- Accurate Development commands (incl. `generateBundledGtkSchema`), CI badges, updated Known Issues (drop stale RUN issue; note VALIDATE sandbox quirk).
- Update `plugin.xml` `<description>` to match (stale RUN issue + accurate feature framing).

### 3.10 Part A — runtime verification (sandbox IDE build report) — DONE
`runIde` smoke run on the committed `4842271` tree, using the `/tmp` flatpak-sample project (build → run → export sequence). Findings:

| # | Finding | Root cause | Resolution |
|---|---------|-----------|------------|
| 1 | Console showed only the final build report; the deep-clean and build steps were invisible | Pre-steps and the main command are separate process handlers; nothing announces the transition | §3.11 B1 |
| 2 | RUN fails: `bwrap: Can't find source path /run/flatpak/bus: No such file or directory` | I2's `DEFAULT_BUS` added the bus sockets unconditionally, but the sandbox IDE host has no `/run/flatpak/bus` | §3.11 B2 |
| 3 | EXPORT fails: `flatpak-node-generator`/`pip3`/`pipx` missing in the Builder runtime | Sandbox-IDE artifact (same class as I5): `flatpak-builder --run` module lacks python tooling | §3.11 B3 (document only) |
| 4 | Portals panel visible for every command, not just RUN | `FlatpakRunSettingsPanel` shows the portals group unconditionally | §3.11 B4 |
| 5 | BUILD deep clean: `SEVERE - Background write action is not permitted on this thread. Consider using 'backgroundWriteAction'` | `DeepCleanExecutor.delete` calls `ApplicationManager.runWriteAction` on the pooled chain thread (`DeepCleanExecutor.kt:47` ← `FlatpakRunner.kt:44` ← `CommandChainProcessHandler.runChain`) | §3.11 B5 |
| 6 | CUSTOM: the custom-arguments field is nowhere in the default UI (flag off), yet it is the only way to pass custom args | I3's flag hides it; no command-sensitive placement exists | §3.11 B6 |
| 7 | *Run → Edit Configurations → New* name is the generic template, not `[command] <app-id>` | `FlatpakRunSettings` lacks `LocatableConfiguration` | §3.11 B7 |

### 3.11 Part B — runtime fixes (build-report follow-ups) — DONE

**B1. Console workflow-step visibility.** `execution/CommandChainProcessHandler.kt` now takes `commandLabels: List<String>` + `preSteps: List<PreStep>` and announces every step: `Running DEEP_CLEAN...`, `Running BUILD: <cmdline>`, `<label> failed; aborting.`, `<label> finished with exit code N`. `FlatpakRunner` labels the chain with the selected command's name and routes the deep clean through a `PreStep("DEEP_CLEAN")`. Test: `CommandChainProcessHandlerTest.kt` (named args + `PreStep`).
Acceptance: BUILD run shows `Running DEEP_CLEAN...` then `Running BUILD: flatpak ...` then the report; deep-clean failure is visible and aborts before the build.

**B2. D-Bus sockets only when the host has a Flatpak bus.** Research: GNOME Builder (`gbp-flatpak-runner.c`) does **not** pass `--socket=session-bus`/`system-bus` — it relies on flatpak's filtered-default session bus (docs: unfiltered sockets are for development tools). Applied as a Builder-aligned fallback, not a full filter:
- `CommandExecutionArguments.hostHasFlatpakBus() = File("/run/flatpak/bus").exists()` (KDoc on `DEFAULT_BUS` updated).
- `RunCommandFactory` gains `hostBusAvailable: () -> Boolean = CommandExecutionArguments::hostHasFlatpakBus`; adds `DEFAULT_BUS` only when true, else `log.warn` and continues.
- `CommandExecutionEngine` passes the predicate through (constructor seam, default wired), so tests inject `{ true }`/`{ false }`.
- Tests: engine test constructs `CommandExecutionEngine(project) { true }`; new test `run command skips dbus sockets when the host has no flatpak bus` (`{ false }`).
Acceptance: RUN on a host without `/run/flatpak/bus` no longer crashes with the `bwrap` source-path error; a warning is logged and the app still starts.

**B3. EXPORT known issue (document only).** Same sandbox-IDE artifact as I5: `flatpak-builder --run` module lacks `flatpak-node-generator`/`pip3`/`pipx`. README Known Issues, AGENTS.md Flatpak notes, CHANGELOG updated. No code change.

**B4. Portals panel is RUN-only.** `FlatpakRunSettingsPanel` gates the portals group to the RUN command (same mechanism as the cleanup group).

**B5. Deep clean on a background thread via `WriteCommandAction`.** `DeepCleanExecutor.delete` (was `DeepCleanExecutor.kt:47`) replaced `ApplicationManager.getApplication().runWriteAction { virtualFile.delete(this) }` with `WriteCommandAction.writeCommandAction(project).withName("Deep clean").run<RuntimeException> { ... }`. Verified (javap on `idea-2025.3.5` `app.jar`, `WriteCommandAction.BuilderImpl.run`): background threads go through `invokeAndWait` to the EDT — safe; only holding a read lock while calling throws. `project` is now passed into `delete()`; the SAM is `ThrowableRunnable<RuntimeException>`, so `IOException` is caught inside the lambda via a holder var (checked after the write action returns).
Acceptance: BUILD with deep clean enabled no longer logs the `Background write action is not permitted` SEVERE; the pre-step deletes and the build proceeds.

**B6. Custom arguments under the command box, command-sensitive.** `FEATURE_FLAG_SHOW_CUSTOM_ARGUMENTS` removed from `FeatureFlags.kt`. The field now lives in the command group right below the command combo (inline `row(...)` + `rowComment` + `RowLayout.LABEL_ALIGNED`; `expandableTextFieldRow` returns a `Cell`, not a `Row`, so the row is built inline to capture it). `updateCommandSensitiveVisibility()` (called in `init` and from the combo's action listener, which also `fireEditorStateChanged()`s) shows: cleanup group for BUILD, portals group for RUN, custom-arguments row (`.visible` + `.enabled`) for CUSTOM. `customArgumentsField` is non-nullable `lateinit`.
Acceptance: selecting CUSTOM reveals the field right under the combo; switching away hides it; other groups toggle accordingly.

**B7. `LocatableConfiguration` for the New-config dialog.** `FlatpakRunSettings : ... , LocatableConfiguration`:
- `isGeneratedName()` — name matches `^\[[a-z]+\] .+$` (the generated template).
- `suggestedName()` — `FlatpakRunGenerator.formatRunName(command, appId ?: manifest base name)` from `flatpakState.flatpakManifest` via `FlatpakManifestVfsReader.readAppId`; returns null when no manifest is configured (dialog falls back to empty name).
- `checkConfiguration()` drops the `super` call (ambiguous with two interface supertypes; the default is a no-op).
Acceptance: Run → Edit Configurations → New suggests `[build] <app-id>`; renaming sticks (name no longer matches the regex); the producer/right-click path is unchanged.

**Docs pass.** AGENTS.md (Key files: `CommandChainProcessHandler`, `CommandExecutionStrategy`, host-bus-guarded D-Bus, panel command-sensitivity, `LocatableConfiguration`, removed `show-custom-arguments` flag; Flatpak notes: deep clean via `WriteCommandAction`, EXPORT/VALIDATE sandbox quirk, build-cache `--no-build-cache` warning; Next steps → pointer to `GTK_BUILDING_PLAN.md`). README (D-Bus conditional wording, custom-arguments placement, EXPORT known issue, checklist). CHANGELOG `[Unreleased]`. `plugin.xml` untouched by B (no new EPs/flags).

**Verification.** `./gradlew clean build --no-build-cache` (build-cache served a stale `compileTestKotlin` ABI once — always local-verify with `--no-build-cache`); 95 tests green, GTK tests `@Ignore`'d. Sandbox smoke (pending user): BUILD w/ deep clean (B1+B5), RUN (B2), EXPORT (B3 artifact), portals/custom-args toggles (B4+B6), New-config name (B7).

---

## 4. GTK — deferred to `GTK_BUILDING_PLAN.md` (phase 2)
- Phase C GTK work (re-enable 10 `@Ignore` tests, preview correctness/Adwaita, GResource + undeclared-file notifications, LSP for XML, `.ui` schema polish, GNOME 50 schema upkeep) is tracked separately in **`GTK_BUILDING_PLAN.md`** and is out of scope for the v1.0 Flatpak release.
- No GTK code changes during phase B beyond the flag consolidation in §3.1 (done).

---

## 5. Reference map (files likely touched)
| File | Work |
|---|---|
| `src/main/kotlin/io/github/andrepg/shared/log/Log.kt` | §0.1 |
| `src/main/kotlin/io/github/andrepg/shared/FeatureFlags.kt` | §3.1, §3.11 B6 (flag removed) |
| `src/main/kotlin/io/github/andrepg/gtk/preview/ui/GtkPreviewService.kt`, `GtkPreviewEditorNotificationProvider.kt` | §3.1 |
| `src/main/kotlin/io/github/andrepg/flatpak/utils/FlatpakManifestReader.kt` (+ new VFS loader) | §3.2 |
| `src/main/kotlin/io/github/andrepg/flatpak/detection/FlatpakProjectDetector.kt` | §3.2, §3.6 |
| `src/main/kotlin/io/github/andrepg/flatpak/runs/execution/commands/RunCommandFactory.kt` | §3.2, §3.4 I2, §3.11 B2 |
| `src/main/kotlin/io/github/andrepg/flatpak/runs/execution/commands/CommandFactory.kt`, `CommandExecutionArguments.kt`, `CommandExecutionEngine.kt` | §3.4 I1/I2, §3.11 B2 |
| `src/main/kotlin/io/github/andrepg/flatpak/runs/execution/CommandChainProcessHandler.kt`, `FlatpakRunner.kt`, `DeepCleanExecutor.kt` | §3.11 B1/B5 |
| `src/main/kotlin/io/github/andrepg/flatpak/runs/ui/FlatpakRunSettingsPanel.kt` | §3.4 I3/I4, §3.11 B4/B6 |
| `src/main/kotlin/io/github/andrepg/flatpak/runs/configuration/FlatpakRunGenerator.kt`, `RunManifestProducer.kt`, `FlatpakRunSettings.kt` | §3.4 I4, §3.11 B7 |
| `src/main/resources/messages/Messages.properties` | §3.4 I4, §3.6 |
| `src/test/kotlin/.../RunConfigurationValidatorTest.kt` (delete probe test) | §0.2 |
| `src/test/kotlin/.../CommandChainProcessHandlerTest.kt`, `CommandExecutionEngineTest.kt` | §3.11 B1/B2 |
| `build.gradle.kts` | §3.7 |
| `.github/workflows/ci.yml`, `.github/workflows/pre-publish.yml` | §3.8 |
| `README.md`, `AGENTS.md`, `CHANGELOG.md`, `src/main/resources/META-INF/plugin.xml` | §3.5, §3.7, §3.9, §3.11 docs |
| `GTK_BUILDING_PLAN.md` (new, committed) | §4 |
| New: `flatpak/exception/*` | §3.3 |

## 6. Risks & notes
- Clean rebuild is slower but required to kill the stale-class GTK failures (§0.3) — and always use `--no-build-cache` locally: `org.gradle.caching=true` served a stale `compileTestKotlin` ABI once (visibility change) and produced a misleading `NoSuchMethodError` in tests.
- Auto-regenerate CI needs a **pinned SDK container** to be deterministic.
- Do not regress the `@Service`-based settings state (`FlatpakGlobalSettingsState` + `FlatpakSettings` + `CommandFactory` wiring) or the `<with>` premium property while consolidating flags.
- §3.11 B7 intentionally leaves the live-rename-in-the-panel stretch goal out of scope — `LocatableConfiguration` covers the New-config dialog; renaming afterwards is a normal user action.
- The JDK-only / VFS boundary is the project's crown jewel (`gtk/schema/**` has zero IntelliJ imports) — do not regress it.
- Part A findings that are sandbox-IDE artifacts (B3 EXPORT / I5 VALIDATE) are documented, not fixed — do not chase `pip3`/`pipx` inside `flatpak-builder --run`.
