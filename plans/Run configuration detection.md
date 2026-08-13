# Plan: Flatpak run-configuration detection & suggestion (Composer-style)

## Goal
Detect Flatpak manifests in a project and suggest run configurations the way PHPStorm handles Composer: right-click a manifest -> **Run 'Build `<app-id>`'**, and on project open -> balloon *"Flatpak project detected"* with an opt-in **Create Run Configurations** action.

## Domain separation (file placement)

Dependency direction is one-way: `runs -> detection`. The `detection` domain stays pure (no run/UI deps); all glue that creates run configs lives in `runs`. No package cycles.

```
io.github.andrepg.flatpak
├── detection/                        # Domain: manifest/project detection - PURE, zero deps
│   └── FlatpakProjectDetector.kt     #   NEW: scan, heuristics, isFlatpakManifest(file): String?, isFlatpakProject(project)
├── runs/                             # Domain: run configs + project-open glue - depends on detection
│   ├── FlatpakRunConfigurator.kt     #   NEW: programmatic creation via RunManager + dedupe
│   ├── FlatpakManifestProducer.kt    #   NEW: LazyRunConfigurationProducer (right-click suggestions)
│   ├── FlatpakProjectOpener.kt       #   NEW: ProjectOpenListener + balloon + opt-in action
│   ├── FlatpakRunConfiguration.kt    #   unchanged
│   ├── FlatpakRunConfigurationType.kt #  EDIT: factory.isApplicable(project) -> detector
│   ├── FlatpakRunExecutor.kt / FlatpakRunState.kt / FlatpakRunStateMachine.kt / enums/ / ui/   # unchanged
├── schemas/ settings/ utils/         # unchanged (utils.FlatpakManifestReader reused as-is)
```

Rationale: the producer, configurator, and opener all exist to create/manage run configurations, so they belong to `runs`; the detector is a reusable pure service consumed by all three. The opener is the composition root for the project-open flow (detect -> notify -> create).

## Steps

1. **`detection/FlatpakProjectDetector.kt`** (new, pure Kotlin + platform file APIs, no execution deps)
   - `isFlatpakManifest(virtualFile): String?` — extension in `{json,yaml,yml}` **and** filename matches reverse-DNS `^[a-z0-9]+(\.[a-z0-9]+){2,}\.(json|ya?ml)$` or starts with `flatpak`/`manifest` -> then validate with existing `FlatpakManifestReader.readAppId()`, return appId or null.
   - `findManifests(project): List<Pair<VirtualFile, String>>` — recursive VFS walk of `ProjectRootManager.contentRoots`, skipping excluded dirs (`.git`, `build`, `_build`, `out`, `node_modules`, `target`, `.flatpak-builder`). Content is read only when the filename already matches.
   - `isFlatpakProject(project): Boolean` — `findManifests().isNotEmpty()`.

2. **`runs/FlatpakRunConfigurator.kt`** (new)
   - `createForManifest(project, file, appId): RunnerAndConfigurationSettings` — via `RunManager.createConfiguration("Build $appId", factory)`; set `command = FlatpakCommand.BUILD`, `manifestPath = file.path`, `buildDir = FlatpakRunSettings.DEFAULT_OUTPUT`.
   - Dedupe: before `addConfiguration`, skip if any existing config of `FlatpakRunConfigurationType` has the same `manifestPath`; return the existing one.

3. **`runs/FlatpakManifestProducer.kt`** (new) — `LazyRunConfigurationProducer<FlatpakRunConfiguration>()`, implement `DumbAware`
   - `getConfigurationFactory()` -> configurator's factory.
   - `setupConfigurationFromContext(config, context)` — resolve `VirtualFile` from `context.location` (fallback `CommonDataKeys.VIRTUAL_FILE`); return `false` if `FlatpakProjectDetector.isFlatpakManifest(file)` is null; else set `manifestPath`, name `"Build $appId"`, return `true`. (Use the modern 2-arg signature — the `Ref<PsiElement>` overload was removed years ago.)
   - `isConfigurationFromContext(config, context)` — `config.manifestPath == context file path`.

4. **`runs/FlatpakProjectOpener.kt`** (new) — `ProjectOpenListener`
   - `projectOpened(project)`: return early if `PropertiesComponent.getInstance(project)` flag `io.github.andrepg.flatpak.notificationShown` is set. Run `FlatpakProjectDetector.findManifests` in a `ProgressManager` background task; on EDT, if manifests found: set the flag and show balloon (NotificationGroup, BALLOON) with body naming found manifests and a `NotificationAction("Create Run Configurations")` that calls `FlatpakRunConfigurator.createForManifest` per manifest.
   - Uses `PropertiesComponent` for the "shown once" state — no new persistent state class, `settings/` domain untouched.

5. **`plugin.xml`**
   - Add `<depends>com.intellij.modules.lang</depends>` (lang-api: producer base class).
   - Add `<runConfigurationProducer implementation="io.github.andrepg.flatpak.runs.FlatpakManifestProducer"/>`.
   - Add `<projectOpenListener implementation="io.github.andrepg.flatpak.runs.FlatpakProjectOpener"/>`.
   - Add `<notificationGroup id="io.github.andrepg.flatpak.detection" displayType="BALLOON"/>`.

6. **`FlatpakRunConfigurationType.kt`** — override `ConfigurationFactory.isApplicable(project)` to return `FlatpakProjectDetector.isFlatpakProject(project)` so the Flatpak type is hidden in the Edit Configurations dialog for non-Flatpak projects. If `isApplicable` doesn't exist in 2025.3 (compile-check), drop this step.

7. **`Messages.properties`** — add keys: `detection.notification.title`, `detection.notification.body` (with manifest list param), `detection.notification.action.create`, `runs.configuration.build.name=Build {0}`.

8. **Tests** — new `src/test/kotlin/io/github/andrepg/flatpak/detection/FlatpakProjectDetectorTest.kt` mirroring `FlatpakManifestReaderTest` style: filename heuristic (reverse-DNS match/mismatch, `flatpak`/`manifest` prefixes, extension filtering) reusing `test-data/valid-manifest.json`. Pure JUnit, no IDE deps.

## Hand-offs / explicit non-goals
- Do **not** refactor `FlatpakRunConfiguration` to `LocatableConfigurationBase` in this task (name-tracking polish); hand off to the refactor agent if desired.
- Do **not** touch executor/state machinery, schemas, or settings domains.

## Risks
- `ConfigurationFactory.isApplicable(Project)` availability in 2025.3 — verify at compile time; drop step 6 if absent.
- Missing `com.intellij.modules.lang` dependency — add explicitly (json module may pull it transitively, but don't rely on it).
- Scan cost on large repos — background task + strict filename pre-filter; never read content unless filename matches.
- Notification spam on every open — `PropertiesComponent` "shown once" flag.
- Producer API signature drift — use the 2-arg `setupConfigurationFromContext(config, context)`.

## Validation
- `./gradlew test` — detector heuristics tests pass.
- `./gradlew verifyPlugin` — plugin.xml extension/EP checks pass.
- `./gradlew runIde` manual: open a repo with `org.example.App.json` -> balloon appears once; action creates "Build org.example.App"; right-click the manifest -> Run 'Build org.example.App'; Edit Configurations hides Flatpak type in a non-Flatpak project; daemon log free of `PluginException`.
