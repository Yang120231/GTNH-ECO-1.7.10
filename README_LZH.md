<p align="center"><img src="/images/logo.png" alt="Logo"></p>
<h1 align="center">Neo ECO AE Extension - GTNH 1.7.10 移植</h1>
<p align="center">為 GT New Horizons / Minecraft 1.7.10 作 Applied Energistics 2 附屬模組之移植。</p>
<h3 align="center">

[English](/README.md) | [简体中文](/README_ZH_CN.md) | [繁體中文](/README_ZH_HK.md) | 文言

</h3>

## 概

Neo ECO AE Extension，AE2 之附屬也，主於高效儲藏、合成、計算諸器。

此倉為 GT New Horizons 一點七點十之移植工區。今上游本為 Minecraft 1.21.1+ NeoForge 所作；此本則從 GTNH 之法，須用 GTNH 所維護之 AE2、NEI、CodeChickenCore 諸分支，不可雜以上游原版。

上游維護：**DancingSnow0517**。

GTNH 1.7.10 移植：**Yang120231**。

## 所欲移植者

- ECO 儲藏元件與存儲單元
- AE2 附屬材料與合成部件
- 儲藏、合成、計算三系之多方塊構想
- 適於 GTNH 1.7.10 之 NEI 配方顯示
- 適於 GTNH 之模型、貼圖、提示與運行邏輯

此非直抄新本。Forge 1.7.10、GTNH AE2、NEI、渲染、註冊、本地化、物品方塊模型，皆與今世 NeoForge 異。

## 今況

尚在初遷。

已成者：

- GTNHGradle 開發環境
- GTNH `daily` manifest catalog
- GTNH 特供 AE2、NEI、CodeChickenCore 及運行依賴
- 模組名號已同上游：
  - mod id：`neoecoae`
  - 包名：`cn.dancingsnow.neoecoae`
  - 顯名：`Neo ECO AE Extension`
- 初遷物品：`neoecoae:aluminum_ingot`

## 開發

GTNH 開發環境用 JDK 25。

常用命令：

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat spotlessApply
```

## 文檔

移植筆記見 [`docs/`](/docs/README.md)。

## 告

Neo ECO AE Extension 與 Eco AE Extension 但有授權移植之緣，非同一維護。

GTNH 1.7.10 之移植問題，勿徑投於現代上游，除非其病亦見於上游。

## 授權

以 GPLv3 行。見 [`LICENSE`](/LICENSE)。
