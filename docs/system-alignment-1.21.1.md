# Three-system alignment work log

Target: sibling `NeoECOAEExtension-1.21.1`, reference commit
`f25faed50c431ceb4310b89e89acad8ee0166573`. The working source, including any
uncommitted reference changes, is authoritative; guidebook prose is not.

User requirement: strictly align crafting, computation and storage behavior.
This is ongoing implementation, not a completed parity certification.

## Implementation and verification ledger

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
