# Item Migration Notes

The upstream item scan was generated in:

```text
E:\Minecraft Project\NeoECOAEExtension-1.21.1\migration-analysis
```

Important files:

```text
migratable-items.md
migratable-items.csv
```

## Current Port Status

Implemented:

| ID | Type | Notes |
|---|---|---|
| `aluminum_ingot` | simple item | First minimal migrated item. Uses upstream PNG texture. |

## Next Good Candidates

These are simple material items and should be straightforward:

```text
aluminum_dust
raw_aluminum_ore
tungsten_ingot
tungsten_dust
raw_tungsten_ore
aluminum_alloy_ingot
aluminum_alloy_dust
black_tungsten_alloy_ingot
black_tungsten_alloy_dust
energized_crystal
energized_crystal_dust
energized_fluix_crystal
energized_fluix_crystal_dust
crystal_ingot
crystal_matrix
energized_superconductive_ingot
cryotheum
cryotheum_crystal
```

## Resource Rule

Copy upstream item textures from:

```text
E:\Minecraft Project\NeoECOAEExtension-1.21.1\src\main\resources\assets\neoecoae\textures\item
```

to this port:

```text
src\main\resources\assets\neoecoae\textures\items
```

Forge 1.7.10 does not use the upstream `models/item/*.json` files for simple item icons.
