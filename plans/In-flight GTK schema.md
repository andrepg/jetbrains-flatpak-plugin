# Plan: GtkBuilder schema from the current GNOME SDK (runtime in-flight generation)

## Goal
Generate the GtkBuilder `.ui` JSON schema from the **current GNOME SDK installed via Flatpak**, generate it **in-flight at IDE runtime**, cache it, and fall back to a bundled schema when no SDK is installed. Include GTK 4, Libadwaita and GtkSource-5 classes, properties and signals.

## Current state (what exists)
- Extractor: `../scripts/src/main/kotlin/io/github/andrepg/flatpak/schema/GirSchemaExtractor.kt` — 805-line Kotlin, JDK-only, parses GIR files, flattens inheritance across namespaces, emits JSON Schema (draft-07) + XSD.
- Gradle task `extractGtkSchema` in `../build.gradle.kts` (`scripts` source set) — works, default GIR path hardcoded to `~/.local/share/flatpak/runtime/org.gnome.Sdk/x86_64/50`.
- Generated artifacts committed: `../src/main/resources/schemas/gtk-ui-schema.json` (468 KB, valid, 563 class variants), `gtk-ui.xsd`.
- **Broken**: `GtkInterfaceSchemaProviderFactory.getProviders()` and `GtkInterfaceSchemaProvider.getSchemaFile()` still throw `FeatureNotImplementedException` → `JsonSchemaService` logs SEVERE `PluginException` in the IDE (see `~/.gradle/daemon/*/daemon-*.out.log`). Provider still serves the wrong remote schema (`GTK_MANIFEST_PATH`) and matches `.json/.yaml/.yml` instead of `.ui`.

## Decisions (confirmed)
1. **Runtime generation + bundled fallback**: plugin discovers the installed SDK at runtime, generates lazily on first `.ui` file, caches keyed by SDK branch; bundled committed schema is the fallback when no SDK is present.
2. **Include `GtkSource-5.gir`** (optional-with-warning if missing; `Gtk-4.0.gir` mandatory).
3. **Shared JDK-only core** used by both the Gradle task and the plugin (no IntelliJ imports in the core).

## SDK discovery chain (`FlatpakSdkLocator`, new)
> Use FlatpakPaths to point to flatpak binary, using FlatpakRunStateMachine with custom arguments.

1. `flatpak list --runtime --columns=application,branch,installation` (ProcessBuilder, timeout ~10s, use the flatpak binary from plugin settings / `FlatpakPaths.MAIN_BINARY`) → rows for `org.gnome.Sdk` → pick **highest numeric branch** (tie → user install).
2. `flatpak info --show-location org.gnome.Sdk//<branch>` → `<loc>/files/share/gir-1.0` (verify `Gtk-4.0.gir` exists).
3. Fallback (no binary / CLI fails): `{~/.local/share, /var/lib}/flatpak/runtime/org.gnome.Sdk/x86_64/<branch>/active/files/share/gir-1.0`.
4. Return `null` if nothing found.

## Steps
1. **Move core to main module**: move `GirSchemaExtractor` (parsing, registry, flattening, JSON/XSD rendering) to `src/main/kotlin/io/github/andrepg/flatpak/schemas/gir/GirSchemaExtractor.kt`; keep a `main()` entry point. Add `GtkSource-5.gir` to the GIR file list. JDK + stdlib imports only.
2. **Add `FlatpakSdkLocator`** in `schemas/gir/` (discovery chain above) + unit tests parsing mocked `flatpak list` output.
3. **Add `GtkSchemaManager`** in `schemas/`: resolution order — (a) cached `PathManager.getPluginsConfigDir()/schemas/gtk-ui-<branch>.json` matching current branch; (b) discover SDK on a pooled thread, generate, write cache; (c) copy bundled `/schemas/gtk-ui-schema.json` to the config dir (instant first open, replaced by generated file on later opens).
4. **Fix the stubs**:
   - `GtkInterfaceSchemaProviderFactory.getProviders()` → `listOf(GtkInterfaceSchemaProvider(project))`, no throw.
   - `GtkInterfaceSchemaProvider` → `SchemaType.schema`; `getSchemaFile()` delegates to `GtkSchemaManager`; `isAvailable()` matches `*.ui` (case-insensitive) + GtkBuilder `.xml` (`<interface` root sniff); remove `GTK_MANIFEST_PATH` const and stale imports.
   - **First**: verify in the sandbox IDE that `JsonSchemaFileProvider` is actually applied to `.ui`/`.xml` files. If not, fallback is `XmlSchemaProvider` + the generated `gtk-ui.xsd` (report back).
5. **Simplify build**: drop the `scripts` source set and its stdlib hack; `extractGtkSchema` runs main output + stdlib; default GIR dir = `FlatpakSdkLocator` auto-detection, `-PgirDir=` still overrides.
6. **Regenerate committed artifacts** from the current SDK (50) incl. GtkSource-5; add a tiny `test-data/gir/` fixture (2–3 classes across namespaces) to test cross-namespace flattening.

## Risks
- IntelliJ JSON-schema-on-XML support is unverified — step 4 gates the wiring.
- Runtime generation latency (~1–3 s DOM parse) — lazy, background, cached.
- flatpak CLI output variance — glob fallback is version-independent, CLI failure non-fatal.
- Cache staleness — branch in filename; best-effort cleanup of old branch files.

## Validation
- `./gradlew extractGtkSchema` regenerates → `git diff --exit-code` clean; spot-check `GtkSourceView`/`GtkSourceBuffer` present.
- `./gradlew build` + unit tests pass.
- `runIde`: open a `.ui` fixture → daemon log free of `PluginException`; status bar shows "GTK/Adwaita Interface"; generated file appears in config dir; fallback works with a bogus flatpak binary path.

## Notes
- Working tree contains unrelated uncommitted refactoring — keep schema commits scoped.
- Optional follow-up (not in scope): GitHub Actions workflow (`flatpak/setup-flatpak` → install `org.gnome.Sdk//<branch>` → `extractGtkSchema` + freshness diff).
