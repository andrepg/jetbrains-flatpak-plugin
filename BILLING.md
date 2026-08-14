# BILLING.md — Billing System & Architecture Plan

**Plugin:** Flatpak DevTools (`io.github.andrepg.flatpak-support`)
**Model:** Freemium via JetBrains Marketplace + open-source (AGPLv3) repository
**Date:** 2026-08-14
**Status:** Implemented in `2026.1.1`

---

## 1. Business model

Single plugin listing on the JetBrains Marketplace in **freemium** mode:

- **Free tier** — core Flatpak workflow, installable and usable without a license (`optional="true"`).
- **Premium tier** — GTK Preview, gated behind a JetBrains Marketplace subscription with a **30-day trial**.
- **Open source** — the full source lives on GitHub under AGPLv3, so Linux/GNOME developers can audit what the plugin executes on their machines and contribute fixes.

There are **no binaries to deploy or distribute** beyond the Marketplace ZIP; the plugin relies on the user's local `flatpak`/`flatpak-builder` and the installed GNOME SDK.

## 2. Why open-sourcing is the right move

- **Trust & security** — Linux developers are skeptical of closed-source binaries that drive their local build environment and Flatpak manifests. An auditable repo removes that objection.
- **Community contributions** — users who cannot or will not pay can still submit bug fixes, schema updates, or minor features.
- **Build-from-source friction** — technical users can clone and build premium features for free, but the majority of professional developers will pay for convenience: automated updates, no local build/compile overhead, and a supported binary.
- **Ecosystem alignment** — transparency is the cultural default in the Linux/GNOME ecosystem; aligning with it is a prerequisite for adoption.

## 3. Feature split (free vs premium)

Mapped to the **actual** codebase, not a generic wish-list:

| Tier | Features | Code |
|---|---|---|
| **Free** | Manifest auto-detection + schema completion/validation | `flatpak/detection/`, `flatpak/schemas/` |
| **Free** | Flatpak run configurations (Build / Run / Export / Clean / Validate / Custom) | `flatpak/runs/` |
| **Free** | Configurable `flatpak`/`flatpak-builder` binaries | `flatpak/settings/` |
| **Free** | GNOME SDK autodetection (GIR locator) | `gtk/schema/locator/` |
| **Free** | `.ui`/`.glade` schema completion & validation (bundled + SDK-generated XSD) | `gtk/schema/`, `gtk/schema/providers/` |
| **Premium** | Live GTK Preview tool window + in-editor *Preview* action | `gtk/preview/` (gated) |

**Why the GTK XML schema stays free:** in this codebase the `.ui` completion *is* the schema feature — gating it would cripple the free editing story and push users to build from source. The visible, "wow" feature (the preview) is the cleaner gate that drives conversion.

## 4. Licensing & legal

**Dual-license** (the author holds the copyright, so this is fully legal):

| Artifact | License |
|---|---|
| GitHub source | **AGPLv3** (`LICENSE`) |
| Marketplace binary | **Commercial** license via JetBrains Marketplace EULA |

**What AGPLv3 protects:** it prevents proprietary/closed redistribution of the code — a competitor cannot repackage the plugin as closed source.

**What it does not protect (be honest about this):**
- It does **not** stop end users from building the premium features from source — that bypass is an accepted trade-off of open-sourcing (see §2).
- It does **not** stop a competitor from forking into another AGPL project. Real defenses are branding/trademark, continuous updates, and Marketplace convenience.

## 5. JetBrains Marketplace mechanics

### 5.1 `plugin.xml` descriptor

```xml
<product-descriptor code="PFLATPAKDEV" release-date="20260814" release-version="20261" optional="true"/>
```

- `code` — Product Code registered in the JetBrains Sales System. Starts with `P`, 4–15 uppercase letters, no digits. **Effectively permanent** — choose once.
- `release-date` / `release-version` — anchor the major release and all subscription/fallback-term calculations.
- `optional="true"` — **required for freemium**: the plugin installs and runs without a license; only premium features are gated.
- Trial — premium features evaluate automatically for **30 days** after install.

### 5.2 Versioning

Paid plugins use **calendar versioning** and the version must match `release-version`:

- `version = 2026.1.1` ↔ `release-version = 20261` ↔ `release-date = 20260814`
- Minor updates keep `release-version`/`release-date` frozen and only increment the last segment (`2026.1.2`), so perpetual-fallback licensees keep receiving them.
- A changed `release-version`/`release-date` is a **new major release** and resets active trials.

### 5.3 Pricing & payout

- **List price:** `$4/month` or `$40/year`.
- **Commission:** 15% (JetBrains) → vendor keeps **85%** (net ≈ $34/yr at the annual price). The agreement caps the rate at 25%; it can change with one month's notice.
- **Taxes:** VAT/WHT are added on top of the list price and processed by JetBrains; they never reduce the vendor's share.
- **Payouts:** monthly, within 30 days of the month end; payments accrue until the **$200/EUR 200** threshold (paid annually even if below the threshold at year end). Requires bank details + tax forms in the Marketplace profile **before release**.

### 5.4 License checks

All licensing communication is handled by the IDE on the platform side (startup + at least once a day). The plugin **verifies** the signed confirmation stamp itself via `LicensingFacade` and JetBrains' public root certificates — no private keys exist in the platform.

