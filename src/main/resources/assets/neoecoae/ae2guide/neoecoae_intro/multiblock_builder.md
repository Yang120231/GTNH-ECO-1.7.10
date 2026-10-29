---
navigation:
  title: Building with StructureLib
  icon: neoecoae:crafting_system_l9
  parent: neoecoae_intro/index.md
---

# Building with StructureLib

ECO multiblocks are built with StructureLib's Structure Hologram Projector, the same tool used for GregTech multiblocks.

## Supported Systems

- [ECO Storage System](storage_system.md)
- [ECO Computation System](computation_system.md)
- [ECO Crafting System](crafting_system.md)
- [Large Integrated Working Station](large_integrated_working_station.md)

## Using the Projector

1. Place the controller and confirm its facing.
2. Right-click the controller with the projector to show the hologram.
3. Sneak + right-click the controller to place blocks.

In creative mode the whole structure is placed at once. In survival mode each click places a batch of blocks and consumes them from your inventory; click again until the structure is complete.

## Length and Mirror

- The projector's stack size selects the structure length. It is clamped to the range the system supports.
- The `length` channel overrides the stack size, for lengths above 64.
- Set the `mirror` channel to `1` to build the mirrored layout.

Channels are configured in the projector's own settings screen.

## Blocked Positions and Missing Materials

Existing correct blocks are kept. Positions holding other non-replaceable blocks are skipped and reported in chat; clear them and click again. When a material runs out, building stops and the missing item is reported.

## NEI Preview

With BlockRenderer6343 installed, NEI shows each ECO structure in its multiblock preview tab, including the required blocks.
