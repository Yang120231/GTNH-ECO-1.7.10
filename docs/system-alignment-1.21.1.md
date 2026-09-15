> Superseded for crafting planning: see native-gtnh-fastpath-host-ui.md. GTNH now owns planning and scheduling; ECO retains fastpath only.

# Three-system alignment work log

Target: sibling `NeoECOAEExtension-1.21.1`, reference commit
`f25faed50c431ceb4310b89e89acad8ee0166573`. The working source, including any
uncommitted reference changes, is authoritative; guidebook prose is not.

User requirement: strictly align crafting, computation and storage behavior.
This is ongoing implementation, not a completed parity certification.

## Implementation and verification ledger

### Planner and graph UI continuation (2026-09-15)

- Production now uses the component planner, full execution plan and Version 2
  runtime. The old linear planner sources and Version 1 runtime were removed
  in the preceding interrupted continuation; earlier ledger entries describe
  historical state rather than the current production wiring.
- Verified the newly ported single-pattern growth solver with the full suite.
  The seedless self-loop remains CYCLE_UNRESOLVED, matching the reference
  ComponentPlanner, while retaining exact required seed and shortfall metadata.
  Corrected the regression's incompatible MISSING_ITEMS expectation.
- Replaced the obsolete JSON confirmation overlay with the structured report
  interface and a graph entry button. Added English/Chinese graph labels and
  fixed cross-package stack comparison and tooltip types. Opening the graph
  preserves the confirmation container so returning can retain the job.
- Full `spotlessApply build --offline` passed: 83 tests, Checkstyle, Spotless
  and reobfuscated jar. `git diff --check` passed. No live client visual or
  machine dispatch acceptance was performed; full parity is not certified.


### Execution-plan persistence and binding continuation (2026-09-15)

- Ported full plan NBT methods from upstream ExecutingCraftingJob into
  ECOExecutionPlanCodec, including allocations, ordered runs, dynamic counts,
  phase dependencies and initial seeds. ECOGtnhExecutionCodec uses the pinned
  GTNH Platform stack codec and ICraftingPatternItem decoding.
- Added submitted-task binding and a version-2 production runtime wrapper;
  CPU mixin can install a supplied full plan and restore its plan/cursors.
  Acceptance bridges GTNH's extra-count-before-return / first-count-after-return
  timing. Version-1 jobs keep the old cursor format.
- Fixed PatternIdentity.equals comparing the argument named value instead of
  its field. Fixed the mechanical record converter for nested generic fields
  and field/parameter name shadowing.
- Ported original ECOExecutionPlanBuilder, ECOExecutionSchedule topology,
  ECOExecutionRequirement, component outcomes, material provenance, normalized
  semantics and SelectedExecutionGraph. GTNH semantic adapter currently covers
  the existing exact/no-container snapshot contract only; it does not prove
  substitution/remainder or stateful fastpath contracts.
- Tests cover full-plan round-trip with 4-billion dynamic counts, allocations,
  dependencies, seed ownership, binding count rejection, runtime restart after
  accepted progress, physical output/return edges and separate DAG phases.
- IMPORTANT: ECOCraftingJob still constructs the legacy runtime. The new CPU
  install entry is infrastructure, not a claim of production cutover. Numeric
  planning must supply genuine component results and provenance, and dispatch
  must wire concrete seed protection/consumption before switching cycle jobs.
  Complete fastpath cache/verifier and real-device/cross-chunk acceptance remain
  open. Current validation: spotlessApply build passed, 98 tests.

### Provider transaction continuation (2026-09-15)

- Ported upstream ECOProviderInputTransaction and ECOCraftingEnergyTransaction.
  Ordinary GTNH CPU extra batch inputs now use the upstream ownership transaction;
  ordinary and computation virtual CPU batches reserve extra energy before the
  provider accepts work and refund it on rejection. The old persisted ordinary
  CPU debt remains readable for existing saves.
- Verified against rv3-beta-990-GTNH source: MECraftingInventory injection is
  local and non-rejecting. IEnergyGrid.injectPower returns already-stored grid
  overflow, NOT rejected energy. The refund boundary therefore returns no
  retained credit after successful injection and converts CONFIG extraction
  units back to raw AE injection units. Refund exceptions retain prepaid credit.
- The inherited CPU NBT mixin persists the prepaid credit. This change does not
  switch production to runtime/ported/ECOExecutionRuntime: complete plan building,
  physical task/phase binding and seed protection still need implementation.
- Tests cover partial energy debit, CONFIG-scaled refund, stored overflow,
  failed refund credit reuse, and idempotent provider input ownership settlement.
  No real device or cross-chunk crash acceptance is claimed for this continuation.

### Source-port continuation (2026-09-15)

- Replaced the hand-written strict ring with the upstream BoundedCycleSolver,
  including exact ring counts/witness, seed ladder, greedy lookahead, bounded
  marking search, metrics and diagnostic verdicts. Ported iterative Tarjan,
  condensation and PlannerAmount. tools/port_planner.py documents mechanical
  package/type/Java-8 transformations; compile boundary uses legacy immutable
  recipe snapshots. Record value equality is retained for condensation keys.
