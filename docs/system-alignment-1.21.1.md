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
- Pending: normal storage sizes/types, legacy oversized-cell preservation,
  12-source infinite admission and durable nonempty restore to normal cells.
- Pending: tier-aware structure constraints and mixed component tiers,
  recipes, UI, localization, storage transfer and lifecycle parity audit.
- Pending: cross-version contract fixtures, regression tests, complete build,
  and available runtime validation. Do not infer parity from compilation alone.

## Checks so far

Initial numeric/overflow/CPU changes passed `spotlessApply test` through IDEA.
Subsequent changes require fresh verification.

## Reference anchors

- `api/ECOTier.java`
- `api/me/network/CraftingCapabilitySnapshot.java`
- `multiblock/cluster/NECraftingNetworkCluster.java`
- `multiblock/cluster/NEComputationNetworkCluster.java`
- `api/me/worker/ECOCraftingThread.java`
- `impl/crafting/planner/ECOCraftingPlannerService.java`
- `blocks/entity/storage/ECOStorageSystemBlockEntity.java`
- `blocks/entity/storage/ECOStorageInfiniteRestore.java`
