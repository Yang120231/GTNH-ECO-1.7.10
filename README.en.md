<p align="center"><img src="https://raw.githubusercontent.com/DancingSnow0517/NeoECOAEExtension/main/images/logo.png" alt="Neo ECO AE Extension"></p>
<h1 align="center">Neo ECO AE Extension</h1>
<p align="center"><strong>GT New Horizons · Applied Energistics 2 Addon</strong></p>

<p align="center"><a href="README.md">简体中文</a> · <strong>English</strong></p>

This is the official Minecraft 1.7.10 version of Neo ECO AE Extension (ECO), built for GT New Horizons.

Provides expandable storage, crafting, and computation multiblocks for ME networks, supporting centralized storage, batch manufacturing, and complex job planning.

## Version Support

<div align="center">
<table align="center">
  <thead>
    <tr><th align="center">GTNH</th><th align="center">Download</th></tr>
  </thead>
  <tbody>
    <tr>
      <td align="center">2.9.0-RC1 and later</td>
      <td align="center"><a href="https://github.com/Yang120231/GTNH-ECO-1.7.10/releases"><img src="https://img.shields.io/github/v/release/Yang120231/GTNH-ECO-1.7.10" alt="Latest release"></a></td>
    </tr>
  </tbody>
</table>
</div>

Required dependencies include Applied Energistics 2, AE2 Fluid Crafting (AE2FC, 1.5.64-gtnh or later), ModularUI2, and StructureLib.

Large Integrated Working Stations support adding and removing processing recipes with CraftTweaker 1.7.10 scripts. See the [API documentation and examples (Chinese)](docs/CRAFTTWEAKER_ZH_CN.md).

## Features

### Real-Time Monitoring

![Real-Time Monitoring](docs/images/en_us/real-time-monitoring.png)

Monitors job inventory and processing status, showing associated machines, actual parallels, progress, remaining time, and available shutdown reasons. Supports pattern location, remote access to machines and pattern providers, and job suspension, tracking, and cancellation.

### ECO Planner

![ECO Planner](docs/images/en_us/eco-planner.png)

Provides fast planning, cyclic recipe handling, and large-order planning, with material requirements, inventory usage, and crafting step counts in one view. Supports CPU selection, job merging, Start With Follow, and NEI bookmarks for missing materials.

With NEE installed, Ctrl ingredient previews and Alt direct requests combine multiple missing ingredients into one temporary order on an ECO CPU. The order is cleared after delivery; cancelling stops further requests and returns the job inventory. Outputs that do not fit in network storage stay in the CPU for retry. Recipes requiring AE2's native planning semantics retain NEE's sequential requests, with ECO confirmation, cancellation, and temporary-pattern cleanup support.

ECO workstation recipes support NEE ingredient indicators, crafting shortcuts, and pattern ingredient cycling. The storage controller supports NEI bookmark extraction and recipe-chain inventory queries. Storage and computation interface filters follow NEE's drag-and-drop and ghost-item retention settings; these type filters do not store ingredient quantities.

### Planning Graph

![Planning Graph](docs/images/en_us/planning-graph.png)

Visualizes recipe dependencies from the target output down to raw materials. Supports panning, zooming, expandable nodes, depth selection, missing-material filters, and search.

### Extensible Computation Subsystem

![Extensible Computation Subsystem](docs/images/en_us/computation-subsystem.png)

Provides crafting CPU threads, parallel acceleration, and job storage, with request filtering for player or automated jobs. C9 supports linked controllers to pool threads, co-processors, and job storage.

### Extensible Storage Subsystem

![Extensible Storage Subsystem](docs/images/en_us/storage-subsystem.png)

Provides expandable item and fluid storage with L4, L6, and L9 controllers, matrix management, resource filtering, and inventory transfers. L9 supports infinite storage mode with inventory migration and restoration.

### Extensible Crafting Subsystem

![Extensible Crafting Subsystem](docs/images/en_us/crafting-subsystem.png)

Provides pattern storage and batch crafting with independent FX execution channels, parallel expansion, overclocking, and active cooling. F9 supports linked controllers for pattern sharing and task distribution.
