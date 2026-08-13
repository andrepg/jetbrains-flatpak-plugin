# Flatpak DevTools

> Build, run, and validate Flatpak applications from your JetBrains IDE.

**Flatpak DevTools** brings the Flatpak ecosystem into JetBrains IDEs (IntelliJ IDEA, PyCharm, GoLand, and more). It turns `flatpak-builder` into a first-class workflow: dedicated Run Configurations for every Flatpak action, schema-aware editing for Flatpak manifests, and — on the roadmap — full support for GNOME/Adwaita UI files.

Develop and package sandboxed Linux applications without leaving your IDE.

## Features

### Flatpak

- **Automatic manifest detection** — files named `manifest.json`, `flatpak.json`, `flatpak-manifest.json`, or app-id style files such as `org.example.App.json` (JSON/YAML) are recognized automatically and bound to the official Flatpak manifest schema.
- **Code completion & live validation** — edit `app-id`, `runtime`, `sdk`, `command`, `modules`, `finish-args`, and every other manifest field with schema-driven completion and inline validation, powered by the community-maintained [SchemaStore](https://www.schemastore.org/) schema (JSON Schema draft-07).
- **One-click Flatpak actions** — a dedicated **Flatpak** Run Configuration type exposes the full build lifecycle as runnable configurations:

  | Command  | Description                                                                   |
  |----------|-------------------------------------------------------------------------------|
  | **Build**    | Runs `flatpak run org.flatpak.Builder <build-dir> <manifest>`                   |
  | **Run**      | Builds and launches the application, resolving the app-id automatically from your manifest |
  | **Export**   | Exports the app to a repository/bundle (`--repo=repo-build`)                    |
  | **Clean**    | Removes the build directory                                                      |
  | **Custom**   | Runs Flatpak with your own additional arguments                                   |
  | **Validate** | Manifest validation *(roadmap)*                                                  |

- **Flatpak Builder SDK integration** — commands are executed through the official Flatpak Builder application (`org.flatpak.Builder`), so builds behave exactly like your terminal workflow.
- **Streamed console output** — command output is streamed into the standard Run console, reusing your favorite JetBrains tool window.
- **Configurable binaries** — a dedicated settings page (Languages & Frameworks → Flatpak) lets you point to custom `flatpak` and `flatpak-builder` binaries, with on-input validation and automatic `PATH` fallback.

### GTK/Adwaita *(in progress)*

- **WIP** — GTK/Adwaita UI editing: XML/`.ui` interface files with code completion
- **WIP** — GResource awareness: notifies you when a UI file is not declared in your GResource manifest
- **WIP** — GNOME SDK autodetection

## Requirements

- A JetBrains IDE (2025.3 or later)
- `flatpak` and `flatpak-builder` (or Flatpak Builder SDK) available on your system
- A Linux distribution with Flatpak support

## Installation

- **JetBrains Marketplace** — coming soon *(follow the repository to be notified on release)*
- **From source** — build the plugin yourself (see [Development](#development))

## Configuration

1. Open **Settings → Languages & Frameworks → Flatpak**.
2. Confirm the paths to the `flatpak` and `flatpak-builder` binaries (defaults: `/usr/bin/flatpak` and `org.flatpak.Builder`).
3. Create a **Flatpak** Run Configuration, pick your manifest, choose the command, and press Run.

## Development

```bash
./gradlew runIde              # Launch sandbox IDE with plugin loaded
./gradlew build              # Build plugin ZIP in build/distributions/
./gradlew test               # Run tests
./gradlew verifyPlugin       # Check plugin compatibility
./gradlew extractGtkSchema   # Regenerate the GTK/Adwaita UI schema from GIR files
```

## Roadmap

- [x] Flatpak Run Configurations (build / run / export / clean / custom)
- [x] Flatpak manifest schema autodetection with completion & validation
- [x] Configurable Flatpak binaries with validation
- [ ] Manifest validation command
- [ ] GTK/Adwaita `.ui` file editing with code completion
- [ ] GResource integration and undeclared-file notifications
- [ ] GNOME SDK autodetection

## Support

- Report bugs and request features via [GitHub Issues](https://github.com/andrepg/jetbrains-flatpak/issues)
- Contact: [contato@startap.dev.br](mailto:contato@startap.dev.br)

## License

Proprietary — all rights reserved.
