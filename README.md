# JetBrains Flatpak SDK Integration

> Compile, build, run and validate your Flatpak apps from any JetBrains IDE

**Flatpak DevTools** is a _freemium_ plugin to integrate Flatpak SDK and make easier the Linux development in any JetBrains IDE. Use your favorite backend language to power your ideas.

👉 The installation of `flatpak` and `org.flatpak.Builder` are required, as documented in Flatpak documentation. They are the very basic tool set of the Flatpak environment and this plugin rely heavly on it.

## Features
> *Running the application*, *writing intelligent code* and have *basic language support* are our foundation.

We have a bunch of core features that will _always be free_. They are the ground to Linux/Flatpak development and our enforces our Open-source vision.

Features like **Autocompletion**, **Program Execution & Build**, code validation, formatting, and linting are part of the core. The ones marked with a 🔐 `lock` emoji are under the paid license.

### Flatpak

- Automatic manifest detection based on their format names - `com.domain.AppName`
- `JSON` and `Yaml` manifest extensions supported
- Smart project detection, with smart suggestions
- One-click Flatpak actions from the **Run** menu
- Flatpak deep clean support
- Configurable binary paths for Flatpak and Flatpak Builder

### GTK/GNOME/Adwaita

- Smart detection of UI/XML files
- In-flight schema generation from local SDK
- Autocompletion for GTK XML tags
- Autocompletion for GTK XML attributes ==| Planned |==
- Autocompletion for Adwaita widgets ==| Planned |==
- GResource awareness and auto-editing ==| Planed |==
- 🔐 GTK interface snapshot preview embedded in IDE ==| Planned |==
- 🔐 LSP for XML files ==| Planned |==

## Qt/KDE

> The entire KDE integration is part of our Roadmap - anything can change anytime

- Qt/KDE interface autocompletion
- In-flight schema generation from local SDK
- 🔐 Qt/KDE interface snapshot preview embedded in IDE

---
## Installation

This plugin can be installed from the JetBrains Marketplace or from your IDE plugin manager.

<iframe width="245px" height="48px" src="https://plugins.jetbrains.com/embeddable/install/33572"></iframe>

## Plugin Development

The plugin development is made in Kotlin and Gradle. You are welcome to make PRs and we reserve the right to evaluate when and how implement in the base code.

### System & IDE prerequisites

- Intellij IDEA 2026.1
- Grade 9.6 / Groovy 4.0
- [Plugin DevKit](https://plugins.jetbrains.com/plugin/22851-plugin-devkit)
- [Ktlint](https://plugins.jetbrains.com/plugin/15057-ktlint)

### Useful commands

There is a ton of Gradle commands configured. All of them listed by `./gradlew tasks`. The most important are:

| ⌨ Command                  | 💬 Description                                            |
| -------------------------- | --------------------------------------------------------- |
| `runIde`                   | Build plugin and launch sandbox IDE with it loaded        |
| `build`                    | Build **JAR** artifacts to distribute as installable      |
| `test`                     | Execute tests routines written to project                 |
| `verifyPlugin`             | Check plugin agains JetBrains Marketplace recommendations |
| `generateBundledGtkSchema` | Read installed SDK and generate GTK schemas to bundle     |
| `check`                    | Run code linting and verifications to ensure quality      |

## Known Issues

If we are aware of any issue, it is registered in our Bug Tracker. If you can not find it, generate a log and send to us. Open a ticket, follow the instructions and wait for our response.

If you are in a hurry, contact us at `contato@startap.dev.br` and we can discuss compensations to prioritize your problem.

## License & Code fairness

- This project is licensed under AGPL License. You are free to fork and edit any of it.
- Any code here is provided **AS-IS**, without any guarantees beyond our subscription model
- You are required to publish your work derived from us, under the same license agreement.
- More details about our license can be seen at [LICENSE disclaimer](https://github.com/andrepg/jetbrains-flatpak-plugin/blob/main/LICENSE).