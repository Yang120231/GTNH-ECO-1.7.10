# ECO Controller Foundation

This document records the first functional foundation for ECO subsystem controllers in the 1.7.10 port.

## Scope

The current implementation gives the system controller blocks a real TileEntity identity.

Covered blocks:

- `storage_system_l4`
- `storage_system_l6`
- `storage_system_l9`
- `crafting_system_l4`
- `crafting_system_l6`
- `crafting_system_l9`
- `computation_system_l4`
- `computation_system_l6`
- `computation_system_l9`

Not covered yet:

- `computation_cooling_controller_l4/l6/l9`
- interfaces, hatches, drives, workers, pattern buses
- AE2 network connection
- multiblock structure scanning
- GUI opening
- AE2 network connection
- multiblock structure scanning
- GUI opening

## Core Classes

- `BlockECOController`
  - Extends `BlockDirectionalModernModel`.
  - Keeps the existing modern model rendering path.
  - Keeps horizontal placement metadata semantics.
  - Creates `TileECOController`.

- `TileECOController`
  - Stores subsystem type.
  - Stores tier.
  - Stores formed state.
  - Stores facing metadata.
  - Saves and loads NBT.
  - Provides a vanilla 1.7.10 description packet for client sync.

- `ECOControllerSubsystem`
  - `STORAGE`
  - `CRAFTING`
  - `COMPUTATION`

- `ECOControllerTier`
  - `L4`
  - `L6`
  - `L9`

- `NETileEntities`
  - Registers the controller TileEntity during preInit.

## Current Data Model

`TileECOController` currently persists:

```text
Subsystem: storage / crafting / computation
Tier: l4 / l6 / l9
Formed: boolean
FacingMeta: 0-3
```

The tier enum intentionally uses neutral `L4/L6/L9` ids internally. Rendering and display names can still expose subsystem-specific labels such as `F4` and `C4`.

## Formed Model Rendering

`TileECOController.formed` now drives world model selection for the supported controller models:

- `storage_system_l4/l6/l9`
  - `formed=false`: `storage_controller/controller_l*_off`
  - `formed=true`: `storage_controller/controller_l*_formed`
- `crafting_system_l4/l6/l9`
  - `formed=false`: `crafting_controller/controller_l*_off`
  - `formed=true`: `crafting_controller/controller_l*_formed`
- `computation_system_l4/l6/l9`
  - `formed=false`: `computation_controller/controller_l*_off`
  - `formed=true`: `computation_controller/controller_l*_formed`

Inventory rendering still uses the off model. This keeps CreativeTab previews stable while the world block reflects runtime state.

The upstream computation formed model uses a NeoForge composite model. The 1.7.10 lightweight loader supports the subset needed here by loading each child, resolving child-local texture references, and flattening the children into one baked element list. The translucent glass child currently renders in the normal block pass; a dedicated translucent pass can be added later if the visual needs stricter sorting.

## Formation Scanning

Controllers now run a server-side formation scan once per second and when the debug stick is used on them.

The scanner ports the 1.21.1 calculator rules for:

- storage systems
- crafting systems
- computation systems

The validation checks:

- controller subsystem and tier
- horizontal facing metadata
- mirrored and non-mirrored layouts
- repeat-line length and tail alignment
- component tier support
- required interface, casing, drive, vent, hatch, core, transmitter, and cooling-controller positions

The 1.21.1 behavior where a controller directly adjacent to another controller could still form is intentionally blocked here. Any controller touching another controller on one of the six sides fails formation.

Current scope:

- The controller block switches `formed` automatically.
- Component blocks are validated by block type, metadata facing, and tier where applicable.
- Blocks covered by the controller formed model are hidden through a lightweight visibility table.
- Component formed visuals and AE2 cluster ownership are not connected yet.

The 1.21.1 implementation stores `FORMED` on all members and uses `INVISIBLE` on selected casing blocks. Interfaces and hatches also render as invisible when formed. The 1.7.10 port currently mirrors the visible result without adding full component TileEntities: the controller records the member coordinates that should be hidden, and the render paths skip those blocks while the controller remains formed.

## Placement Behavior

The controller block still uses the existing metadata rule:

```text
0/1/2/3 = north/east/south/west
```

On placement:

1. `BlockDirectionalModernModel` writes metadata from player yaw.
2. `BlockECOController` copies that metadata into `TileECOController`.
3. The TE can later use this facing for network ports, UI orientation, or formed model state.

## Next Steps

Recommended next layer:

1. Add formed model switching for non-controller component blocks.
2. Persist and expose the validated component list.
3. Add AE2 grid connection through the controller TE.

Avoid attaching AE2 cable logic directly to static model blocks. The controller TE should be the ownership point for subsystem state.
