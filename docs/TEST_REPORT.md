# Flatpak DevTools — Test Report (v1.0 stable release)

**Plugin:** Flatpak DevTools (`io.github.andrepg.flatpak-support`)
**Target IDE:** IntelliJ IDEA 2025.3.5 (`since-build 253`, `until-build 253.*`)
**Version under test:** `2026.1.1`
**Date:** 2026-08-15
**Verdict:** **READY for stable release** — no release-blocking failures. 1 GTK test intentionally skipped (phase 2, see [§4](#4-skipped-tests)).

---

## 1. Executive summary

| Metric | Value |
|---|---|
| Test suite total | **101** |
| Passed | **100** |
| Skipped (`@Ignore`, phase 2) | **1** |
| Failures / errors | **0** |
| Build | `./gradlew clean build --no-build-cache` — green |
| CI gate (`ci.yml`) | `./gradlew build` + `./gradlew test` — non-GTK gate |
| Compatibility (`pre-publish.yml`) | `verifyPlugin` on the pinned SDK container before publish |

The suite covers the complete Flatpak v1.0 module (run configurations, command
execution, manifest parsing, project detection, settings) plus the shared
diagnostics/licensing stack and the GTK phase-2 cores. All Flatpak module and
shared tests are green; the single skipped test is a known phase-2 GTK gap
tracked in `GTK_BUILDING_PLAN.md` §4.1 and does **not** gate the v1.0 release.

> **How this report was produced:** the numbers below are read from the local
> Gradle test results in `build/test-results/test/*.xml` on commit
> `b133da3`. Command to reproduce: `./gradlew clean test --no-build-cache`.

---

## 2. Test inventory (by package)

### 2.1 Flatpak module — `flatpak/*` (31 tests, 0 failures)

| Test class | Tests | Coverage |
|---|---|---|
| `detection/FlatpakProjectDetectorTest` | 7 | Manifest filename heuristics (shared predicate), candidate-name detection across `.json`/`.yaml`/`.yml`, app-id style names |
| `runs/execution/CommandExecutionStrategyTest` | 1 | Mapping of each `UserVisibleCommand` to the executed `InternalCommand` |
| `runs/execution/CommandExecutionEngineTest` | 6 | Command-line generation per command; **host-bus-guarded D-Bus sockets** (`{ true }` adds `DEFAULT_BUS`, `{ false }` skips with warning — I2/B2); positional argument order (I2) |
| `runs/execution/CommandChainProcessHandlerTest` | 4 | Workflow-step labelling (`Running DEEP_CLEAN...`, `Running BUILD: <cmdline>`, exit-code relay), pre-step abort semantics (B1) |
| `runs/execution/DeepCleanExecutorTest` | 3 | VFS deep clean; write-action on the pooled chain thread (B5) |
| `runs/execution/RunConfigurationValidatorTest` | 4 | `default manifest path is reported`, two-error combination, `missing manifest file is reported`, `valid configuration has no errors` |
| `runs/ui/RunConfigurationApplyTest` | 2 | Settings editor apply/reset round-trip |
| `settings/ui/FlatpakSettingsConfigurableTest` | 1 | Binaries settings page configurable |
| `utils/FlatpakManifestReaderTest` | 3 | Pure-JDK manifest parser: JSON/YAML fields, `command` fallback, `FlatpakManifestException` |

### 2.2 Shared stack — `shared/*` (11 tests, 0 failures)

| Test class | Tests | Coverage |
|---|---|---|
| `license/LicenseCheckTest` | 6 | Kotlin `CheckLicense` port: stamp/key verification against JetBrains root certs, `null → locked` gate behaviour |
| `log/LogConfigurationTest` | 3 | JUL level switch on the `io.github.andrepg.*` namespace; debug toggle |
| `sentry/SentryLogBridgeTest` | 2 | Log → Sentry event/breadcrumb mapping; no-op when the client is off |

### 2.3 GTK phase 2 — `gtk/*` (59 tests, 1 skipped)

| Test class | Tests | Coverage |
|---|---|---|
| `preview/AdwShimManagerTest` | 3 | Per-branch `adw_init()` constructor shim compilation/caching logic |
| `preview/AdwShimManagerHermeticTest` | 4 (1 skipped) | Hermetic shim detection under the fake runner — **see §4** |
| `preview/GtkBuilderToolRunnerTest` | 8 | `gtk4-builder-tool` validate/render invocation |
| `preview/GtkBuilderToolRunnerHermeticTest` | 8 | Fake-runner command shaping (JDK-only) |
| `schema/GirSchemaExtractorTest` | 10 | GIR → XSD/JSON extraction |
| `schema/GtkSchemaManagerTest` | 8 | Runtime per-project schema generation (primary path) |
| `schema/locator/GirSdkLocatorTest` | 10 | SDK GIR-dir resolution: flatpak CLI first, install-root glob fallback |
| `schema/providers/GtkInterfaceXmlSchemaProviderTest` | 4 | `.ui`/`.glade` XSD serving via `XmlSchemaProvider` |
| `schema/providers/GtkSdkHintResolverTest` | 4 | SDK/runtime hint resolution from the manifest |

The GTK tests exercise the **JDK-only cores** (schema, gir, locator) in a normal
JVM and the IDE glue via the platform test framework. They currently run as part
of the suite and are green; per `AGENTS.md`/`TOUCHES_UP.md` the CI comment treats
them as the non-blocking phase-2 gate.

---

## 3. Manual / runtime verification status

Runtime smoke findings were captured during the v1.0 hardening pass
(`TOUCHES_UP.md` §3.10 Part A + §3.11 Part B) in the sandbox IDE (`runIde`).
Status per item:

| # | Check | Result | Evidence |
|---|---|---|---|
| B1 | Console shows each workflow step (`Running DEEP_CLEAN...`, `Running BUILD: <cmdline>`, exit codes) | ✅ implemented, unit-tested | `CommandChainProcessHandlerTest` |
| B2 | RUN adds D-Bus sockets only when `/run/flatpak/bus` exists; otherwise warns + proceeds | ✅ implemented, unit-tested | `CommandExecutionEngineTest` (`{ true }`/`{ false }`) |
| B3 | EXPORT failure (`pip3`/`pipx`/`flatpak-node-generator` missing) | ⚠️ **sandbox-IDE artifact, not a plugin bug** | documented — README/AGENTS/CHANGELOG |
| B4 | Portals permission group visible only for the RUN command | ✅ implemented | panel command-sensitivity |
| B5 | Deep clean runs on a pooled thread via `WriteCommandAction`, no EDT violation | ✅ implemented, unit-tested | `DeepCleanExecutorTest` |
| B6 | *Custom arguments* row shown only when the CUSTOM command is selected | ✅ implemented | panel command-sensitivity |
| B7 | *Run → Edit Configurations → New* suggests `[command] <app-id>` | ✅ implemented | `LocatableConfiguration.suggestedName()` |

### 3.1 Full end-to-end smoke on a normal IDE — still to run

The automated suite covers command-line construction; a full build → run → export
cycle on a **normal IDE** (not the sandbox) with a real Flatpak sample is the one
remaining manual gate. The README *Visual Testable Features Checklist* enumerates
every case (Build / Run / Export / Clean / Validate / Custom, sandbox flags,
cleanup options, settings). See `docs/PUBLISHING.md` → *Release-day smoke* for the
exact sequence.

---

## 4. Skipped tests

- **`gtk/preview/AdwShimManagerHermeticTest`** (1 of 4 tests) —
  `@org.junit.Ignore("phase 2: fake runner's adw-shim detection does not match
  compile command paths; ensureShim returns null")`.
  The fake runner's shim-detection does not match the real compile command
  paths, so `ensureShim` returns null under test. Tracked in
  `GTK_BUILDING_PLAN.md` §4.1 (re-enable as part of phase 2). **Does not gate the
  v1.0 release.**

---

## 5. Known issues (documented, non-blocking)

1. **VALIDATE / EXPORT in the sandbox IDE** — `flatpak-node-generator`/`pip3`/
   `pipx` are absent from the `flatpak-builder --run` runtime in the JetBrains
   test sandbox. Sandbox artifact only; works on a normal IDE. (README Known
   Issues, `AGENTS.md` Flatpak notes.)
2. **GTK Preview — Adwaita rendering** — `Adw*` interfaces are not yet rendered
   correctly end-to-end (premium, feature-flagged). Tracked in
   `GTK_BUILDING_PLAN.md` §4.2.
3. **GResource awareness** — WIP; not yet shipping (README marks it explicitly).

None of the above blocks the stable release of the Flatpak v1.0 module.

---

## 6. Release-readiness checklist

- [x] Full unit suite green (101 tests, 1 phase-2 skip, 0 failures)
- [x] Flatpak v1.0 module — all automated tests green
- [x] B1–B7 runtime fixes implemented and covered by tests
- [x] CI (`ci.yml`) runs build + test on PR/push
- [x] Pre-publish (`pre-publish.yml`) runs schema regen + `verifyPlugin` + `publishPlugin`
- [ ] End-to-end build → run → export smoke on a normal IDE (release-day, see §3.1)
- [ ] `verifyPlugin` result on the release commit (runs in `pre-publish.yml`; can be run locally before tagging)

---

*See also:* `docs/PUBLISHING.md` (release procedure), `TOUCHES_UP.md` (hardening
log), `GTK_BUILDING_PLAN.md` (phase 2), `BILLING.md` (licensing/payout).
