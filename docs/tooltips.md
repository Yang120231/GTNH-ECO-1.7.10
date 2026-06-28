# Tooltip Pipeline

This document records the lightweight tooltip convention used by the 1.7.10 port.

## Runtime Entry Points

Block item tooltips are handled by:

- `cn.dancingsnow.neoecoae.block.ItemBlockTooltip`
- `cn.dancingsnow.neoecoae.client.tooltip.NETooltips`

`ItemBlockModernModel` and `ItemBlockModelDrive` extend `ItemBlockTooltip`, so modern model blocks and drive blocks use the same tooltip path. Plain cube blocks that need tooltips should be registered with `ItemBlockTooltip.class`.

Example:

```java
GameRegistry.registerBlock(storageCasing, ItemBlockTooltip.class, "storage_casing");
```

Blocks registered with vanilla `GameRegistry.registerBlock(block, id)` do not receive this custom tooltip behavior.

## Lang Key Convention

Tooltips are data-driven through lang keys. Java does not keep a per-block tooltip table.

For a block whose unlocalized name is `tile.crafting_worker`, define:

```properties
tile.crafting_worker.tooltip.summary=&7ECO - FX 工作核心是合成子系统的主要部分
tile.crafting_worker.tooltip.line.0=&7ECO - FX 工作核心可缓存 32 个合成任务，每次处理 1 个合成任务
tile.crafting_worker.tooltip.line.1=&7启用超频时：
tile.crafting_worker.tooltip.line.2=  &7任务缓存容量：&a128&7 / &b256&7 / &d512
```

Behavior:

- `*.tooltip.summary` is always shown.
- If Shift is not held, `tooltip.neoecoae.hold_shift` is shown and detail lines are hidden.
- If Shift is held, `*.tooltip.line.0`, `*.tooltip.line.1`, etc. are shown in order.
- Missing tooltip keys are ignored.
- `&` color codes are converted to Minecraft `§` formatting at runtime.

## Style Rules

Keep machine tooltips compact and consistent:

- Use gray base text: `&7`.
- Do not use italic by default.
- Use tier colors for compact tier values:
  - L4/F4/C4: `&a`
  - L6/F6/C6: `&b`
  - L9/F9/C9: `&d`
- Prefer compact tier rows over one line per tier:

```properties
tile.crafting_worker.tooltip.line.2=  &7任务缓存容量：&a128&7 / &b256&7 / &d512
tile.crafting_worker.tooltip.line.3=  &7功耗倍率：&a×4&7 / &b×8&7 / &d×16
```

- Avoid `[L4]`, `[L6]`, `[L9]` when color already identifies the tier.
- Use full-width Chinese punctuation in `zh_CN.lang` and concise English in `en_US.lang`.

## Current Coverage

The current tooltip pass covers:

- `storage_casing`, `crafting_casing`, `computation_casing`
- `storage_vent`, `crafting_vent`
- `storage_interface`, `crafting_interface`, `computation_interface`
- `input_hatch`, `output_hatch`
- `eco_drive`, `computation_drive`
- `crafting_pattern_bus`, `crafting_worker`
- `computation_transmitter`
- `storage_system_l4/l6/l9`
- `crafting_system_l4/l6/l9`
- `computation_system_l4/l6/l9`
- `computation_cooling_controller_l4/l6/l9`

`aluminum_alloy_casing` and `black_tungsten_alloy_casing` also have basic structure tooltips, but they still use the older short structure wording.

## Verification

Run:

```bat
.\gradlew.bat build
```

For visual verification, open CreativeTab and check:

- summary is visible without Shift,
- detail lines are visible with Shift,
- tier values use green / aqua / purple,
- no raw lang keys appear in the tooltip.
