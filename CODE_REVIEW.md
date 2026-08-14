# Flatpak DevTools — Codebase, Architecture & Complexity Review

**Plugin**: `io.github.andrepg.flatpak-support` (IntelliJ IDEA 2025.3.5, Kotlin)
**Scope**: full review of the working tree — cyclomatic complexity, code quality, architecture.
**Date**: 2026-08-14

> ⚠️ **Context flag**: this review was written against a working tree that is **mid-refactor**. There is a mixed git state (staged renames/deletions + untracked new files + unstaged edits; `git status` shows the same files both `D` and `??`). Files were being edited during the review (mtimes within minutes of analysis). Two compile errors observed at one point (`CleanupThenProcessHandler` calling non-existent `destroy()`/`detach()`, and `UiRows.expandableTextField(value=…)` against the 2025.3 API) were fixed shortly after, and `./gradlew test` is green again. This churn itself is one of the risk findings (§6.1).

---

## 1. Executive summary

Overall this is a **small, healthy, well-factored codebase**:

- ~4,200 lines of production Kotlin across 39 files; low complexity (mean CC **2.7**, max **12**, only 2 functions ≥ 10).
- Clean domain separation (`flatpak.*`, `gtk.*`, `shared.*`), with a genuinely good "JDK-only core vs. IDE glue" split in the GTK schema feature.
- 69 unit tests, all green (`./gradlew test`), good coverage of the pure logic.
- Clear command pipeline (selection → execution → runner) and correct IntelliJ persistence pattern (`FlatpakRunSettingsAttributes`).

The problems are concentrated, not systemic:

| Severity | Finding |
|---|---|
| 🔴 High | The **"Configurable binaries" settings feature is dead code** — nothing is wired to the persisted state, and every consumer reads hardcoded paths. |
| 🟠 Medium | **Duplication**: 2 byte-identical settings configurables; duplicated SDK-hint resolver; duplicated process runner (`runProcess` + `ProcessResult`). |
| 🟠 Medium | UI strings **hardcoded in English** while the `messages` bundle defines unused keys. |
| 🟠 Medium | Non-hermetic integration tests call `/usr/bin/flatpak` at test time. |
| 🟡 Low | Command-ordering coupling in the runner; repeated full project walks; a couple of race/unbounded-cache issues in the preview panel; shell-string `DEEP_CLEAN`. |

---

## 2. Codebase overview

### 2.1 Size and shape

| Domain | Files | Lines | Purpose |
|---|---|---|---|
| `gtk.schema` | 7 | 1,573 | GIR→JSON/XSD schema extraction, SDK location, schema manager (mostly JDK-only) |
| `flatpak.runs` | 13 | 983 | Run configuration, command selection/execution, settings editor |
| `gtk.preview` | 7 | 738 | GtkBuilder snapshot preview (shim, runner, panel, tool window) |
| `shared.license` | 2 | 329 | Marketplace license verification + premium gate |
| `flatpak.settings` | 4 | 199 | Settings state + configurables |
| `flatpak.schemas` | 2 | 115 | Flatpak JSON manifest schema provider |
| `flatpak.detection` / `utils` | 2 | 166 | Manifest detection & reading |
| `shared` (UI/localization) | 2 | 127 | Shared UI rows, message bundle access |
| **Total main** | **39** | **4,230** | |
| **Tests** | 12 | ~1,100 | 69 tests, all passing |

### 2.2 Runtime wiring (plugin.xml)

Registered: `FlatpakRunSettingsType`, `RunManifestProducer` (right-click manifest), `RunPostStartupDetection` (project-open detection), `GtkInterfaceXmlSchemaProvider` (`xml.schemaProvider`), `FlatpakSchemaProviderFactory` (JSON schema), `GtkPreviewToolWindowFactory`, `GtkPreviewService` (projectService), `GtkPreviewEditorNotificationProvider`, one notification group, one `projectConfigurable` — and the auxiliary `standardResource` for the XSD.

**Missing**: there is **no `<applicationService>`/`<projectService>` for `FlatpakGlobalSettingsState`** — see §4.2.

### 2.3 Build/test facts

- `./gradlew test` → **69 tests, 0 failures, 0 skipped** (verified with `cleanTest test --no-build-cache`).
- Build is green after a transient mid-refactor break (§6.1).
- `build.gradle.kts` hardcodes the target IDE `intellijIdea("2025.3.5")`; `buildSearchableOptions = false` is set because a paid plugin cannot be scanned headless.

