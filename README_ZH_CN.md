<p align="center"><img src="/images/logo.png" alt="Logo"></p>
<h1 align="center">Neo ECO AE Extension - GTNH 1.7.10</h1>
<p align="center">面向 GT New Horizons / Minecraft 1.7.10 的 ECO AE Extension。</p>
<h3 align="center">

[English](/README.md) | 简体中文 | [繁體中文](/README_ZH_HK.md) | [文言](/README_LZH.md)

</h3>

## 概述

Neo ECO AE Extension - GTNH 1.7.10 是 [Neo ECO AE Extension](https://github.com/DancingSnow0517/NeoECOAEExtension) 面向 Minecraft 1.7.10 与 GT New Horizons 生态的重写版本，旨在将现代版项目中的高性能 Applied Energistics 2 存储、合成与计算设计带到 GTNH。

由于 Forge 1.7.10、GTNH AE2、NEI、渲染、注册机制和数据格式均与现代 Minecraft 存在显著差异，本项目针对旧版本环境重新实现，而非直接移植现代版源代码。部分美术与视觉资源来源于现代版项目。

## GTNH 1.7.10 版本提供了什么？

本模组引入了相互独立的存储、合成与计算三个多方块系统。每套系统均提供 L4、L6 和 L9 三个等级，等级越高，容量与性能越强。

此外，本模组还提供 ECO 存储元件与存储矩阵、高吞吐量合成组件、计算硬件、适配 GTNH 的交互界面，以及面向大型后期 AE2 网络的 NEI 集成。

## 重要提示

本 GTNH 1.7.10 版本与[现代版 Neo ECO AE Extension 项目](https://github.com/DancingSnow0517/NeoECOAEExtension)相互独立维护。现代版项目由 **DancingSnow0517** 主导开发；本 GTNH 1.7.10 重写版本由 **Yang120231** 维护。

请勿将本 GTNH 版本的 Issue、错误报告或技术支持请求提交到现代版项目。同样，现代 Minecraft 版本特有的问题也应提交到现代版项目，而不是本仓库。

## 开发

本项目使用 GTNH 开发工具链，以及由 GTNH 维护的 AE2、NEI、CodeChickenCore 等依赖分支。开发与移植笔记位于 [`docs/`](/docs/README.md)。

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

## 许可证

本项目以 [GNU 通用公共许可证 v3.0](/LICENSE) 发布。现代版项目与 GTNH 重写版本的作者信息均保留在项目元数据中。
