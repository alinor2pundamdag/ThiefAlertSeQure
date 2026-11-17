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

## Completion rule

Batch 1 is not fully complete until new exact-head Android/JVM/source-contract/lint/APK checks and CodeQL pass. Direct OEM charging control remains blocked until an authorized OEM integration is available.
