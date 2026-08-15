<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Flatpak-support Changelog

## [Unreleased]

### Added

- Diagnostics infrastructure: opt-in **Sentry** error reporting (*Settings → Languages & Frameworks → Flatpak → Diagnostics*) wiring `Log` → `SentryLogBridge` (exceptions, plain errors, and warning breadcrumbs; self-hosted instance supported via `flatpak.sentry.dsn`/`SENTRY_DSN`, see AGENTS.md) and a **Debug logging** toggle (`-Dflatpak.debug=true` or the Diagnostics checkbox) that raises the `io.github.andrepg.*` log level to `FINE`
- Flatpak v1.0 hardening pass:
  - Custom plugin exceptions (`FlatpakPluginException` + manifest/execution/configuration subtypes) wrapping command and process failures at the engine boundary
  - IDE-glue manifest reads go through the IntelliJ VFS (`FlatpakManifestVfsReader`); the pure-JDK `FlatpakManifestReader.parseFields(content, ...)` stays for tooling/hermetic tests
  - `DEFAULT_BUS` sandbox flags: the **Run** command exposes the session and system D-Bus sockets when the host Flatpak bus exists (`/run/flatpak/bus`), skipping them with a warning otherwise (GNOME Builder-style filtered default bus)
  - `generateBundledGtkSchema` Gradle task (regenerated bundled GTK/Adwaita schema, provisioned in CI pre-publish)
  - GitHub Actions: `ci.yml` (build + tests on PR/push) and `pre-publish.yml` (schema regeneration + `verifyPlugin` + `publishPlugin` on release)
  - Run-configuration suggestions on *Run → Edit Configurations → New* (`[command] <app-id>`) via `LocatableConfiguration.suggestedName()`

### Changed

- Run configurations named `[build] <app-id>` (template `[{0}] {1}`) via `FlatpakRunGenerator.formatRunName`
- Build directory/manifest path never blank on the command line (`effectiveBuildDir()`/`effectiveManifestPath()` default to `_build`/`flatpak.json`)
- Manifest-name heuristics unified: `FlatpakProjectDetector.isCandidateName` now shared with the JSON schema provider
- Feature flags consolidated: GTK gates all use `FeatureFlags`; the *Custom arguments* row was un-flagged and is now shown right below the command box only when the **Custom** command is selected
- Run-configuration editor option groups toggle live with the selected command: cleanup for **Build**, portal permissions for **Run**, custom arguments for **Custom**
- Console output shows each workflow step (`Running DEEP_CLEAN...`, `Running BUILD: <cmdline>`, `<label> finished with exit code N`) instead of only the final build report
- Deep clean runs inside a `WriteCommandAction` (no longer throws "Background write action is not permitted on this thread" when started from the pooled thread)

### Known Issues
- **GTK Preview**: Adwaita interfaces are not yet rendered correctly.
- **VALIDATE/EXPORT**: require `pip3`/`pipx`/`flatpak-node-generator` in the sandbox IDE (JetBrains test-IDE artifact; not reproducible on a normal IDE).

## [2026.1.1] - 2026-08-14

### Added

- JetBrains Marketplace **freemium licensing**: `product-descriptor` (`PFLATPAKDEV`, `optional`) with plugin-side license verification via `LicensingFacade`
- **Premium gating**: the GTK Preview tool window and in-editor *Preview* action are available to licensed users; unlicensed users see an upgrade panel/action opening the JetBrains registration dialog
- Publishing configuration (`PUBLISH_TOKEN`) for `./gradlew publishPlugin`
- Dual-license (AGPLv3 + commercial) with a `LICENSE` file

### Changed

- Version scheme switched to calendar versioning (`2026.1.1`) to match the paid-plugin `release-version` requirements
- README documents the free/premium split and the licensing model

### Added

- GTK snapshot preview: render `.ui`/`.glade` files to an image inside the GNOME SDK (`gtk4-builder-tool`) with an editor notification and a *GTK Preview* tool window
- Adwaita compatibility shim: Adwaita types render even without the Libadwaita runtime installed
- Portal sandbox flags on the **Run** command (portals, themes & icons, audio, Wayland), injected as `flatpak-builder --run` sandbox options
- Cleanup options: *Force clean* and *Deep clean* run as their own synchronous pre-steps before build/run/export
- `FlatpakManifestReader.readCommand`: the **Run** command executes the manifest's `command` (falling back to the app-id)

### Changed

- **Run** now uses `flatpak-builder --run` (run-only) instead of rebuilding, fixing an invalid `--force-clean` flag on run and a broken app-id-as-command invocation
- Commands are no longer flattened into a single process line; each command maps to its own process
- README roadmap and command reference refreshed to match the implemented behavior

### Fixed

- Invalid `--force-clean` on `flatpak-builder --run` mode
- **Run** failing with `bwrap: execvp <app-id>: No such file or directory` by executing the manifest `command`
- Cleanup commands being flattened into a single dangerous command line with `rm -rf`
