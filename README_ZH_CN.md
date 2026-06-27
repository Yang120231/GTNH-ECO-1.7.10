<p align="center"><img src="/images/logo.png" alt="Logo"></p>
<h1 align="center">Neo ECO AE Extension - GTNH 1.7.10 移植版</h1>
<p align="center">面向 GT New Horizons / Minecraft 1.7.10 的 Applied Energistics 2 附属模组移植工程。</p>
<h3 align="center">

[English](/README.md) | 简体中文 | [繁體中文](/README_ZH_HK.md) | [文言](/README_LZH.md)

</h3>

## 概述

Neo ECO AE Extension 是一个围绕 Applied Energistics 2 的附属模组，重点提供高性能的存储、合成与计算组件。

本仓库是现代 NeoForge 版本在 GT New Horizons / Minecraft 1.7.10 环境下的移植工作区。现代上游项目面向 Minecraft 1.21.1+，而本移植版面向 GTNH 生态，因此必须使用 GTNH 维护的 AE2、NEI、CodeChickenCore 以及相关库的特供版本。

上游维护者：**DancingSnow0517**。

GTNH 1.7.10 移植负责人：**Yang120231**。

## 移植目标

- ECO 存储组件与存储元件
- AE2 附属材料与合成组件
- 受多方块结构启发的存储、合成、计算系统
- 适配 GTNH 1.7.10 的 NEI 配方/分类显示
- 适配 GTNH 的模型、贴图、物品信息与运行行为

本项目不是对 1.21.1 源码的直接复制。Forge 1.7.10、GTNH AE2、NEI、渲染、注册表、本地化、物品和方块模型机制都与现代 NeoForge 有明显差异。

## 当前状态

项目处于早期迁移阶段。

目前已完成：

- GTNHGradle 开发环境
- GTNH `daily` manifest catalog 支持
- GTNH 特供 AE2、NEI、CodeChickenCore 与运行时辅助依赖
- 与上游对齐的模组身份：
  - mod id：`neoecoae`
  - 包名：`cn.dancingsnow.neoecoae`
  - 显示名：`Neo ECO AE Extension`
- 第一个迁移物品：`neoecoae:aluminum_ingot`

## GTNH 依赖规则

不要在本项目中使用原版/上游 AE2、NEI 或 CodeChickenCore jar。

必须使用 GTNH manifest 中的特供构件，例如：

- `com.github.GTNewHorizons:Applied-Energistics-2-Unofficial`
- `com.github.GTNewHorizons:NotEnoughItems`
- `com.github.GTNewHorizons:CodeChickenCore`

## 开发

GTNH 开发环境使用 JDK 25。

常用命令：

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat spotlessApply
```

首次导入 IDE：

```powershell
.\gradlew.bat setupDecompWorkspace
.\gradlew.bat idea
```

## 文档

移植工作笔记位于 [`docs/`](/docs/README.md)。这些文档面向当前 GTNH 迁移工作，而不是通用模板说明。

1.21.1 源项目的迁移分析清单位于源项目本地工作区：

- `migration-analysis/migratable-items.md`
- `migration-analysis/migratable-items.csv`

## 重要提示

Neo ECO AE Extension 与 Eco AE Extension 之间仅存在授权移植关系。两者是独立维护的项目。

请不要把 GTNH 1.7.10 移植版的问题提交到现代上游项目，除非该问题已确认同样影响上游版本。

## 许可证

本项目遵循上游许可证方向，使用 GPLv3 发布。详见 [`LICENSE`](/LICENSE)。
