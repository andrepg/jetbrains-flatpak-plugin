<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Flatpak-support Changelog

## [Unreleased]

### Known Issues
- **GTK Preview**: Adwaita interfaces are not yet rendered correctly.
- **RUN Command**: The build output path is incorrect, and the app does not run after building.

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