## 6. Technical architecture

### 6.1 New module: `shared/license`

- **`LicenseCheck.kt`** — Kotlin port of JetBrains' reference `CheckLicense` (2024.3+ variant):
  - `PRODUCT_CODE = "PFLATPAKDEV"` (must match the `product-descriptor`).
  - `isLicensed(): Boolean?` — `true` licensed, `false` not, **`null`** when `LicensingFacade` is not initialized yet (no definitive answer).
  - Verifies `key:` stamps (personal/activation codes) and `stamp:` stamps (floating license servers) against the embedded JetBrains root certificates.
  - `requestLicense(message)` — opens the JetBrains registration dialog (`Register`/`RegisterPlugins`) with the product pre-selected and an explanatory message.
- **`PremiumFeatureGate.kt`** — the single decision point:
  - `isPremiumAvailable()` — `null` license state is treated as **locked** (safe default).
  - `requestAccess(message)` — routes to the registration dialog.

### 6.2 Gating points (premium surface = GTK Preview only)

1. **`GtkPreviewToolWindowFactory.createToolWindowContent`** — unlicensed users get an `UpgradePanel` (message + *Get a license* button → `requestAccess`) instead of the preview panel.
2. **`GtkPreviewEditorNotificationProvider`** — the *Preview* action becomes *Upgrade to unlock Preview* (→ `requestAccess`) for unlicensed users; validation status text remains visible for everyone.

Everything else — Flatpak runs, settings, schema providers — is untouched and free.

### 6.3 Upgrade UX flow

1. User opens the GTK Preview tool window or clicks the notification action.
2. Gate reports unlicensed → upgrade panel/CTA shown.
3. User clicks *Get a license* → JetBrains registration dialog opens with the product pre-selected and a custom message.
4. On purchase/trial activation the IDE refreshes the stamp; the next open grants access.

### 6.4 Development is never blocked

The premium gate must not slow down or block the team building the plugin:

- **Sandbox IDE (`./gradlew runIde`)** — has no Marketplace license, so the build task automatically sets the system property `flatpak.devtools.development=true` on the forked IDE. `PremiumFeatureGate` returns unlocked, so the GTK Preview works in dev like before the gate existed.
- **Unit tests** — the headless test JVM has no license and no override; the gate stays locked, so the `null → locked` behavior remains covered by tests. Tests can opt into the override by setting the property.
- **Release builds** — never set the property; the gate behaves as shipped to customers.
- **Why a runtime property is acceptable** — the source is open, so building from source already grants premium access; the override adds no new attack surface. It only removes the dev friction of the gate.
- **Frequency** — the gate only runs on tool-window open / notification render (a few times a day at most), not in a hot loop, so there is no CPU cost concern.

## 7. Open-source strategy

- Repo: `github.com/andrepg/jetbrains-flatpak` (create + push **the same day** as the paid release).
- `LICENSE` — full AGPLv3 text.
- `README.md` — dual-license section, the free/premium table, and this disclaimer:

> "This repository contains the full source code. Pre-compiled binaries with automated updates and the GTK Preview tool window require a subscription via the JetBrains Marketplace to support continuous development."

- Contributors implicitly license their contributions under the same dual-license model (note this in `CONTRIBUTING.md` — planned, not blocking).

## 8. Risks & mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Paid-plugin **approval** takes days | Can't be live today | Submit on release day; expect review turnaround |
| Product Code is **permanent** | Wrong choice is costly | `PFLATPAKDEV` is short, on-brand; locked at upload |
| **Build-from-source bypass** | Some users never pay | Accepted friction (95%+ pay for convenience) |
| AGPL does **not** stop AGPL forks | Possible free competitor | Branding, updates cadence, Marketplace presence |
| `LicensingFacade` **null-state** on cold start | False "unlicensed" flash | Gate treats null as locked (no false grants) |
| **Obfuscation** recommended for paid plugins | Binary reverse-engineering | Follow-up: ProGuard on the Marketplace build |

## 9. Publish checklist (release day)

1. [x] `version = 2026.1.1` in `gradle.properties`
2. [x] `<product-descriptor code="PFLATPAKDEV" release-date="20260814" release-version="20261" optional="true"/>` in `plugin.xml`
3. [x] `intellijPlatform.publishing { token = PUBLISH_TOKEN }` in `build.gradle.kts`
4. [x] `LicenseCheck` + `PremiumFeatureGate` + UpgradePanel + gated notification
5. [x] LICENSE (AGPLv3), README (dual-license + disclaimer), CHANGELOG
6. [ ] Commit working tree (incl. 45 pre-existing entries)
7. [ ] `./gradlew build` → `verifyPlugin` → smoke-test ZIP in `runIde`
8. [ ] Marketplace: bank/tax payout setup → upload ZIP → set `$4/mo` + `$40/yr` → submit for review
9. [ ] Create + push GitHub repo with the AGPLv3 repo and the README disclaimer

## 10. Follow-ups

- GResource awareness gated as premium (when implemented)
- ProGuard obfuscation for the Marketplace build
- `CheckLicense` REST API integration for support/audit workflows
- `CONTRIBUTING.md` with the dual-license contribution note
- Telemetry/usage considerations (opt-in) to inform future pricing
