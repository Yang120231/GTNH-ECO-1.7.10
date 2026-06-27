# Migration Map

This is a compact map for translating 1.21.1 NeoForge code into Forge 1.7.10 / GTNH code.

## Identity

Keep these aligned with upstream:

```text
mod id: neoecoae
package: cn.dancingsnow.neoecoae
display name: Neo ECO AE Extension
license: GPLv3
upstream maintainer: DancingSnow0517
GTNH port maintainer: Yang120231
```

## Registries

Modern NeoForge / Registrate:

```java
REGISTRATE.item("aluminum_ingot", MaterialItem::new).register();
```

Forge 1.7.10:

```java
public static final Item aluminumIngot = new Item()
    .setUnlocalizedName("aluminum_ingot")
    .setTextureName(NeoECOAE.MODID + ":aluminum_ingot");

GameRegistry.registerItem(aluminumIngot, "aluminum_ingot");
```

Register simple items during `FMLPreInitializationEvent`.

## Resources

Modern item model and texture:

```text
assets/neoecoae/models/item/aluminum_ingot.json
assets/neoecoae/textures/item/aluminum_ingot.png
```

Forge 1.7.10 item texture:

```text
assets/neoecoae/textures/items/aluminum_ingot.png
```

Forge 1.7.10 localization:

```text
assets/neoecoae/lang/en_US.lang
assets/neoecoae/lang/zh_CN.lang
```

## AE2 And NEI

Use GTNH AE2 APIs, not modern AE2 APIs.

Modern JEI/EMI recipe categories must be redesigned as NEI handlers for 1.7.10.

Modern AE2 storage key/cell APIs need mapping to the GTNH AE2 fork before implementation.

## Rendering

Modern block/item JSON models are not directly usable by Minecraft 1.7.10.

Simple items can reuse the PNG texture with `setTextureName`.

Complex blocks and formed multiblock visuals will need one of:

- simple block icons
- ISimpleBlockRenderingHandler/TESR style rendering
- BlockRenderer6343 or GTNH rendering helpers
- custom item renderer for machine previews or advanced icons
