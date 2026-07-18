<p align="center"><img src="/images/logo.png" alt="Logo"></p>
<h1 align="center">Neo ECO AE Extension - GTNH 1.7.10</h1>
<p align="center">ECO AE Extension for GT New Horizons on Minecraft 1.7.10.</p>
<h3 align="center">

English | [简体中文](/README_ZH_CN.md) | [繁體中文](/README_ZH_HK.md) | [文言](/README_LZH.md)

</h3>

## Overview

Neo ECO AE Extension - GTNH 1.7.10 is a rewrite of [Neo ECO AE Extension](https://github.com/DancingSnow0517/NeoECOAEExtension) for Minecraft 1.7.10 and the GT New Horizons ecosystem. It brings the modern project's high-performance Applied Energistics 2 storage, crafting, and computation concepts to GTNH.

Because Forge 1.7.10, GTNH AE2, NEI, rendering, registries, and data formats differ substantially from modern Minecraft, this project was rebuilt for the legacy environment rather than directly backporting the modern source code. Some artwork and visual assets originate from the modern project.

## What does the GTNH 1.7.10 edition offer?

The mod introduces three independent multiblock systems for storage, crafting, and computation. Each system is available in L4, L6, and L9 tiers, with higher tiers providing greater capacity and performance.

It also provides ECO storage cells and matrices, high-throughput crafting components, computation hardware, GTNH-compatible interfaces, and NEI integration designed for large late-game AE2 networks.

## Important Reminder

This GTNH 1.7.10 edition and the [modern Neo ECO AE Extension project](https://github.com/DancingSnow0517/NeoECOAEExtension) are independently maintained. The modern project is led and developed by **DancingSnow0517**; this GTNH 1.7.10 rewrite is maintained by **Yang120231**.

Please do not submit issues, bug reports, or support requests for this GTNH edition to the modern project. Likewise, problems specific to modern Minecraft versions should be reported to the modern project rather than here.

## Development

This project uses the GTNH development toolchain and GTNH-maintained forks of AE2, NEI, CodeChickenCore, and related libraries. Development notes are available under [`docs/`](/docs/README.md).

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

## License

This project is distributed under the [GNU General Public License v3.0](/LICENSE). Credits for the modern project and the GTNH rewrite are retained in the project metadata.
