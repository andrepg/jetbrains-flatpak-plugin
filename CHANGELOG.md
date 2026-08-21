<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Flatpak DevTools Changelog

## Unreleased

## 2026.1.4 - 2026-08-20

#### Bugfixes

- Stop button terminates `flatpak` forcefuly and left dead `ro-files` fuse mounts
  - The wrongful termination caused `Error: opendir(rofiles-...): Transport endpoint is not connected`
  - A new silent (unless errored) `UNMOUNT_STALE` pre-step was added to detected and unmount these type of errors before build
  - When killing a `flatpak` process there is a chain termination
- A wrong validation was creating an output folder inside the `$HOME` (user's folder). We removed this validation completely.
- Remove invalid `io.sentry.jvm.gradle` from `plugin.xml` dependency, which led to an initialization error

#### Changed

- The `./gradlew build` now produces a installable ZIP inside `./build/distributions`
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
