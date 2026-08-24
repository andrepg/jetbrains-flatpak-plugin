<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Flatpak DevTools Changelog

## Unreleased

#### Added

- Run configurations are now validated before launching, as a chain of named rules
  (`runs/configuration/validation/`): manifest must parse and carry `app-id`/`id`, the
  configured `flatpak` CLI must be locatable (absolute path or on `PATH`), and the CUSTOM
  command now requires arguments

#### Fixed

- The `UNMOUNT_STALE` pre-step introduced in 2026.1.4 scanned only the build directory, but
  flatpak-builder creates its `.flatpak-builder` state dir in the working directory (the project
  root), so real `rofiles-fuse` leftovers were never detected and builds kept failing with
  "Transport endpoint is not connected" after Stop
  - The sweep now covers the project root's state dir (plus the configured build dir) and only ever
    touches mounts containing `.flatpak-builder/`, leaving unrelated FUSE mounts alone
  - Dead mount points are matched through their canonicalized parent, surviving symlinked roots such
    as Fedora Atomic's `/home` → `/var/home`

#### Removed

- Bundled GTK schema from build - we are now relying only at runtime
  - GtkBuilder `.ui` completion/validation is now generated exclusively at runtime from the user's
    installed GNOME SDK and cached in the plugin config dir
  - Projects without a discoverable installed SDK get no schema until generation succeeds; the
    existing warning balloon reports failures
  - This is a safe change because the user it is supposed to have the SDK installed to build the app

## 2026.1.4 - 2026-08-21

#### Bugfixes

- Stop button terminates `flatpak` forcefully and leaves dead `ro-files` FUSE mounts
  - The wrongful termination caused `Error: opendir(rofiles-...): Transport endpoint is not connected`
  - A new silent (unless errored) `UNMOUNT_STALE` pre-step detects and unmounts these mounts before a build
  - Killing a `flatpak` process now terminates the whole process chain
- A wrong validation was creating an output folder inside the `$HOME` (user's folder). We removed this validation completely.
- Remove invalid `io.sentry.jvm.gradle` from `plugin.xml` dependency, which led to an initialization error

#### Changed

- The `./gradlew build` now produces an installable ZIP inside `./build/distributions`
- Silently check for Sentry or fallback to log only (on missing Sentry lib, or jar installed directly, for instance)

## 2026.1.3 - 2026-08-15

- Bump our supported version to anyone since 2025.3

## 2026.1.2 - 2026-08-15

- We have included a new plugin icon to identify better our project against others in Markeplace
- There is an entire new README and cool screenshots to see at our marketplace page
- New plugin description and feature presentation

## 2026.1.1 - 2026-08-15

#### 🎉 First release

- Flatpak integration with `org.flatpak.Builder` under **Run** settings, with flags and portals
- Automatic schema detection for Flatpak manifests in the format com.developer.AppName(.?), in YAML or JSON formats
- Code completion with detected schema inside Flatpak manifest
- Integration and deep clean of Flatpak's artifacts before building the app
- Run detection from manifest files, with option on Context Menu (right-click, usually)
- Custom binaries can be configured under Settings | Languages & Frameworks | Flatpak Binaries
