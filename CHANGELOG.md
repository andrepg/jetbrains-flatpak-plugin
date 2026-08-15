<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Flatpak DevTools Changelog

## [Unreleased]

## [2026.1.1] - 2026-08-15

### ✨ Added

- **JetBrains Marketplace freemium licensing** — `product-descriptor` (`PFLATPAKDEV`, `optional`) with plugin-side license verification via `LicensingFacade`
- **Premium gating** — GTK Preview tool window and in-editor *Preview* action available to licensed users; unlicensed users see upgrade panel/action opening JetBrains registration dialog
- **Publishing configuration** — `PUBLISH_TOKEN` environment variable for `./gradlew publishPlugin`
- **Dual-license** — AGPLv3 for GitHub source, commercial license for Marketplace binary with `LICENSE` file
- **Diagnostics infrastructure** — opt-in Sentry error reporting (*Settings → Languages & Frameworks → Flatpak → Diagnostics*) wiring `Log` → `SentryLogBridge` (exceptions, plain errors, and warning breadcrumbs; self-hosted instance supported via `flatpak.sentry.dsn`/`SENTRY_DSN`); Debug logging toggle (`-Dflatpak.debug=true` or Diagnostics checkbox) raises `io.github.andrepg.*` log level to `FINE`
- **Flatpak v1.0 hardening pass:**
  - Custom plugin exceptions (`FlatpakPluginException` + manifest/execution/configuration subtypes) wrapping command and process failures at engine boundary
  - IDE-glue manifest reads go through IntelliJ VFS (`FlatpakManifestVfsReader`); pure-JDK `FlatpakManifestReader.parseFields(content, ...)` stays for tooling/hermetic tests
  - `DEFAULT_BUS` sandbox flags: Run command exposes session and system D-Bus sockets when host Flatpak bus exists (`/run/flatpak/bus`), skipping with warning otherwise (GNOME Builder-style filtered default bus)
  - `generateBundledGtkSchema` Gradle task for regenerated bundled GTK/Adwaita schema, provisioned in CI pre-publish
  - GitHub Actions: `ci.yml` (build + tests on PR/push) and `pre-publish.yml` (schema regeneration + `verifyPlugin` + `publishPlugin` on release)
  - Run-configuration suggestions on *Run → Edit Configurations → New* (`[command] <app-id>`) via `LocatableConfiguration.suggestedName()`
- **GTK snapshot preview** — render `.ui`/`.glade` files to an image inside the GNOME SDK (`gtk4-builder-tool`) with editor notification and *GTK Preview* tool window
- **Adwaita compatibility shim** — Adwaita types render even without the Libadwaita runtime installed
- **Portal sandbox flags on Run command** — portals, themes & icons, audio, Wayland injected as `flatpak-builder --run` sandbox options
- **Cleanup options** — Force clean and Deep clean run as synchronous pre-steps before build/run/export
- **Manifest command execution** — Run command executes the manifest's `command` (falling back to the app-id)

### 🔧 Changed

- Version scheme switched to calendar versioning (`2026.1.1`) to match paid-plugin `release-version` requirements
- README documents the free/premium split and the licensing model
- Run configurations named `[build] <app-id>` (template `[{0}] {1}`) via `FlatpakRunGenerator.formatRunName`
- Build directory/manifest path never blank on command line (`effectiveBuildDir()`/`effectiveManifestPath()` default to `_build`/`flatpak.json`)
- Manifest-name heuristics unified: `FlatpakProjectDetector.isCandidateName` now shared with the JSON schema provider
- Feature flags consolidated: GTK gates all use `FeatureFlags`; Custom arguments row un-flagged, shown below command box only when Custom command is selected
- Run-configuration editor option groups toggle live with selected command: cleanup for Build, portal permissions for Run, custom arguments for Custom
- Console output shows each workflow step (`Running DEEP_CLEAN...`, `Running BUILD: <cmdline>`, `<label> finished with exit code N`) instead of only final build report
- Deep clean runs inside `WriteCommandAction` (no longer throws "Background write action is not permitted on this thread" on pooled thread)
- Run now uses `flatpak-builder --run` (run-only) instead of rebuilding, fixing invalid `--force-clean` flag on run and broken app-id-as-command invocation
- Commands no longer flattened into single process line; each command maps to its own process
- README roadmap and command reference refreshed to match implemented behavior

### 🐛 Fixed

- Invalid `--force-clean` on `flatpak-builder --run` mode
- Run failing with `bwrap: execvp <app-id>: No such file or directory` by executing the manifest `command`
- Cleanup commands being flattened into single dangerous command line with `rm -rf`

### ⚠️ Known Issues

- **GTK Preview** — Adwaita interfaces are not yet rendered correctly
- **VALIDATE/EXPORT** — require `pip3`/`pipx`/`flatpak-node-generator` in sandbox IDE (JetBrains test-IDE artifact; not reproducible on normal IDE)