- Wired SCC solving into ECOPlanningEngine and prepare external demand before
  replay, so successive imports do not consume the supplier SCC's startup stock.
  Tests exercise side inputs/byproducts, separate SCCs, exact-ring diagnostic,
  seed-ladder diagnostic and material replay. Legacy DAG/route selection and
  single-pattern growth are still present; this is not a full ComponentPlanner port.
- Ported ECOBatchCraftingHelper's batch arithmetic, energy search and extraction
  rollback through a BatchInventory interface. Virtual CPU energy preview now
  calls that upstream policy. Other dispatch paths still need conversion.
- Ported the upstream execution runtime's phase/candidate/progress state machine
  and execution-plan shape validation into runtime/ported. Java-8 NBT codec
  preserves cursors, ordered remaining counts, dynamic counts and seed ownership.
  Independent-phase/non-acceptance/accepted-progress and NBT round-trip tests are
  present. Production still uses the legacy runtime: full plan generation,
  task binding, actual provider transactions and startup seed inventory gating
  must be integrated before switching it. The schedule currently contains only
  its immutable contract; the modern physical-graph builder is not ported yet.
- Source file hashes are recorded in docs/planner-port-sources.json. The source
  port script covers only the solver/graph subset; runtime and fastpath changes
  are manual API-boundary adaptations, not regenerated by that script.
- User explicitly expanded scope to the executor and fastpath strategies.
  Next source chain: PlanIdentity / execution plan + schedule + phase scheduler,
  api/me/ECOExecutionRuntime, provider input and energy transactions, fastpath
  verifier/cache/credential/stateful models and dispatcher. Existing runtime is
  still the legacy ordered cursor; it is NOT accepted as equivalent.
- ReferenceOwnershipLedger, OwnershipEvent and PendingChoiceGroup were copied
  as replay specifications, not wired as a replacement runtime ownership ledger.
- No real-device or process-crash acceptance was performed in this continuation.

### Strict integer ring continuation (2026-09-15)

- Added BigInteger balance/closure solving for reachable deterministic rings of
  at least three single-input/single-output recipes, ahead of recursive planning.
  Executable batches are capped by real inventory and replayed before publication.
  Regressions cover a billion-item three-stage ring from each seed position and
  refusal to publish a seedless integer solution. This strict subset does not
  cover side inputs, general SCC decomposition or seed-ladder diagnostics.
- Corrected the crash fixture's remaining Runtime.halt calls to wait for the
  external kill harness, matching the documented Forge restriction. No new
  process-kill or real-device runtime acceptance was performed in this continuation.
- Real switch cabling/dispatch and arbitrary cross-chunk interruption recovery
  remain open; previous fixture PASS records must not be read as their acceptance.


### Large-cycle and runtime audit (2026-09-15)

- Added maximal-safe batch probes and one/target/unblocking/maximal batch edges
  from the modern solver's marking-search approach. Recursive circulation now
  hands off before exhausting the search budget. A billion-item two-pattern ring
  passes with fewer than 100 material-replayed steps. Exact-ring integer solving,
  SCC boundaries and seed-ladder diagnostics are still not fully ported.
- Processing-source uniqueness now counts real `getMediums` identities rather
  than pattern identities. Verification keys include the actual world and
  pattern object; positive verification expires as well as negative verification.
  Forge-runtime fixtures passed exact output/count, multi-output rejection and
  one-pattern/two-provider cardinality checks. These use fixture providers, not
  a third-party machine dispatch test.
- Added checked compressed-NBT journal writes using fsync and atomic replacement;
  vanilla MapStorage's swallowed errors are no longer the restore commit barrier.
  Completed snapshots remain as tombstones to repair sealed members from stale
  chunks, including after the controller releases ownership. Portable cells are
  never replayed. In-memory completion alone cannot authorize recovery.
- Ran an isolated Forge 1.7.10 server (Java 8, 55 mods, localhost:25579,
  `run/audit-server`, world `audit-world`). Real external process termination at
  prepared and completed durable checkpoints was followed by a third startup:
  journal reload, stale-member replay and no portable replay all PASSED.
  The crash payload is a journal fixture, not a fully formed storage multiblock;
  arbitrary region-file write interruption and power-loss hardware guarantees
  remain outside this evidence. Completed tombstones currently retain snapshots.
- Forge host fixtures passed sorted eight-host partitioning, ninth-host isolation,
  shared overclock, frequency split/refill and leader unload. Grid identity is a
  fixture; real switch cabling, tick dispatch, cooling and endgame mode are still
  integration gaps.

Evidence: `build/audit-prepare-v2.log`, `build/audit-commit-v2.log`,
`build/audit-verify.log`. Both killed runs intentionally return nonzero; the
verify run exits normally with all four integration PASS messages. Initial
`Runtime.halt` attempts were trapped by Forge and are not counted as crash tests.
The opt-in crash fixture waits for an external kill only in `audit-world`.
IDEA MCP remained unavailable; all changes preserved the prior working tree.
Final validation: `spotlessApply build` PASSED (82 tests, Checkstyle, Spotless,
reobfuscated jar), and `git diff --check` passed. No audit server remains running.