---

## 3. Cyclomatic complexity

### 3.1 Methodology

Heuristic scanner over string/comment-masked Kotlin sources (so XSD/XML literals and doc comments don't skew results). CC = 1 + `if`/`for`/`while`/`do`/`catch`/`when` + `&&`/`||`/`?:`/`!!` + `when`-branch arrows. This is an approximation (lambdas can inflate the arrow count), but it is stable and good enough to rank hotspots. Ref. McCabe, "A Complexity Measure" (1976) — thresholds used below: 1–5 low, 6–10 moderate, 11–20 high, >20 very high.

### 3.2 Global statistics

| Metric | Production | Tests |
|---|---|---|
| Functions analyzed | 131 | 79 |
| Mean CC | **2.7** | 1.7 |
| Max CC | 12 | 9 |
| CC ≥ 10 | 2 | 0 |
| CC ≥ 15 | 0 | 0 |

**Verdict**: no function in this codebase is a genuine maintainability emergency. Only two functions cross CC 10, and both are small, mechanical codecs/validators.

### 3.3 Hotspots (production)

| CC | LOC | Location | What it is | Why it's hot / fix |
|---|---|---|---|---|
| 12 | 15 | `GtkSchemaExtractor.kt:157` `Js.writeString` | JSON string escaper | 8-arm `when` over control chars inside a `for`. Replace with a `Map<Char,String>` lookup or `Char.toString()` + escape table. |
| 11 | 17 | `GtkInterfaceXmlSchemaProvider.kt:64` `getSchema` | Schema resolution pipeline | Chains `isAvailable`, sdk hint, cache, background-generation scheduling, URL/VFS/PSI fallbacks in one method. Extract `resolveGenerated()`, `resolveBundled()`, `toXmlFile(url)` and the generation-side-effect out of a "getter". |
| 9 | 30 | `FlatpakRunner.kt:77` `validateConfiguration` | Run-config validation | 6 sequential `if { throw }` blocks + a warn. Extract a `RunConfigurationValidator` (returning a list of errors) or use `error()`/require-style guards. Also move it to Apply time, not `startProcess` (see §6.4). |
| 8 | 20 | `SchemaPatches.kt:302` `replaceAt` | Recursive JSON-path walker | `when` on path head with null-safety returns at every step. Refactor to a sealed `PathSegment` or a `getOrNull`-style safe traversal. |
| 8 | 18 | `CleanupThenProcessHandler.kt:50` `runChain` | Cleanup chain orchestration | Branches for cancellation/failure/termination propagation. Acceptable for a state machine; add unit tests around the states (currently untested — see §5). |
| 8 | 11 | `SchemaPatches.kt:161` `xmlEscape` | XML escaper | 5-arm `when`; same trivial fix as `writeString` (shared escape table would remove the duplication too). |
| 8 | 9 | `CommandSelectionStrategy.kt:53` `mapUserCommandToInternal` | enum → enum mapping | The CC is purely an exhaustive `when` over 6 values — **not a real problem**. The real issue is that the mapping is ~100% identity and adds an indirection layer (§4.4). |
| 7 | 22 | `GtkSchemaExtractor.kt:133` `Js.write` | JSON renderer | `when` over sealed class; can move the traversal into the sealed subclasses (visit methods) to flatten it. |
| 7 | 18 | `GtkBuilderToolRunner.kt:40` `resolveBranch` | SDK branch resolution | Nested null/pinned/installed fallbacks. Extract the "pinned vs best" decision (it overlaps `GirSdkLocator.pickBranch`). |
| 7 | 8 | `LicenseCheck.kt:133` `isLicensed` | Stamp routing | Fine; `when` over prefixes. Leave as is. |

### 3.4 Per-domain averages

| File | Avg CC | Max | Hotspots |
|---|---|---|---|
| `CommandSelectionStrategy.kt` | 6.0 | 8 | `mapUserCommandToInternal` (mechanical) |
| `SchemaPatches.kt` | 5.2 | 8 | `replaceAt`, `xmlEscape` |
| `FlatpakRunner.kt` | 4.0 | 9 | `validateConfiguration` |
| `LicenseCheck.kt` | 4.0 | 7 | `isLicensed` |
| `GtkInterfaceXmlSchemaProvider.kt` | 3.8 | 11 | `getSchema` |
| `GirSchemaExtractor.kt` | 3.5 | 12 | `writeString`, `write` |
| `CleanupThenProcessHandler.kt` | ~3.3 | 8 | `runChain` |

Everything else is ≤ 2.8 average. The **flatpak runs** domain and the **GIR extractor** are the two areas that would benefit from any complexity refactor; the preview/UI code is already low.

---

## 4. Architecture review

### 4.1 What's good

1. **JDK-only core / IDE-glue split (GTK schema)** — `gtk/schema/gir`, `gtk/schema/locator`, `GtkSchemaManager`, `SdkHint` have no IntelliJ imports, so the same `GirSchemaExtractor` runs from the `extractGtkSchema` Gradle task and inside the IDE, and is unit-testable without the platform. This is the best architectural decision in the codebase.
2. **Composition roots** are clearly identifiable: `GtkInterfaceXmlSchemaProvider` (schema) and `GtkPreviewService` (preview) own cross-domain wiring (`FlatpakProjectDetector` → `SdkHint` → `GtkSchemaManager`/`GtkBuilderToolRunner`), keeping domains decoupled.
3. **Command pipeline** is separated into "what" (`CommandSelectionStrategy`), "how" (`CommandExecutionEngine`), and orchestration (`FlatpakRunner`), each testable in isolation.
4. **Persistence** uses the correct platform pattern: `RunConfigurationOptions : BaseState` with `@Attribute` annotations + `ConfigurationFactory.getOptionsClass()`.
5. Small classes, descriptive KDoc, one concern per class. The `BranchResolution` sealed hierarchy and `ValidationResult`/`RenderResult` result types are good modeling.

### 4.2 🔴 The settings subsystem is disconnected (biggest finding)

`FlatpakGlobalSettingsState` is a `BaseState` persistence holder with default paths, and two configurables edit it — **but**:

- It is **never registered** in `plugin.xml` as `<applicationService>` (only `<projectConfigurable>` is registered, and it references `FlatpakSettingsConfigurable`).
- The two configurables (`FlatpakSettingsConfigurable`, `FlatpakGlobalSettingsConfigurable`) are **byte-identical except the class name** and both operate on a private, ephemeral `FlatpakGlobalSettingsState()` instance — nothing they write is ever read back.
- Every actual consumer reads the **hardcoded constants**: `CommandExecutionEngine.kt:23-24` (`DefaultFlatpakPaths.MAIN_BINARY`/`BUILDER_BINARY`), `GtkPreviewService.kt:22`, `GtkInterfaceXmlSchemaProvider.kt:114`.

**Consequence**: the "Configurable binaries" feature advertised in `plugin.xml` and README is non-functional. Users can type a custom `flatpak` path in Settings and it is silently ignored.

**Fix path**: (1) register `FlatpakGlobalSettingsState` as an `<applicationService>`; (2) have the configurable hold the *service instance* (`state.applicationService` / `projectService` pattern), not a new instance; (3) delete one of the two duplicate configurables; (4) inject the paths into `CommandExecutionEngine`, `GtkPreviewService` and the schema provider instead of reading `DefaultFlatpakPaths` directly. Keep `DefaultFlatpakPaths` only as the *default values*.

### 4.3 Duplication hotspots

| Duplicate | Locations | Fix |
|---|---|---|
| SDK-hint resolution (incl. `splitBranch`) | `GtkInterfaceXmlSchemaProvider.sdkHint` (`:99-110`) vs `GtkSdkHintResolver.resolve` (`:20-47`) | Delete the private copy in the provider and call `GtkSdkHintResolver.resolve(project)`. Two sources of truth will drift. |
| Process runner (`runProcess` + `ProcessResult`) | `GtkBuilderToolRunner.kt:126-158` vs `AdwShimManager.kt:78-110` | Extract a JDK-only `FlatpakProcessRunner`/`CommandProcessRunner` in a shared location (e.g. `gtk.preview` or a `shared.process` package) with injectable timeout; reuse in both. |
| Settings configurables | `FlatpakSettingsConfigurable.kt` == `FlatpakGlobalSettingsConfigurable.kt` | Delete one (see §4.2). |
| Manifest-name heuristics | `FlatpakProjectDetector.kt:19-24` (`reverseDnsNameRegex`, `commonNameRegex`, **requires ≥3 dotted segments**) vs `FlatpakJsonSchemaProvider.kt:26-33` (`manifestAppIdRegex`, **requires ≥2 segments**, different common-name set) | Share one candidate-name predicate. Today `org.a.b.json` is a manifest for the JSON provider but not for the detector, and vice versa for `manifest.yml`. |
| Escape tables (`writeString` vs `xmlEscape`) | `GirSchemaExtractor.kt:157` / `SchemaPatches.kt:161` | One shared escape-map util (also kills the top-2 CC hotspots). |

### 4.4 Command selection / ordering coupling

`CommandSelectionStrategy` maps the user-visible enum to the internal enum via a **1:1 identity `when`**, then prepends CLEAN/DEEP_CLEAN and `sortedBy { priority }`. `FlatpakRunner.kt:46` then re-filters with `commands.last { it != CLEAN && it != DEEP_CLEAN }` to find the "main" command.

Problems:
- The identity mapping adds a level of indirection with no behavioral value.
- Ordering is split across three mechanisms (the prepend logic, the `priority` sort, and the `last{}` filter in the runner). `InternalCommand.DEEP_CLEAN` exists as a *command* but has no user-visible counterpart — it's really an option flag, modeled as a command.
- The `last{}` call is an exception if a config were ever produced with only cleanup commands.

**Suggested redesign**: model the selection as `data class CommandPlan(val main: InternalCommand, val preSteps: List<PreStep>)` with `PreStep = FORCE_CLEAN | DEEP_CLEAN`, and have `FlatpakRunner` iterate `preSteps` then run `main`. This removes `priority`, the `last{}` filter, and the `sortedBy` in one move, and makes the plan unit-testable without reimplementing sort semantics.

### 4.5 Performance: repeated full-project walks

`FlatpakProjectDetector.findManifests(project)` recursively visits the whole content-root tree (skipping only 7 directory names) on **every** call:

- `GtkInterfaceXmlSchemaProvider.sdkHint` → called per schema request (per open file / refresh),
- `GtkSdkHintResolver.resolve` → called by the editor notification provider per editor,
- `RunPostStartupDetection` → once per project open.

For a large project this is an O(files) pass per editor event. Recommend: cache by project + content-root modification stamp (`VfsUtil.markDirty`/`FileContentUtilCore` are overkill; a simple `ContentIterator` result cached on the VFS file-timestamp of the manifest files, or a project-level `CachedValue<...>`, suffices). Also `readSdk`/`readRuntime` re-parse the manifest file twice (and `findManifests` already read the app-id) — batch all fields into one read.

### 4.6 Domain boundaries worth preserving

- `gtk.schema` correctly **imports** `flatpak.detection` (SDK hint) but `gtk.schema.providers` should be the *only* place that does; currently `GtkPreviewService`/`GtkPreviewEditorNotificationProvider` also reach into `GtkSdkHintResolver` — fine, that is the agreed composition-root pattern. Keep the *core* (`gtk/schema/gir|locator`, `GtkSchemaManager`, `SdkHint`) free of platform imports — it's the project's crown jewel; do not regress it.
- The JDK-only *preview core* (`GtkBuilderToolRunner`, `AdwShimManager`) is also clean; keep it that way.

---

## 5. Testing assessment

**69 tests, all green**, but distribution is uneven:

| Area | Tests | Notes |
|---|---|---|
| GTK schema (extractor, locator, manager, hint resolver, provider) | 32 | Excellent — fixture GIR files in `test-data/gir`, JDK-only logic. |
| flatpak runs (selection, execution, detector, manifest reader) | 21 | Good; command-line assertions are exact (catch regressions well). |
| License/gate | 6 | **Weak** — `isLicensed()` returns `null` in headless tests, so tests 3, 5, 6 are near-tautologies ("null is not true"). The private `isKeyValid`/`isLicenseServerStampValid` paths are untested. |
| **Preview** | 10 | **Non-hermetic**: `GtkBuilderToolRunnerTest` and `AdwShimManagerTest` shell out to `/usr/bin/flatpak` + the installed GNOME SDK at test time. They pass only on machines with flatpak + `org.gnome.Sdk//50` and will break CI. |
| Cleanup handler / runner chain | 0 | `CleanupThenProcessHandler` (the EDT-workaround state machine, CC 8) has **no tests**. |
| Settings / configurables | 0 | Untested — consistent with the feature being dead (§4.2). |
| UI panels | 0 | No headless/UI tests for `FlatpakRunSettingsPanel`, `GtkPreviewPanel` logic (the `updateStatus` `when`, `renderIfGatePassed`). |

Recommendations:
1. Split the two preview tests into *hermetic* tests (inject a fake `ProcessRunner`; assert command-lines and result mapping) + an opt-in integration tag (`@Tag("integration")`/Gradle condition) for the real flatpak calls.
2. Add tests for `CleanupThenProcessHandler` state transitions (cleanup fail → terminate 1, cancel mid-cleanup, main-command fail, timeout) using a stubbed `ProcessRunner`.
3. Make `FlatpakRunner`'s plan (proposed in §4.4) and `validateConfiguration` unit-testable (currently private, side-effecting).
4. If you keep the settings feature, add a test that the configurable round-trips through the real service.

---

## 6. Code-quality findings by category

### 6.1 Repository/build hygiene (fix first)

- **Mixed git state**: same paths appear as staged `D`/`R` and untracked `??` (e.g. `FlatpakRunSettings.kt`, `FlatpakRunner.kt`, `execution/`). One careless `git add -A && commit` will double-delete or resurrect files. Commit the refactor as a single coherent change.
- The build **was broken during this review** (`destroy`/`detach` on `ProcessHandler`, and the `expandableTextField(value=…, converter=…)` API that doesn't exist in 2025.3.5 — the platform signature is `expandableTextField(parser, joiner)`). Both were fixed minutes later. Lesson: run `./gradlew build` before finishing a refactor; consider a pre-commit hook.
- Consider adding **detekt** (complexity + style rules) now — at 4.2k LOC it's cheap and would have caught both issues.

### 6.2 Correctness / robustness

- **`DEEP_CLEAN` is shell string-interpolation** (`CommandExecutionEngine.kt:56-60`): `rm -rf ${config.buildDir}` inside a `bash -c` string. Any space/special char in `buildDir` breaks or misbehaves; in principle it's command injection from project config. `CLEAN` already does the safe `["rm","-rf",path]` list form — make DEEP_CLEAN match it (two `rm` invocations or a safe list of paths).
- **Late validation**: `validateConfiguration` runs at `startProcess` (run time), so a bad manifest path surfaces as a run error, not in the editor. Add a `ConfigurationErrorCollector` in the settings editor so the Apply button/run dialog flags it early.
- **`getAppId` fallback** (`CommandExecutionEngine.kt:139`) splits on `'/'` only — wrong on Windows; use `Path`/`File(name)`.
- **`GtkPreviewPanel.renderIfGatePassed` race**: `service.render(currentFile!!, …)` (`:149`) uses `!!` on a field that the debounce/editor tracking can null between the async validate and render. Guard with a local snapshot (`val file = currentFile ?: return`).
- **Unbounded validation cache** in `GtkPreviewService` (`cache: ConcurrentHashMap`, keyed with `#timeStamp`) never evicts; long sessions with many edited files leak memory. Cap it (e.g. `LinkedHashMap` LRU or clear on file delete).
- **Daemon threads in `CleanupThenProcessHandler.streamForwarder`** are not joined; after `destroyForcibly` the reader threads may race `notifyTextAvailable` on a terminating handler. Track and await them (or use `CapturingProcessHandler` per step on a worker thread).
- `GtkBuilderToolRunner`/`AdwShimManager` swallow **all** process failures to `null` — acceptable for discovery, but the preview validate path then shows "Validation failed" with no root-cause. Consider logging the `IOException`.

### 6.3 i18n (inconsistent)

`Messages.properties` defines `preview.notification.*` keys that are **unused**, while the UI hardcodes English:

- `GtkPreviewPanel.kt`: `"No preview"`, `"Select a .ui or .glade file"`, `"Validating..."`, `"Rendering..."`, `"Render failed"`, `"Validation failed"`, `"Adwaita types unsupported"`.
- `GtkPreviewEditorNotificationProvider.kt:51,55,61,68`: `"Adwaita types unsupported"`, `"GTK Preview"`, `"Upgrade to unlock Preview"`, `"Validation failed"`.
- `RunConfigurationSettingsPanel.kt:55-86`: checkbox labels/comments are literal strings (group headings "Command"/"Manifest"/… too).
- `UpgradePanel.kt`: literal strings.
- `FlatpakRunner`/`CommandExecutionEngine` throw messages are literal (acceptable for errors, but `ExecutionException` messages surface in the UI).

Either localize everything through the bundle (recommended; the bundle already has the shape) or delete the unused keys. Half-localized UI is the worst state.

### 6.4 Misc

- `CommandExecutionEngine` still logs "Executing command sequence" even when it doesn't; `flatpakBinaryPath`/`flatpakBuilderCommand` are vals that should come from the (future) settings.
- `GirSdkLocator.runProcess` has a **10s timeout**; `GtkBuilderToolRunner`/`AdwShimManager` **120s**; constants differ per class — centralize.
- `GtkSchemaManager.generateSchema` catches all exceptions and returns null silently; a debug log would help support.
- `SdkHint` branch split (`//`) is duplicated in `GtkInterfaceXmlSchemaProvider` and `GtkSdkHintResolver` (covered in §4.3) — also the Flatpak manifest format allows `sdk: org.gnome.Sdk//50`, but `FlatpakManifestReader` never parses the branch; only the ad-hoc `splitBranch` does.
- `build.gradle.kts` hardcodes IDE version and disables searchable options with good rationale; consider parameterizing the version via `gradle.properties` for easier upgrades.

---

## 7. Prioritized improvement roadmap (hotspots → changes)

Priority ordering by (impact / effort):

### P0 — finish the refactor safely (do this before anything else)
1. **Stabilize the tree**: one coherent commit of the `runs` refactor; `./gradlew build` + `./gradlew test` gate.
2. Fix the `CleanupThenProcessHandler` thread-join issue (§6.2) while the file is fresh.

### P1 — high impact, moderate effort
3. **Wire up the settings subsystem** (§4.2): register `FlatpakGlobalSettingsState` as `applicationService`, delete the duplicate configurable, inject paths into `CommandExecutionEngine`, `GtkPreviewService`, `GtkInterfaceXmlSchemaProvider`. This turns a shipped-but-dead advertised feature into reality.
4. **Dedup the SDK-hint resolver** (§4.3): `GtkInterfaceXmlSchemaProvider` delegates to `GtkSdkHintResolver`. Touches the CC-11 `getSchema` too.
5. **Extract the shared process runner** (§4.3) and make preview tests hermetic + integration-tagged (§5).

### P2 — good hygiene
6. **Localize UI strings** (§6.3) — the bundle already has keys.
7. **Share one escape table** → collapses the two CC-12/8 hotspots in `GirSchemaExtractor`/`SchemaPatches` (§3.3).
8. **`CommandPlan` refactor** (§4.4) — removes `priority`/`last{}` coupling and makes the chain unit-testable.
9. **Cache manifest detection** (§4.5) and **cap the preview validation cache** (§6.2).

### P3 — nice-to-haves
10. Move `validateConfiguration` to editor time; add `ConfigurationErrorCollector` (P1-lite, listed here because it needs the settings editor).
11. Add detekt/ktlint; fix `DEEP_CLEAN` shell usage; Windows-safe `getAppId` fallback.
12. Better tests for `LicenseCheck` crypto paths (test the verifier directly with sample signed keys) and for `CleanupThenProcessHandler`.

### Quick wins ranked by pure complexity score
| Change | CC units removed | Location |
|---|---|---|
| Escape-map table (dedupe + simplify) | 20 | `GirSchemaExtractor.kt:157`, `SchemaPatches.kt:161` |
| `getSchema` extraction | 11 | `GtkInterfaceXmlSchemaProvider.kt:64` |
| Validator extraction | 9 | `FlatpakRunner.kt:77` |
| `replaceAt` safe-traversal | 8 | `SchemaPatches.kt:302` |
| `resolveBranch` delegation | 7 | `GtkBuilderToolRunner.kt:40` |

---

## 8. Conclusion

The codebase is in **better shape than typical plugin projects its size**: complexity is low, the core/glue separation is excellent, and the pure logic is well tested. The three things that most need attention are (1) **finishing and committing the in-flight `runs` refactor**, (2) **repairing the disconnected settings subsystem**, and (3) **removing the concrete duplication** (resolver, process runner, configurables, escape code) — after which the remaining hotspots are small, mechanical cleanups rather than architectural ones.
