# Batch 1 — Phase 0–4 Implementation Record

**Project:** ThiefAlertSeQure  
**Working branch:** work/phase-0-12-coding  
**Baseline before this batch:** d174669ec522a77ab24b95999c0cf0ece606c9f1

## Batch scope

- Phase 0 — Preserve and audit the repository baseline.
- Phase 1 — Security architecture and persistent state.
- Phase 2 — Permission center and device capability engine.
- Phase 3 — Charger and trusted Bluetooth protection.
- Phase 4 — Bypass Charging integration framework.

The repository's historical FIXALL checklist uses a different internal phase numbering. This record preserves the agreed product-roadmap numbering without rewriting that historical ledger.

## Phase 0–3 baseline review

The branch already contains the core implementation for Phases 0–3: production identity and SDK invariants, authoritative protection state and persistence, setup/readiness checks, charger event validation and 10-second countdown, encrypted trusted Bluetooth registry and 8-second A2 separation logic. Existing features were preserved rather than duplicated or silently redesigned.

Exact-head baseline checks on d174669ec522a77ab24b95999c0cf0ece606c9f1:
- Android workflow [#44013936060 — SUCCESS](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44013936060).
- CodeQL workflow [#44013936266 — SUCCESS](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44013936266).
- Instrumentation-disabled guard passed. No instrumentation, emulator, Codespaces, or physical-device testing was performed.

## Phase 4 implementation

The new Bypass Charging controller, adapter contract, fail-closed registry, read-back verification policy, Settings UI and JVM regression tests are added in this batch. The adapter registry remains empty because no public, model/firmware-specific third-party ON/OFF API has been verified. The switch therefore remains disabled and actual mode remains UNKNOWN without a certified adapter.

This is an intentional safety boundary, not a claim that OEM system Bypass Charging is absent. Owners can still use any Bypass Charging feature exposed by their phone's own Settings or gaming tools.

## Verification result

The non-physical implementation passed on code-bearing commit e8aabcb4dc42df7817b4ec4c2fb7e7c1ac392523:
- Android build and tests: [run #44048497535 — SUCCESS](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44048497535).
- CodeQL: [run #44048497474 — SUCCESS](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44048497474).
- JVM tests, Bypass Charging privilege/state guard, instrumentation-disabled guard, Android lint, manifest/protection invariants, APK build and metadata checks all passed.
- Development debug artifact: [artifact #14312886783](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44048497535/artifacts/14312886783), SHA-256 628cd98ceab0e24af26d29f0502862456ed4e3d2f8615cc85130084a2b19a945. The minified release APK passed metadata verification but remains unsigned because permanent owner signing secrets are not configured.

**Batch 1 non-physical implementation is complete for the available software scope.** Direct OEM charging control remains blocked until a documented and authorized model/firmware-specific integration is available; no universal third-party ON/OFF capability is claimed.
