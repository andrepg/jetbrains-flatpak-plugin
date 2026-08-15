# Flatpak DevTools — Publishing Guide (v1.0 stable release)

**Plugin:** Flatpak DevTools (`io.github.andrepg.flatpak-support`)
**Version:** `2026.1.1` — calendar versioning (`2026.1.x`), paid-plugin scheme
**Product code:** `PFLATPAKDEV` (`release-version 20261`, `release-date 20260814`)
**Marketplace:** https://plugins.jetbrains.com/marketplace

This guide walks the **first stable release** from a clean tree to a live
Marketplace listing. It is the executable counterpart of
[`TEST_REPORT.md`](./TEST_REPORT.md).

---

## 1. Pre-flight (do before tagging)

### 1.1 Local verification

```bash
./gradlew clean build --no-build-cache   # build-cache caveat: use --no-build-cache (AGENTS.md)
./gradlew test                           # 101 tests, 1 phase-2 skip, 0 failures
```

> `--no-build-cache` is required locally: the Gradle build cache once served a
> stale `compileTestKotlin` ABI and produced misleading test failures.

### 1.2 Compatibility check (recommended before tagging)

```bash
./gradlew verifyPlugin
```

`pre-publish.yml` runs this automatically on release, but running it locally
catches problems before a tag exists. **Do not** run `runPluginVerifier` unless
you specifically want it — `verifyPlugin` is the Marketplace gate.

### 1.3 Smoke test (end-to-end on a normal IDE)

Use a normal IDE instance (not the `runIde` sandbox) with a real Flatpak sample
project and the plugin ZIP from `build/distributions/` installed. Walk the
[README *Visual Testable Features Checklist*](../README.md) — at minimum:

1. **Build** a sample manifest (console shows `Running DEEP_CLEAN...` and
   `Running BUILD: <cmdline>` steps).
2. **Run** the built app (D-Bus sockets when `/run/flatpak/bus` exists; warning
   otherwise).
3. **Export** to a repo/bundle (skip on the sandbox IDE — see Known Issues).
4. **Validate** (`--show-manifest`), **Clean**, and **Custom** with arguments.
5. **Run → Edit Configurations → New** suggests `[command] <app-id>`.
6. Settings → Languages & Frameworks → Flatpak binaries.

Known sandbox IDE artifact: **Validate/Export may fail with missing
`pip3`/`pipx`/`flatpak-node-generator`** — that is the bare test sandbox, not the
plugin (see `docs/TEST_REPORT.md` §5).

### 1.4 Checklist before tagging

- [x] `gradle.properties` → `version = 2026.1.1`
- [x] `plugin.xml` → `<product-descriptor code="PFLATPAKDEV" release-date="20260814" release-version="20261" optional="true"/>`
- [x] `build.gradle.kts` → `publishing { token = PUBLISH_TOKEN; channels = listOf("stable") }`
- [x] `build.gradle.kts` → `pluginConfiguration { ideaVersion { untilBuild = "253.*" } }` (compat bound)
- [x] `CHANGELOG.md` → release notes folded under `[2026.1.1]` (change-notes are auto-generated from it)
- [x] `LICENSE` (AGPLv3), README dual-license disclaimer
- [x] Test suite green (`docs/TEST_REPORT.md`)
- [ ] `./gradlew build` ZIP present in `build/distributions/`
- [ ] Working tree committed; **no tags exist for this version yet**

---

## 2. Release mechanics (tag → CI publish)

Releases are published by the **pre-publish workflow**, triggered on a `v*`
tag push or by `workflow_dispatch`.

### 2.1 Commit and tag

```bash
git add -A
git commit -m "chore: prepare 2026.1.1 stable release"
git tag -a v2026.1.1 -m "Flatpak DevTools 2026.1.1"
git push origin main
git push origin v2026.1.1
```

### 2.2 What the workflow does (`.github/workflows/pre-publish.yml`)

1. Runs in a pinned `fedora:41` container with `org.gnome.Sdk//50` installed.
2. `./gradlew generateBundledGtkSchema` — regenerates the bundled GTK schema;
   **auto-commits** any drift in `src/main/resources/schemas/` back to the
   branch. (If a schema-drift commit lands, the release tag points at the
   pre-drift commit — that is fine: the regenerated artifact is committed
   *after* the tag but *before* `publishPlugin` runs in the same job, so the
   uploaded ZIP contains the fresh schema.)
