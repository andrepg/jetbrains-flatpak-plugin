# GTK/Adwaita Building Plan (phase 2)

**Date:** 2026-08-15
**Status:** Deferred from the Flatpak v1.0 hardening pass (`TOUCHES_UP.md`). No GTK feature work happens on the v1.0 branch — this document is the single source of truth for the phase-2 GTK track and may be handed to another agent.

---

## 1. Scope

The GTK/Adwaita feature set: schema-aware `.ui`/`.glade` editing, GNOME SDK autodetection, and the GTK snapshot preview. All of it is already shipped, feature-flagged, and behind the premium billing gate — phase 2 is correctness + missing-feature work, not greenfield.

Out of scope: Flatpak run-configuration work (v1.0, see `TOUCHES_UP.md`).

## 2. Hard constraints (do not regress)

1. **JDK-only cores:** `gtk/schema/`, `gtk/schema/gir/`, `gtk/schema/locator/` must keep **zero IntelliJ imports** — they run from the `generateBundledGtkSchema` Gradle task and from inside the IDE. IDE glue lives in `gtk/schema/providers/` and `gtk/preview/ui/`.
2. **Feature flag:** every UI gate goes through `FeatureFlags.FEATURE_FLAG_ENABLE_GTK_PREVIEW` (`flatpak.gtk.preview.enabled`); no inline `System.getProperty` checks.
3. **Billing:** premium = GTK Preview only. Keep `PremiumFeatureGate` as the single decision point and the `<product-descriptor code="PFLATPAKDEV" optional="true"/>` consistent with `BILLING.md`. The dev property `flatpak.devtools.development` (set by `runIde`) must never be removed.
4. **Schema provisioning:** `generateBundledGtkSchema` runs only in CI pre-publish (`pre-publish.yml`, pinned Fedora + GNOME SDK 50) or on demand — never in app lifecycle. Committed bundled artifacts under `src/main/resources/schemas/` are the fallback; runtime per-project generation (`GtkSchemaManager` + `GirSdkLocator`) is the primary path.
5. **XSD, not JSON:** `.ui`/`.glade` are served the generated XSD via `com.intellij.xml.schemaProvider` (`GtkInterfaceXmlSchemaProvider`). The JSON schema feature has no XML support and must never be the completion driver.

## 3. Current state (2026-08-15)

- **Tests:** `AdwShimManagerTest` (3 tests), `GtkBuilderToolRunnerTest` (8), `GtkBuilderToolRunnerHermeticTest` (8) all run green. **One** test is `@org.junit.Ignore("phase 2: ...")`: `AdwShimManagerHermeticTest` — the fake runner's adw-shim detection does not match the compile command paths, so `ensureShim` returns null (1 of its 4 tests). The 10 stale-class GTK failures reported in `TOUCHES_UP.md` §0.3 were incremental-build artifacts cleared by the clean rebuild.
- **Known runtime gap:** Adwaita (`Adw*`) widgets do not render correctly in the preview yet (the `adw_init()` constructor shim works, but rendering correctness is unverified end-to-end).

## 4. Work items (suggested order)

### 4.1 Re-enable the ignored test
- `src/test/kotlin/io/github/andrepg/gtk/preview/AdwShimManagerHermeticTest.kt:39` — fix `ensureShim` detection under the fake runner (the compile command path shape) or make the test assert on the real command shape. Remove the `@Ignore`.

### 4.2 Preview correctness (Adwaita rendering)
- Verify `GtkBuilderToolRunner` render output for `.ui` files using `Adw*` widgets; the `AdwShimManager` per-branch `adw_init()` constructor shim (compiled with `cc`/`pkg-config`, cached in the plugin config dir) must make Adwaita types load without the runtime installed.
- Remember the sandbox quirk: host `/tmp` is masked inside the flatpak sandbox — test/preview files must live under `$HOME` (exposed via `--filesystem=host`).
- Acceptance: a representative Adwaita window renders with header bar, `AdwPreferencesGroup`, etc., not blank/missing widgets.

### 4.3 GResource integration + undeclared-file notifications
- `GtkPreviewEditorNotificationProvider` should flag `.ui` files that are not declared in the project's GResource manifest (`.gresource.xml`), and offer a quick-fix to add the entry.
- Currently listed as WIP in README.

### 4.4 `.ui` schema completion polish
- Tighten the generated XSD against real-world GtkBuilder files (nested `<template>`, custom widgets, `glade`-specific attributes), using `GtkInterfaceXmlSchemaProvider` as the entry point. Regenerate bundled artifacts with `./gradlew generateBundledGtkSchema`.

### 4.5 LSP for XML
- Heaviest item; would require adding a new dependency and its configuration. Explicitly **not** attempted so far. Revisit only after 4.1–4.4.

### 4.6 GNOME 50 schema upkeep
- When the platform SDK bumps, regenerate the bundled fallback in the pinned CI container and commit the drift (auto-commit by `pre-publish.yml`). Runtime per-project generation follows whatever SDK the manifest pins, so this only affects the no-SDK fallback.

## 5. Verification
- `./gradlew clean build --no-build-cache` (never trust the Gradle build cache locally — it served a stale `compileTestKotlin` ABI once; see `AGENTS.md`).
- GTK preview smoke inside `runIde` with `-Dflatpak.gtk.preview.enabled=true` + `-Dflatpak.devtools.development=true` (the latter is set automatically by `runIde`).
- CI gate: `ci.yml` runs the non-GTK suite; GTK tests must not break it once re-enabled (they currently run as part of the suite except the one ignored test).

## 6. Files that matter
- Core (JDK-only): `src/main/kotlin/io/github/andrepg/gtk/schema/**`
- IDE glue: `src/main/kotlin/io/github/andrepg/gtk/schema/providers/GtkInterfaceXmlSchemaProvider.kt`, `src/main/kotlin/io/github/andrepg/gtk/preview/ui/**`
- Preview runner: `src/main/kotlin/io/github/andrepg/gtk/preview/GtkBuilderToolRunner.kt`, `AdwShimManager.kt`
- Bundled artifacts: `src/main/resources/schemas/gtk-ui.xsd` (+ `gtk-ui-schema.json` artifact)
- Tests: `src/test/kotlin/io/github/andrepg/gtk/preview/**`
- Gate: `src/main/kotlin/io/github/andrepg/shared/FeatureFlags.kt`, `shared/license/PremiumFeatureGate.kt`
