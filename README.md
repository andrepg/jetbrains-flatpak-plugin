# Flatpak DevTools

> Build, run, and validate Flatpak applications from your JetBrains IDE.

**Flatpak DevTools** brings the Flatpak ecosystem into JetBrains IDEs (IntelliJ IDEA, PyCharm, GoLand, and more). It turns `flatpak-builder` into a first-class workflow: dedicated Run Configurations for every Flatpak action, schema-aware editing for Flatpak manifests, and — behind a feature flag — schema-aware GNOME/Adwaita `.ui` file editing with a live GTK preview.

Develop and package sandboxed Linux applications without leaving your IDE.

> **v1.0** stabilizes the **Flatpak module** (run configurations, command execution, detection, settings). The **GTK/Adwaita schema + preview** work is feature-flagged and tracked as phase 2.

## Features

### Flatpak (v1.0 core)

- **Automatic manifest detection** — files named `manifest.json`, `flatpak.json`, `flatpak-manifest.json`, or app-id style files such as `org.example.App.json` (JSON/YAML) are recognized automatically and bound to the official Flatpak manifest schema.
- **Code completion & live validation** — edit `app-id`, `runtime`, `sdk`, `command`, `modules`, `finish-args`, and every other manifest field with schema-driven completion and inline validation, powered by the community-maintained [SchemaStore](https://www.schemastore.org/) schema (JSON Schema draft-07).
- **Smart project detection** — Flatpak manifests are detected when a project opens: a one-time balloon (*"Flatpak project detected"*) offers an opt-in **Create Run Configurations** action, and right-clicking any manifest suggests **Run '`[build] <app-id>`'** directly from the context menu. The Flatpak configuration type is hidden in **Edit Configurations** for projects without manifests.
- **One-click Flatpak actions** — a dedicated **Flatpak** Run Configuration type exposes the full build lifecycle as runnable configurations:

  | Command  | Description                                                                   |
  |----------|-------------------------------------------------------------------------------|
  | **Build**    | Runs `flatpak run org.flatpak.Builder --force-clean <build-dir> <manifest>`   |
  | **Run**      | Launches the built app inside the sandbox via `flatpak-builder --run`, executing the manifest's `command` (falls back to the app-id) |
  | **Export**   | Exports the app to a repository/bundle (`--repo=repo-build`)                    |
  | **Clean**    | Removes the build directory (optionally with a deep clean of the `flatpak-builder` cache) |
  | **Custom**   | Runs Flatpak with your own additional arguments                                   |
  | **Validate** | Shows the effective manifest (`--show-manifest`)                                |

  Cleanup and sandbox toggles are wired into the generated command line:
  - **Cleanup Options** — *Force clean* prepends a `clean` step before build/run/export; *Deep clean* also removes the `flatpak-builder` cache.
  - **D-Bus (on for `Run`)** — the app is given the session and system bus sockets (`--socket=session-bus`, `--socket=system-bus`) so D-Bus service discovery works out of the box. The sockets are added only when the host exposes a Flatpak bus at `/run/flatpak/bus`; otherwise they are skipped with a warning and the app relies on flatpak's filtered default session bus (the same behaviour as GNOME Builder).
  - **Portal Permissions** (applied to `Run`) — *Portals* (`--talk-name=org.freedesktop.portal.*`, `--device=dri`, `--env=GTK_USE_PORTAL=1`), *Themes & icons* (`--filesystem=xdg-config/gtk-3.0:ro`, `xdg-data/icons:ro`, `xdg-data/themes:ro`, `xdg-config/glib-2.0`), *Audio* (`--socket=pulseaudio`), *Wayland* (`--socket=wayland`).

- **Flatpak Builder SDK integration** — commands are executed through the official Flatpak Builder application (`org.flatpak.Builder`), so builds behave exactly like your terminal workflow.
- **Streamed console output** — command output is streamed into the standard Run console, reusing your favorite JetBrains tool window.
- **Configurable binaries** — a dedicated settings page (Languages & Frameworks → Flatpak) lets you point to custom `flatpak` and `flatpak-builder` binaries, with on-input validation and automatic `PATH` fallback.

### GTK/Adwaita (phase 2, feature-flagged)

- Schema-aware editing for `.ui`/`.glade` GtkBuilder files, generated from the project's GNOME SDK GIR data (falls back to the bundled schema) — completion, highlighting and validation, including Adwaita and GtkSource widgets
- **GNOME SDK autodetection** — the SDK and runtime are resolved from your manifest via the Flatpak CLI (with an install-root glob fallback), so the schema always matches the SDK you build against
- **GTK snapshot preview** — a *GTK Preview* tool window renders the active `.ui` file to an image inside the GNOME SDK (via `gtk4-builder-tool`); an editor notification offers a one-click preview, and an Adwaita compatibility shim (`LD_PRELOAD`) makes Adwaita types render without installing the runtime
- **WIP** — GResource awareness: notifies you when a UI file is not declared in your GResource manifest

> These features are gated behind the premium feature flag `flatpak.gtk.preview.enabled` (part of a paid subscription). See [Free & Premium](#free--premium).

## Requirements

- A JetBrains IDE (2025.3 or later)
- `flatpak` and `flatpak-builder` (or Flatpak Builder SDK) available on your system
- A Linux distribution with Flatpak support

## Installation

- **JetBrains Marketplace** — search for *Flatpak DevTools* on the [JetBrains Marketplace](https://plugins.jetbrains.com/marketplace). The core Flatpak workflow is **free**; a subscription (`$4/month` or `$40/year`, 30-day trial) unlocks the GTK Preview.
- **From source** — build the plugin yourself (see [Development](#development)). The repository is licensed under AGPLv3.

### Free & Premium

| Tier | Features |
|---|---|
| **Free** | Manifest auto-detection and schema completion/validation, all Flatpak run configurations (Build / Run / Export / Clean / Validate / Custom), configurable binaries |
| **Premium** | GNOME SDK autodetection, `.ui`/`.glade` schema completion and validation, live **GTK Preview** tool window and the in-editor *Preview* action |

## Configuration

1. Open **Settings → Languages & Frameworks → Flatpak**.
2. Confirm the paths to the `flatpak` and `flatpak-builder` binaries (defaults: `/usr/bin/flatpak` and `org.flatpak.Builder`).
3. Create a **Flatpak** Run Configuration, pick your manifest, choose the command, and press Run.

## Diagnostics & privacy

The plugin can help you debug issues and (optionally) report them back to the team.

- **Debug logging** — write verbose `io.github.andrepg.*` plugin logs to the IDE log
  (`Help | Show Log`). Enable it in *Settings → Languages & Frameworks → Flatpak →
  Diagnostics → Debug logging*, or launch the IDE with `-Dflatpak.debug=true`. Everything
  is logged under the `io.github.andrepg.*` categories, so you can also scope it with
  `Help | Diagnostic Tools | Debug Log Settings`.
- **Anonymous error reporting (Sentry)** — **off by default**. When enabled
  (*Settings → Flatpak → Diagnostics → Share anonymous error reports*, or
  `-Dflatpak.sentry.enabled=true`), errors and uncaught exceptions are reported to the
  plugin's Sentry instance. No code, manifest contents, or personal data is sent: the
  hostname and user identity are stripped, and only `io.github.andrepg` stack frames are
  kept as in-app. The DSN comes from the `flatpak.sentry.dsn` system property, the
  `SENTRY_DSN` environment variable, or the embedded project DSN.

## Development

```bash
./gradlew runIde                      # Launch sandbox IDE with plugin loaded
./gradlew build                      # Build plugin ZIP in build/distributions/
./gradlew test                       # Run tests
./gradlew verifyPlugin               # Check plugin compatibility
./gradlew generateBundledGtkSchema   # Regenerate the bundled GTK schema from the installed GNOME SDK
```

The sandbox IDE runs with premium features unlocked automatically
(`flatpak.devtools.development=true`); release builds do not.

**`generateBundledGtkSchema`** regenerates the bundled GTK/Adwaita schema artifacts
(`src/main/resources/schemas/gtk-ui-schema.json` + `gtk-ui.xsd`) from GIR data. It
provisions the *bundled fallback* (GNOME 50 basic support) that ships with the
plugin; the phase-2 runtime per-project generation is the primary path. Override
inputs with `-PgirDir=<dir>` / `-PschemaOut=<file>` (see AGENTS.md).

## Roadmap

### v1.0 — Flatpak module
- [x] Flatpak Run Configurations (build / run / export / clean / custom / validate)
- [x] Flatpak manifest schema autodetection with completion & validation
- [x] Flatpak project detection with run-configuration suggestions (`[build] <app-id>`)
- [x] Configurable Flatpak binaries
- [x] Cleanup options (force/deep clean), D-Bus sockets and portal sandbox flags on Run

### Phase 2 — GTK/Adwaita (feature-flagged)
- [x] GTK/Adwaita `.ui` file editing with code completion
- [x] GNOME SDK autodetection
- [x] GTK snapshot preview tool window
- [ ] GResource integration and undeclared-file notifications
- [ ] LSP for XML files

## Known Issues

- **GTK Preview**: Adwaita interfaces are not yet rendered correctly (phase 2).
- **Validate/Export command in the JetBrains test sandbox**: running `Validate` or `Export` inside the plugin-development sandbox IDE can report missing `pip3`/`pipx` (via `flatpak-node-generator`). This is an artifact of the bare sandbox SDK (which lacks the full Flatpak Builder runtime), not of the command itself — it works on a normal IDE.
- **Custom arguments**: the *Custom arguments* field is shown in the run-configuration editor only when the **Custom** command is selected.

## Visual Testable Features Checklist

### Flatpak Run Configurations
- [ ] **Build**: Runs `flatpak-builder` and completes without errors.
- [ ] **Run**: Launches the built app inside the sandbox and displays output in the Run console.
- [ ] **Export**: Exports the app to a repository/bundle without errors.
- [ ] **Clean**: Removes the build directory and clears the cache.
- [ ] **Validate**: Shows the effective manifest without errors.
- [ ] **Custom**: Runs Flatpak with custom arguments.

### Portal Sandbox Flags (Run Command)
- [ ] **D-Bus**: `--socket=session-bus`, `--socket=system-bus` are applied when `/run/flatpak/bus` exists.
- [ ] **Portals**: `--talk-name=org.freedesktop.portal.*` is applied.
- [ ] **Themes & Icons**: `--filesystem=xdg-config/gtk-3.0:ro`, `xdg-data/icons:ro`, `xdg-data/themes:ro`, `xdg-config/glib-2.0` are applied.
- [ ] **Audio**: `--socket=pulseaudio` is applied.
- [ ] **Wayland**: `--socket=wayland` is applied.

### Cleanup Options
- [ ] **Force Clean**: Prepends a `clean` step before build/run/export.
- [ ] **Deep Clean**: Also removes the `flatpak-builder` cache.

### GTK/Adwaita UI Support (premium / feature-flagged)
- [ ] **Schema Completion**: `.ui`/`.glade` files show schema-driven completion and validation.
- [ ] **GTK Preview**: The *GTK Preview* tool window renders the active `.ui` file to an image.
- [ ] **Editor Notification**: An in-editor notification offers a one-click preview.

### Settings
- [ ] **Custom Binaries**: The path to `flatpak` and `flatpak-builder` can be configured in Settings → Languages & Frameworks → Flatpak.

## Support

- Report bugs and request features via [GitHub Issues](https://github.com/andrepg/jetbrains-flatpak/issues)
- Contact: [contato@startap.dev.br](mailto:contato@startap.dev.br)

## License

This project is **dual-licensed**:

- **Source code** (this repository) — [AGPLv3](LICENSE). Free to use, modify, and distribute, provided derivative works remain open under the same license.
- **Marketplace binaries** — a separate commercial license sold through the JetBrains Marketplace.

This repository contains the full source code. Pre-compiled binaries with automated updates and the GTK Preview tool window require a subscription via the JetBrains Marketplace to support continuous development.
