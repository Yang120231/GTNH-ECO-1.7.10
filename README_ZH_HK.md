<p align="center"><img src="/images/logo.png" alt="Logo"></p>
<h1 align="center">Neo ECO AE Extension - GTNH 1.7.10</h1>
<p align="center">面向 GT New Horizons / Minecraft 1.7.10 的 ECO AE Extension。</p>
<h3 align="center">

[English](/README.md) | [简体中文](/README_ZH_CN.md) | 繁體中文 | [文言](/README_LZH.md)

</h3>

## 概述

Neo ECO AE Extension - GTNH 1.7.10 是 [Neo ECO AE Extension](https://github.com/DancingSnow0517/NeoECOAEExtension) 面向 Minecraft 1.7.10 與 GT New Horizons 生態的重寫版本，旨在將現代版專案中的高效能 Applied Energistics 2 儲存、合成與計算設計帶到 GTNH。

由於 Forge 1.7.10、GTNH AE2、NEI、渲染、註冊機制和資料格式均與現代 Minecraft 有顯著差異，本專案針對舊版本環境重新實作，而非直接移植現代版原始碼。部分美術與視覺資源來自現代版專案。

## GTNH 1.7.10 版本提供甚麼？

本模組引入相互獨立的儲存、合成與計算三個多方塊系統。每套系統均提供 L4、L6 和 L9 三個等級，等級越高，容量與效能越強。

此外，本模組亦提供 ECO 儲存單元與儲存矩陣、高吞吐量合成元件、計算硬體、適配 GTNH 的操作介面，以及面向大型後期 AE2 網絡的 NEI 整合。

## 重要提示

本 GTNH 1.7.10 版本與[現代版 Neo ECO AE Extension 專案](https://github.com/DancingSnow0517/NeoECOAEExtension)相互獨立維護。現代版專案由 **DancingSnow0517** 主導開發；本 GTNH 1.7.10 重寫版本由 **Yang120231** 維護。

請勿將本 GTNH 版本的 Issue、錯誤報告或技術支援請求提交到現代版專案。同樣，現代 Minecraft 版本特有的問題亦應提交到現代版專案，而非本倉庫。

## 開發

本專案使用 GTNH 開發工具鏈，以及由 GTNH 維護的 AE2、NEI、CodeChickenCore 等依賴分支。開發與移植筆記位於 [`docs/`](/docs/README.md)。

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

## 授權

本專案以 [GNU 通用公共授權條款 v3.0](/LICENSE) 發布。現代版專案與 GTNH 重寫版本的作者資訊均保留於專案元資料中。
