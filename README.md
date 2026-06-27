<p align="center"><img src="/images/logo.png" alt="Logo"></p>
<h1 align="center">Neo ECO AE Extension - GTNH 1.7.10 Port</h1>
<p align="center">An Applied Energistics 2 addon port for GT New Horizons on Minecraft 1.7.10.</p>
<h3 align="center">

English | [简体中文](/README_ZH_CN.md) | [繁體中文](/README_ZH_HK.md) | [文言](/README_LZH.md)

</h3>

## Overview

Neo ECO AE Extension is an Applied Energistics 2 addon focused on high-performance storage, crafting, and computation components.

This repository is the GT New Horizons / Minecraft 1.7.10 porting workspace for the modern NeoForge project. The upstream modern project targets Minecraft 1.21.1+, while this port targets the GTNH ecosystem and must use GTNH-maintained forks of AE2, NEI, CodeChickenCore, and related libraries.

Upstream maintainer: **DancingSnow0517**.

GTNH 1.7.10 port maintainer: **Yang120231**.

## What This Port Aims To Provide

- ECO storage components and storage cells
- AE2 addon materials and crafting components
- multiblock-inspired storage, crafting, and computation systems
- NEI recipe/category support for GTNH 1.7.10
- GTNH-compatible models, textures, tooltips, and runtime behavior

This is not a direct source copy. Minecraft 1.7.10 Forge, GTNH AE2, NEI, rendering, registries, localization, and item/block model handling differ substantially from modern NeoForge.

## Current Status

This project is in early migration.

Implemented so far:

- GTNHGradle development environment
- GTNH `daily` manifest catalog support
- GTNH fork dependencies for AE2, NEI, CodeChickenCore, and supporting runtime mods
- mod identity aligned with upstream:
  - mod id: `neoecoae`
  - package: `cn.dancingsnow.neoecoae`
  - display name: `Neo ECO AE Extension`
- first migrated item: `neoecoae:aluminum_ingot`

## GTNH Dependency Policy

Do not use upstream/original AE2, NEI, or CodeChickenCore jars in this project.

Use GTNH forked artifacts from the GTNH manifest, for example:

- `com.github.GTNewHorizons:Applied-Energistics-2-Unofficial`
- `com.github.GTNewHorizons:NotEnoughItems`
- `com.github.GTNewHorizons:CodeChickenCore`

## Development

Use JDK 25 for the GTNH development environment.

Common commands:

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat spotlessApply
```

For a fresh IDE setup:

```powershell
.\gradlew.bat setupDecompWorkspace
.\gradlew.bat idea
```

## Documentation

Porting notes live under [`docs/`](/docs/README.md). They are written as working notes for the GTNH migration rather than generic template documentation.

Migration analysis from the 1.21.1 source project is generated in the upstream local workspace under:

- `migration-analysis/migratable-items.md`
- `migration-analysis/migratable-items.csv`

## Important Reminder

Neo ECO AE Extension and Eco AE Extension are related through an authorized porting relationship. They are independently maintained projects.

Please do not submit GTNH 1.7.10 port issues to the upstream modern Neo ECO AE Extension project unless the issue is confirmed to apply there as well.

## License

This project follows the upstream licensing direction and is distributed under GPLv3. See [`LICENSE`](/LICENSE).
