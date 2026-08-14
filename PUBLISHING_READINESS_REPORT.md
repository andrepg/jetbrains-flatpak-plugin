# Flatpak DevTools — Exploration & Publishing Readiness Report

**Date:** 2026-08-14
**Author:** opencode (assisted audit + verification)
**Status:** Published for review — target publish date 2026-08-15

---

## 1. Executive summary

The plugin is **functionally complete for a v1 publish** and has passed the IntelliJ
Plugin Verifier against three IDE generations (2025.3, 2026.1, 2026.2) with **no
compatibility errors**. Two hard blockers must be resolved before `publishPlugin` can
succeed (SNAPSHOT version + missing publishing configuration), and two product-quality
items should be decided (the silent no-op portal checkboxes and the stub GTK preview
tool window). All details below.

---

## 2. Scope of the exploration

Full audit of `src/main/kotlin/io/github/andrepg/` — every package:

| Area | Files | Verdict |
|---|---|---|
| Flatpak run configuration | `runs/configuration/*` (options, configuration, factory, type, generator, manifest producer, startup activity) | Sound; runtime crash (null `ConfigurationFactory.getType`) fixed |
| Flatpak command execution | `runs/execution/*` (engine, runner, selection strategy) | Fixed: VALIDATE no-op, app-id fallback, cleanup semantics |
| Flatpak settings | `settings/*` (states + 2 configurables) | Sound; manual wiring avoids DSL binding pitfall |
| Manifest/detection utils | `utils/*`, `detection/*` | Sound |
| GTK schema core | `gtk/schema/`, `gir/`, `locator/` | Sound; reviewed extractor, manager, locator, patches |
| GTK schema providers | `gtk/schema/providers/*`, `shared/*` | Sound; VFS-composition root OK |
| GTK preview | `gtk/GtkUiPreview*.kt` | **Stub/WIP** (see §6) |
| Plugin wiring | `resources/META-INF/plugin.xml`, `messages/*` | Consistent; all referenced classes exist |
| Tests | 7 test classes, 42 tests | All green |

---

## 3. Changes made during the exploration

Fixes applied (all verified by `./gradlew build`):

1. **VALIDATE command was a silent no-op** (`emptyList()`). Now runs
   `flatpak run org.flatpak.Builder --show-manifest <manifest>` — a real manifest
   parse/resolve. — `runs/execution/CommandExecutionEngine.kt`
2. **App-id fallback bug**: `com.foo.Bar.json.split('.').first()` produced `"com"`.
   Now strips the file extension (`com.foo.Bar`). — `CommandExecutionEngine.kt`
3. **Empty command-line guard**: `executeCommandSequence` now throws a descriptive
   `ExecutionException` instead of an opaque "executable name is empty". — `CommandExecutionEngine.kt`
4. **`FlatpakRunner` created two `CommandExecutionEngine`s**; now one instance. — `runs/execution/FlatpakRunner.kt`
5. **Cleanup semantics**: force/deep-clean flags previously prepended a `rm -rf` to
   *every* command (incl. VALIDATE/CUSTOM) and duplicated CLEAN when the main command
   was already CLEAN. Now they apply only to BUILD/RUN/EXPORT. — `runs/execution/CommandSelectionStrategy.kt`
6. **Dead code removed**: `CleanupStrategy.kt` (refactor leftover referencing removed `config.state`).
7. **Stale docs**: `AGENTS.md` and `UiRows.kt` KDoc still referenced deleted classes.

Earlier-session fixes that remain in force (context for the report):
- Run-config persistence (`@Attribute` options), typed `state` accessor, manual
  settings-editor wiring.
- `ConfigurationFactory(type)` ctor + `getOptionsClass()` override (fixed the
  "getType must not return null" crash when adding a run configuration).
- Test suite rebuilt (removed tests for deleted preview classes; Mockito added for
  `GtkSdkHintResolverTest`; `resolveFromManifests` seam).

---

## 4. Verification results

| Gate | Command | Result |
|---|---|---|
| Compile + package + tests | `./gradlew build` | **GREEN** (42/42 tests) |
| Plugin structure/API checks | `./gradlew verifyPlugin` | **GREEN** |
| Binary compatibility 2025.3 (IU-253) | Plugin Verifier | **No errors** |
| Binary compatibility 2026.1 (IU-261) | Plugin Verifier | **No errors** |
| Binary compatibility 2026.2 (IU-262) | Plugin Verifier | **No errors** |
| Dynamic plugin eligibility | Plugin Verifier | Eligible without IDE restart |

Verifier warnings (non-blocking, all pre-existing):
- **7 experimental** + **6 deprecated** API usages, confined to
  `GtkUiPreviewToolWindowFactory` (experimental `ToolWindowFactory` methods) and
  `UiRows.browseTextFieldRow` (`textFieldWithBrowseButton(Project, ...)`).
  Risk: these APIs may change in a future IDE release. No missing classes, no
  `NoSuchMethodError`-class problems in any IDE build.

