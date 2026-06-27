<p align="center"><img src="/images/logo.png" alt="Logo"></p>
<h1 align="center">Neo ECO AE Extension - GTNH 1.7.10 移植版</h1>
<p align="center">面向 GT New Horizons / Minecraft 1.7.10 的 Applied Energistics 2 附屬模組移植工程。</p>
<h3 align="center">

[English](/README.md) | [简体中文](/README_ZH_CN.md) | 繁體中文 | [文言](/README_LZH.md)

</h3>

## 概述

Neo ECO AE Extension 是圍繞 Applied Energistics 2 的附屬模組，重點提供高效能的儲存、合成與計算元件。

本倉庫是現代 NeoForge 版本在 GT New Horizons / Minecraft 1.7.10 環境下的移植工作區。現代上游專案面向 Minecraft 1.21.1+，而本移植版面向 GTNH 生態，因此必須使用 GTNH 維護的 AE2、NEI、CodeChickenCore 以及相關庫的特供版本。

上游維護者：**DancingSnow0517**。

GTNH 1.7.10 移植負責人：**Yang120231**。

## 移植目標

- ECO 儲存元件與儲存單元
- AE2 附屬材料與合成元件
- 受多方塊結構啟發的儲存、合成、計算系統
- 適配 GTNH 1.7.10 的 NEI 配方/分類顯示
- 適配 GTNH 的模型、貼圖、物品資訊與執行行為

本專案不是對 1.21.1 源碼的直接複製。Forge 1.7.10、GTNH AE2、NEI、渲染、註冊表、本地化、物品和方塊模型機制都與現代 NeoForge 有明顯差異。

## 當前狀態

專案處於早期遷移階段。

目前已完成：

- GTNHGradle 開發環境
- GTNH `daily` manifest catalog 支援
- GTNH 特供 AE2、NEI、CodeChickenCore 與執行時輔助依賴
- 與上游對齊的模組身份：
  - mod id：`neoecoae`
  - 套件名：`cn.dancingsnow.neoecoae`
  - 顯示名：`Neo ECO AE Extension`
- 第一個遷移物品：`neoecoae:aluminum_ingot`

## GTNH 依賴規則

不要在本專案中使用原版/上游 AE2、NEI 或 CodeChickenCore jar。

必須使用 GTNH manifest 中的特供構件，例如：

- `com.github.GTNewHorizons:Applied-Energistics-2-Unofficial`
- `com.github.GTNewHorizons:NotEnoughItems`
- `com.github.GTNewHorizons:CodeChickenCore`

## 開發

GTNH 開發環境使用 JDK 25。

常用命令：

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat spotlessApply
```

首次導入 IDE：

```powershell
.\gradlew.bat setupDecompWorkspace
.\gradlew.bat idea
```

## 文檔

移植工作筆記位於 [`docs/`](/docs/README.md)。這些文檔面向當前 GTNH 遷移工作，而不是通用模板說明。

1.21.1 源專案的遷移分析清單位於源專案本地工作區：

- `migration-analysis/migratable-items.md`
- `migration-analysis/migratable-items.csv`

## 重要提示

Neo ECO AE Extension 與 Eco AE Extension 之間僅存在授權移植關係。兩者是獨立維護的專案。

請不要把 GTNH 1.7.10 移植版的問題提交到現代上游專案，除非該問題已確認同樣影響上游版本。

## 授權

本專案遵循上游授權方向，使用 GPLv3 發布。詳見 [`LICENSE`](/LICENSE)。