### Focused audit continuation (2026-09-15)

- Multi-pattern cycles: added a bounded forward-search fallback with isolated
  inventories, duplicate-marking elimination, executable firing schedules and
  shared cancellation/time/work budgets. Regressions cover temporary goal
  consumption, interleaved growth and a finite non-growing cycle. This is not
  the modern SCC/seed-ladder macro-step solver; large-cycle parity is still open.
- Fast paths: pattern keys now compare defensive NBT snapshots, not only hashes;
  a collision regression uses `Aa`/`BB`. A single observed output no longer
  verifies multiple declared outputs. Provider uniqueness, contextual cache
  lifetime and multi-output processing contracts still need integration work.
- Restore: discard sealed-cell backend caches when unsealing, and save released
  controller ownership with unsealed matrices before deleting the journal.
  Added journal reload/idempotence/defensive-copy coverage. Cross-chunk atomicity,
  swallowed vanilla save failures and actual process-kill recovery are not
  certified by these NBT tests.
- Clusters: coordinate-sort members before frequency allocation and eight-host
  partitioning; synchronize overclock/cooling settings from that stable leader.
  Frequency tests cover 32 hosts, overflow allocation and filling a freed slot.
  Live topology changes, balanced dispatch and eight-host virtual modes still
  require game integration validation.

Validation: `spotlessApply build` passed, 80 tests, Checkstyle, Spotless and
reobfuscated artifact generation; `git diff --check` passed. IDEA MCP unavailable.
An attempted AE output-matching unit fixture required unavailable game/LWJGL
initialization and was removed; that branch has no new standalone unit coverage.
Existing uncommitted migration work was preserved. No game was launched.

### Continuation checkpoint (2026-09-15)

The interrupted task left implementations for fluid cells, network switches,
frequency grouping, pooled computation, crafting dispatch and infinite restore
in the working tree. The older pending entries below are not an inventory of
missing files; their behavior still requires a complete parity audit.

This continuation corrected growth planning: reserve startup seed while planning
external inputs, reject gross-output overflow at the AE2 long boundary, and keep
goal inventory available as seed when requesting additional crafted output.
Added regressions for seed contention, gross overflow and additional requests;
updated the old doubling-schedule assertion for compact sequential phases.

Validation: local Gradle `spotlessApply build`, including 75 tests, Checkstyle,
Spotless and reobfuscated artifact generation. IDEA MCP was unavailable.
No game runtime validation was performed. General bounded multi-pattern cycle
solver parity, fast-path provider contracts, restore crash recovery and network
topology/settings parity remain unverified; this is not full migration sign-off.

- Implemented: FT extra contribution 32/96/384; computation accelerators
  64/192/576; correct overflow ratio and fixed baseline FX capacity.
- Implemented: each task CPU receives the full host accelerator count.
- In progress: one batch per physical FX lane, FT-independent admission,
  fractional power progress and persistence. Controller-owned state must still
  preserve physical lane semantics, output ownership and restart recovery.
- Pending: crafting and computation switches, frequency grouping, shared
  settings/resources, topology refresh, balanced dispatch and eight-host modes.
- Pending: planner graph/cycle/growth/exact-amount semantics, fast paths,
  transaction/ownership scheduling and compatible external providers.
- Implemented: normal storage sizes 16/64/256 MiB, oversized legacy contents
  remain extractable without allowing new inserts, corrected matrix tier labels
  and localized capacity names. Resource-type separation remains pending.
- Implemented: 12-source infinite admission permits non-L9/empty slots, rejects
  foreign domain members and transfer mode, and keeps nonmember cells mounted.
  Durable nonempty restore to normal cells and migration crash consistency remain pending.
- Pending: tier-aware structure constraints and mixed component tiers,
  recipes, UI, localization, storage transfer and lifecycle parity audit.
- Pending: cross-version contract fixtures, regression tests, complete build,
  and available runtime validation. Do not infer parity from compilation alone.

## Checks so far

Current changes passed `spotlessApply build` through IDEA, including tests,
Checkstyle and Spotless. Mixed storage admission and physical lane behavior
still need integration-level regression coverage and runtime validation.
Fractional power progress now marks persistent state dirty; occupied FX display
uses the actual number of occupied lanes instead of marking all workers busy.

## Reference anchors

- `api/ECOTier.java`
- `api/me/network/CraftingCapabilitySnapshot.java`
- `multiblock/cluster/NECraftingNetworkCluster.java`
- `multiblock/cluster/NEComputationNetworkCluster.java`
- `api/me/worker/ECOCraftingThread.java`
- `impl/crafting/planner/ECOCraftingPlannerService.java`
- `blocks/entity/storage/ECOStorageSystemBlockEntity.java`
- `blocks/entity/storage/ECOStorageInfiniteRestore.java`