---

## 5. Plans status (vs. `plans/` folder)

| Plan | Status | Notes |
|---|---|---|
| `flatpak-run-component-redesign.md` | **Implemented** | Enum split, selection strategy, execution engine, runner refactor, config/UI updates, old-code removal — all done |
| `In-flight GTK schema.md` | **Implemented** | Runtime SDK-driven XSD generation, cached per branch, bundled fallback, `GirSdkLocator`, manifest-driven `SdkHint` |
| `flatpak-command-improvements.md` | **Partial** | Cleanup half done (DEEP_CLEAN + force/deep-clean flags ✅). **Portal permission injection not done** — the 4 checkbox flags exist in UI/persistence but are not applied to the run command (see §6). `FlatpakManifestAnalyzer` not created |
| `GTK-schema-full-refactor.md` | **Not executed** | JSON pipeline + `extractGtkSchema` Gradle task + CLI `main()` still present (deliberate; plan awaited approval). Not a publish blocker |
| `GTK UI preview panel.md`, `XML Preview Panel.md` | **WIP** | Preview feature is a stub; not part of v1 feature set |

---

## 6. Known limitations & risks

### Hard blockers (publish will fail / be rejected without these)

1. **SNAPSHOT version** — `gradle.properties` has `version = 1.0.0-SNAPSHOT`.
   JetBrains Marketplace rejects SNAPSHOT versions. Must become e.g. `1.0.0`.
2. **No publishing configuration** — `build.gradle.kts` has no
   `intellijPlatform { publishing { ... } }` block and no token source
   (`PUBLISH_TOKEN` env / `ijPublisherToken` gradle property). `publishPlugin`
   will fail. The plugin id is read from `plugin.xml` `<id>`
   (`io.github.andrepg.flatpak-support`).

### Product-quality items to decide before publish

3. **Portal checkboxes are a silent no-op** — `enablePortals`, `enableThemes`,
   `enableAudio`, `enableWayland` are persisted (`FlatpakRunSettingsAttributes.kt`) and
   rendered as checkboxes in the run-config editor (`FlatpakRunSettingsPanel.kt`)
   but are **never read by `CommandExecutionEngine`**. Users toggling them see no
   effect. Options: (a) wire them into `buildRunCommand` (`--socket=wayland`,
   `--talk-name=org.freedesktop.portal.*`, etc.), or (b) remove the section from the
   UI until implemented.
4. **GTK preview tool window is a stub** — `GtkUiPreviewPanel`/`GtkUiPreviewToolWindowFactory`
   render hardcoded English text (no live preview); it is the source of all
   experimental-API verifier warnings. It is disclosed in `plugin.xml` as
   *"GResource awareness (in progress)"*, so not misleading, but consider hiding the
   tool-window registration until functional to reduce API risk and user confusion.

### Cosmetic / docs (recommended before publish)

5. **README is stale** — still lists **Validate** as *roadmap* (now implemented) and
   **GNOME SDK autodetection** as *WIP* (implemented); Build command example omits
   `--force-clean`.
6. **CHANGELOG.md** has only `[Unreleased]` — add a `1.0.0` entry for the release.

### Working tree

7. **45 uncommitted entries** (staged renames + untracked new files). Must be committed
   before publishing.

---

## 7. Pre-publish checklist (recommended order)

1. [ ] Bump `version` to `1.0.0` in `gradle.properties`
2. [ ] Add publishing config to `build.gradle.kts`:
   ```kotlin
   intellijPlatform {
       publishing {
           token = providers.environmentVariable("PUBLISH_TOKEN")
           channels = listOf("stable")
       }
   }
   ```
3. [ ] Decide portal flags: wire into the RUN command **or** remove the UI section (see §6.3)
4. [ ] Decide GTK preview tool window: keep (disclosed) **or** drop registration (see §6.4)
5. [ ] Refresh README (Validate done, SDK autodetection done, `--force-clean`)
6. [ ] Add CHANGELOG `1.0.0` entry
7. [ ] Commit all 45 working-tree entries
8. [ ] `./gradlew build` → `./gradlew verifyPlugin` → `PUBLISH_TOKEN=… ./gradlew publishPlugin`
9. [ ] Smoke-test the installed ZIP from `build/distributions/` in `runIde` once before upload

---

## 8. Follow-up (post-publish)

- **Billing/pricing scheme report + plan** — to be produced after publishing, covering
  pricing model options (free, one-time, subscription via JetBrains Marketplace),
  tiering, and telemetry/usage considerations. *Explicitly deferred by request.*
- Longer-term roadmap per `plans/`: portal permission injection, `FlatpakManifestAnalyzer`,
  GTK schema full refactor (drop JSON/Gradle pipeline), functional GTK preview,
  GResource awareness.