3. `./gradlew verifyPlugin` — Marketplace compatibility gate.
4. `./gradlew publishPlugin` with the `PUBLISH_TOKEN` secret → publishes to the
   **stable** channel.

### 2.3 Manual fallback (no CI)

```bash
PUBLISH_TOKEN=<token> ./gradlew publishPlugin
```

Publish target: the **stable** channel (default in `build.gradle.kts`). If a
pre-release channel is ever needed, `./gradlew publishPlugin -PintellijPublishChannel=...`.

---

## 3. Marketplace listing (plugins.jetbrains.com) — first release

The code release is only half the job. The listing itself is managed in the
Marketplace web console.

### 3.1 Profile / payout setup (must exist **before** release)

Per `BILLING.md` §5.3: payouts require bank details and tax forms in the
Marketplace profile, and payments accrue until the $200/EUR 200 threshold
(paid annually below it). **Do this before submitting** so first-month revenue
isn't held up.

### 3.2 Listing metadata

- **Name:** Flatpak DevTools
- **Description / change-notes:** served from `plugin.xml` + `CHANGELOG.md`
  (auto-generated change-notes). Verify the rendered change-notes on the
  Marketplace before submitting.
- **Category:** *Tools Integration* (or *Framework Integration* — pick the
  closest fit on the form).
- **Tags:** `flatpak`, `linux`, `gnome`, `gtk`, `sandbox`, `builder`.
- **Screenshots (required):** upload at least 2–3 — a Flatpak manifest with
  completion/validation, the Run Configuration editor, and (optionally) the
  GTK Preview tool window.
- **License/EULA:** the Marketplace listing uses the JetBrains paid-plugin EULA
  for the commercial binary; the GitHub repo keeps AGPLv3 (dual-license, see
  `README.md`).

### 3.3 Pricing

- **List price:** `$4/month` and `$40/year` (30-day trial).
- Commission 15% → vendor keeps 85% (net ≈ $34/yr at annual price).

### 3.4 Upload & submit

1. Upload the ZIP from `build/distributions/` (or let `publishPlugin`/CI push it).
2. Set the price, add screenshots, review the description.
3. **Submit for review.** Paid-plugin approval takes days (JetBrains review
   turnaround) — plan the announcement accordingly.

---

## 4. Post-release

- **Verify the listing** once review passes: version `2026.1.1`, icon, change-notes,
  price/trial visible.
- **Install test:** install the listed plugin in a clean IDEA 2025.3.5 and confirm
  the free tier works and the premium GTK Preview gate opens the registration
  dialog (30-day trial).
- **Open-source sync:** the GitHub repo (`github.com/andrepg/jetbrains-flatpak`)
  already hosts the source with the AGPLv3 license and the dual-license
  disclaimer — confirm the README/Sentry release tag (`flatpak-devtools@2026.1.1`)
  line up.
- **Future minor releases:** bump only the last version segment (`2026.1.2`),
  keep `release-version`/`release-date` frozen (per `BILLING.md` §5.2), update
  `CHANGELOG.md`, tag `v2026.1.2`, push. A changed `release-version`/`release-date`
  is a **new major release** and resets active trials — avoid unless intended.

---

## 5. Risks & rollback

| Risk | Mitigation |
|---|---|
| Paid-plugin review takes days | Submit early on release day; announce after approval |
| No `until-build` bound | Fixed in `build.gradle.kts` (`253.*`) — prevents claiming compatibility with all future IDE builds |
| Schema drift auto-commit changes the tagged tree | Harmless: regenerated before `publishPlugin` in the same job; keep `schemas/` in sync by running `generateBundledGtkSchema` after bumping the GNOME SDK |
| Publish to wrong channel | `channels = listOf("stable")` is pinned in `build.gradle.kts`; never pass `-PintellijPublishChannel=stable` accidentally |
| `PUBLISH_TOKEN` missing | Workflow fails fast at the `publishPlugin` step; the token is a repository secret (`PUBLISH_TOKEN`) |
| License stamp verification fails on cold start | Gate treats `null` as locked (no false grants); see `BILLING.md` §6.4 |
| Build-from-source bypass of premium features | Accepted trade-off of open-sourcing (see `BILLING.md` §8) |

---

*See also:* [`TEST_REPORT.md`](./TEST_REPORT.md), [`BILLING.md`](../BILLING.md),
`TOUCHES_UP.md`, `GTK_BUILDING_PLAN.md`.
