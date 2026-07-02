# Infinite Storage Engine Design

This document describes the target storage architecture for the ECO infinite storage host.

The current 1.7.10 port already separates portable cells from host-domain storage, but both paths still use the same core backend shape:

```text
ECOStorageBackend
`- LinkedHashMap<ECOStorageKey, ECOAmount>
```

Portable cells serialize the full backend into item NBT. Infinite host domains serialize the full backend through `WorldSavedData`. This is simple and correct for small systems, but it does not scale well when late-game automation creates a high write rate, a large type count, or many NBT-distinct item variants.

The long-term target is a small storage engine instead of a larger map.

## Goals

- Keep normal insert and extract operations bounded and cheap on the server thread.
- Avoid full NBT serialization after every meaningful storage change.
- Avoid full inventory scans when AE2 asks for available items.
- Keep hot data in memory and cold data on disk.
- Store data in shards so one large domain does not become one large save operation.
- Make world save, chunk unload, and server stop perform predictable bounded work.
- Preserve crash recovery with a write-ahead log.

## Non-Goals

- True mathematical infinity.
- Keeping every stored key resident in JVM heap forever.
- Writing every storage change directly to a final disk file.
- Replacing AE2's storage API.

## Current Bottlenecks

The current backend has three important scaling risks:

```text
full memory residency
full list rebuilds
full NBT persistence
```

Large quantities of a few item types are manageable. The hard case is a large number of distinct keys, especially keys with NBT fingerprints.

The expensive paths are:

- A new `ECOStorageKey` and `ECOAmount` object for every distinct item or fluid variant.
- `getAvailableItems()` rebuilding AE2-visible lists after the backend revision changes.
- Portable cells writing the full `ECOStorage` tag after modulated changes.
- Infinite domains writing all entries through `WorldSavedData`.
- World autosave serializing the entire domain in one pass.

## Target Architecture

Use an LSM-style sharded key-value backend:

```text
ECOStorageEngine
|- MemTable
|  `- in-memory key -> delta amount
|- Write-Ahead Log
|  `- append-only operation records
|- Shards
|  |- shard_000
|  |  |- manifest
|  |  |- segment_000.sst
|  |  |- segment_001.sst
|  |  `- compacted.sst
|  |- shard_001
|  `- ...
|- Caches
|  |- hot key cache
|  |- shard index cache
|  `- AE2 available-list cache
`- Background Worker
   |- flush immutable memtables
   |- compact shard segments
   `- checkpoint WAL progress
```

This follows the same broad design used by log-structured storage engines: write small sequential records first, then batch final layout work later.

## Data Placement

TileEntity NBT should store only controller-local state:

```text
host mode
domain id
priority
infinite component stack
small UI/config fields
```

`WorldSavedData` should store only small world-level metadata:

```text
data version
domain ids
domain manifest pointers
last committed sequence
```

Large storage contents should live outside TileEntity NBT and outside the `WorldSavedData` entry list:

```text
world/
`- neoecoae_storage/
   `- dim_0/
      `- domain_<uuid>/
         |- manifest.dat
         |- wal_000.log
         |- shard_000/
         |- shard_001/
         `- ...
```

## Sharding

Assign each key to a stable shard:

```text
shardId = hash(encodedKey) & (shardCount - 1)
```

Recommended initial values:

```text
shardCount: 256
hash: stable non-cryptographic 32-bit hash
```

Each shard owns its own segment files and compacted data. This keeps save, load, and compaction work local.

## Write Path

The server-thread write path should be short:

```text
insert/extract
-> encode key
-> compute shard
-> update MemTable delta
-> append WAL record
-> update hot cache and dirty key set
-> return to AE2
```

The operation should not serialize the whole domain. It should not rewrite the shard file synchronously.

Example WAL record:

```text
sequence
operation: add/subtract
domain id
shard id
encoded key
delta amount
checksum
```

The WAL can be binary and varint-encoded later. The first implementation may use a simpler framed binary format as long as it is append-only and recoverable.

## Read Path

Single-key reads should prefer hot memory:

```text
1. MemTable delta
2. hot key cache
3. shard index cache
4. shard segment lookup
5. compacted base value
```

The result is:

```text
base amount + pending memtable delta
```

Hot items should resolve without disk reads. Cold items pay the cost only when requested.

## Flush And Compaction

When the MemTable reaches a configured size, freeze it:

```text
active MemTable
-> immutable MemTable
-> background flush to shard segments
-> update manifest
-> advance WAL checkpoint
```

Compaction merges several shard segments into a smaller set of sorted files:

```text
segment_000 + segment_001 + segment_002
-> compacted_003
```

Compaction must run under a time and IO budget. It should never monopolize the server thread.

## AE2 Available Items

AE2 inventory listing is one of the most dangerous paths because a complete list can be very large.

Maintain an incremental visible index:

```text
AvailableItemsIndex
|- revision
|- dirty keys
|- cached AE2 list
`- optional page cache
```

On storage mutation:

```text
dirtyKeys.add(key)
revision++
```

On `getAvailableItems()`:

```text
if cached revision matches:
    return cached list copy
else if dirty key count is small:
    patch cached list
else:
    schedule background rebuild and return last stable snapshot
```

The ideal behavior is eventual consistency for large list rebuilds and immediate consistency for direct insert/extract correctness.

## Cache Policy

The first implementation can use bounded LRU caches:

```text
hot key cache
loaded shard index cache
AE2 list/page cache
```

For maximum late-game performance, evolve toward a TinyLFU-style admission policy:

```text
recent window
frequency sketch
main protected cache
```

The cache limit should be byte-based, not just entry-count-based, because NBT-heavy keys can be much larger than normal item keys.

## Crash Recovery

Recovery should replay WAL records after the last committed manifest sequence:

```text
load manifest
load compacted shard state
replay WAL records with sequence > checkpoint
rebuild MemTable and dirty indexes
```

Each WAL frame should include enough structure to detect a torn final write. A corrupted or incomplete tail record should be ignored only if all earlier records are valid.

## Early, Mid, And Late Game Benefits

Early game:

- Inserts and extracts stay cheap.
- Saves remain small.
- Players see fewer occasional stalls from cell NBT rewrites.

Mid game:

- Automation writes become sequential log appends.
- Repeated ore-line items stay hot in cache.
- AE2 list generation can reuse stable cached state.
- Save work is spread across ticks and background tasks.

Late game:

- Cold data can leave heap memory.
- Large type counts do not require every key to stay resident.
- World autosave no longer serializes one massive domain map.
- Shards limit the blast radius of flush and compaction work.
- Server-thread work can be capped with explicit budgets.

## Migration Plan

1. Keep the existing `ECOStorageBackend` as the compatibility model.
2. Add a `StorageEngine` interface for domain storage operations.
3. Implement an adapter backed by the current `ECOStorageBackend`.
4. Move infinite domains from full `WorldSavedData` NBT to an external domain directory.
5. Add append-only WAL for domain mutations.
6. Add sharded flush files and a manifest.
7. Add bounded caches and an incremental AE2 visible index.
8. Add compaction and recovery tests.
9. Keep portable cells on NBT unless they also need large-scale behavior.

## Phase 1 Enhanced Backend

The first implementation step keeps the existing full-map backend and improves the change-tracking boundary around it.

Implemented scope:

- `ECOStorageBackend` records a bounded dirty-key history tied to backend revisions.
- Consumers can ask whether dirty history is complete since a cached revision.
- Portable cell and host-domain AE2 handlers share one `ECOAvailableItemsCache` helper.
- The cache helper centralizes the current full-list rebuild path.
- The same helper is the replacement point for a future custom incremental visible index.

This is intentionally conservative. AE2's `IItemList` exposes add and lookup operations, but not a safe remove/update primitive for an existing stored stack. Because of that, phase 1 does not mutate AE2's list in place when an item reaches zero or changes amount. It records the information needed for incremental work, then still falls back to rebuilding the visible list.

The next phase can replace the internal cache representation with a mod-owned index:

```text
ECOVisibleItemIndex
|- key -> StackType
|- revision
|- dirty key patching
`- AE2 list materialization
```

At that point dirty-key history can update the mod-owned index safely, and `IItemList` only needs to be materialized when AE2 asks for a list snapshot.

## Recommended First Interface

```java
public interface ECOStorageEngine {
    ECOAmount insert(ECOStorageKey key, ECOAmount amount, boolean simulate);

    ECOAmount extract(ECOStorageKey key, ECOAmount amount, boolean simulate);

    ECOAmount getAmount(ECOStorageKey key);

    ECOStorageSnapshot snapshotHotView();

    long getRevision();

    void flushBudgeted(long maxNanos);

    void closeAndFlush();
}
```

The interface should keep AE2 integration independent from the physical storage layout.

## Design Principle

The performance goal is to move from:

```text
more data makes every operation heavier
```

to:

```text
hot operations stay cheap
cold data is paid for on demand
save work is batched and budgeted
large domains are processed by shard
```

This is not an infinitely large chest. It is a dedicated storage engine for an AE2 infinite storage host.
