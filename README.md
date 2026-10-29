<div align="center">

# Neo ECO AE Extension

**Minecraft 1.7.10 · GT New Horizons 社区移植版**

为 Applied Energistics 2 提供可扩展的存储、合成与计算多方块。

[![Releases](https://img.shields.io/github/v/release/Yang120231/GTNH-ECO-1.7.10)](https://github.com/Yang120231/GTNH-ECO-1.7.10/releases)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.7.10-5c9e31)](https://github.com/Yang120231/GTNH-ECO-1.7.10/releases)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue)](LICENSE)

[下载](https://github.com/Yang120231/GTNH-ECO-1.7.10/releases) · [功能介绍](#主要功能) · [安装说明](#下载与安装) · [问题反馈](https://github.com/Yang120231/GTNH-ECO-1.7.10/issues)

</div>

## 关于本项目

Neo ECO AE Extension（ECO）面向大型 ME 网络，将材料存储、样板执行和合成任务管理分为三套可以独立扩展的系统。你可以先补足网络中最需要的能力，再逐步搭建完整的自动化生产体系。

本仓库是 [Neo ECO AE Extension](https://github.com/DancingSnow0517/NeoECOAEExtension) 的 Minecraft 1.7.10 / GTNH 移植版，使用 GTNH 维护的 AE2、ModularUI 2 与对应平台的配方接入。

| 系统 | 主要职责 | 适用场景 |
| --- | --- | --- |
| ECO 存储系统 | 保存和管理物品、流体 | 集中仓储、扩充容量、管理资源输入输出 |
| ECO 合成系统 | 执行样板并批量产出 | 提高重复配方的吞吐、扩展并行制造能力 |
| ECO 计算系统 | 作为合成 CPU 承接任务 | 同时运行多个订单、处理复杂配方链与大型任务 |

## 主要功能

### 可扩展的存储系统

- 提供 L4、L6、L9 三级主机，以及物品、流体两类存储矩阵；通过增加驱动器和配套结构扩展容量。
- 在主机界面集中查看矩阵、容量、资源分布与能量状态，调整存储优先级。
- 通讯接口支持存储、输入和输出模式，可设置资源过滤，在本机矩阵与 ME 网络之间转移库存。
- L9 主机满足组件与成员矩阵要求后，可迁入无限存储模式，并通过配套流程恢复到普通矩阵。

### 批量执行的合成系统

- 样板总线保存合成样板，FX 工作核心承担执行，并行核心扩充处理能力。
- 支持批量合成、超频和主动冷却，配合流体仓与散热部件提高持续加工能力。
- 合成通讯接口连接 ME 网络，并提供样板集中查看与管理入口。
- F9 主机可通过普通或高能网络交换模块组网，共享样板域并分配工作。

### 多任务计算系统

- 成型后作为 ME 网络中的合成 CPU 使用；线程核心提供任务线程，并行核心提供加速能力，计算单元提供任务存储。
- 支持 ECO 快速规划、循环路线处理与大订单规划，通过确认界面和规划图查看材料需求及配方依赖。
- 主机界面显示任务、线程、存储与运行状态，CPU 选择模式可区分玩家请求和自动请求。
- C9 主机可通过网络交换模块组网，汇总多台主机的计算资源。

### 配套设备与联动

- **集成工作站与大型集成工作站**：加工 ECO 材料、存储组件和机器部件。
- **自动搭建面板**：查看多方块结构、材料需求，辅助搭建和扩展。
- **GregTech**：安装后启用对应的 GT 配方与机器加工接入；未安装时保留独立配方。
- **NEI / WAILA / GuideNH**：分别提供配方查询、设备状态提示与内置指南接入。

## 下载与安装

从本仓库的 [Releases](https://github.com/Yang120231/GTNH-ECO-1.7.10/releases) 下载运行包，并查看对应版本的发布说明。

| 环境或依赖 | 要求 |
| --- | --- |
| Minecraft / Forge | Minecraft 1.7.10，Forge 10.13.4.1614 |
| Applied Energistics 2 | 必需，使用 GTNH 维护的分支 |
| ModularUI 2 | 必需，`2.3.49-1.7.10` 或更高兼容版本 |
| GTNHLib | 必需，使用与所选依赖配套的版本 |
| GregTech | 可选，启用 GT 配方与加工接入 |
| NEI、WAILA、GuideNH | 可选，提供对应的查询、提示和指南功能 |

1. 关闭游戏与服务端，更新前先备份存档。
2. 移除旧版 ECO JAR，将新运行包放入客户端和服务端的 `mods` 目录。
3. 确认双方使用相同的 ECO 版本，并安装必要依赖后启动。

普通安装请选择 `neoecoae-<版本>.jar`。`-dev.jar` 用于开发依赖，`-sources.jar` 是源码包。

**旧存档升级：**从 `7.3.0-beta1` 升级时，当前兼容层仅迁移存储系统，旧合成与计算设备需要按新版重新搭建。升级前请阅读 [存储升级兼容说明](docs/STORAGE_7_3_0_BETA_COMPATIBILITY_ZH_CN.md)，并使用存档副本确认数据。

## 开始使用

先通过当前环境的配方查询获取盈能水晶并制作集成工作站，再加工 ECO 组件。随后按需求选择存储、合成或计算系统，使用主机的自动搭建面板查看结构和材料，成型后通过对应通讯接口接入 ME 网络。

安装 GuideNH 后，可以从设备的指南入口查看内置介绍。具体配方、材料和设备参数以当前版本的 NEI、Tooltip 与界面为准。

相关文档：

- [7.3.1 功能介绍与发布说明](docs/releases/7.3.1.md)
- [7.3.0-beta 存储升级兼容](docs/STORAGE_7_3_0_BETA_COMPATIBILITY_ZH_CN.md)
- [F9 / C9 网络交换接入说明](docs/NETWORK_SWITCH_PORT_ZH_CN.md)

## 问题反馈与贡献

本移植版的问题请提交到 [本仓库 Issues](https://github.com/Yang120231/GTNH-ECO-1.7.10/issues)。请附上 GTNH、ECO 与相关依赖版本、复现步骤，以及完整日志或崩溃报告；界面问题也请提供截图。

欢迎提交修复、翻译、文档改进和功能建议。Pull Request 请说明修改目的、玩家可见变化与验证方式；涉及机制或配方调整时，也请同步更新相关说明。

## 开发与构建

使用仓库自带的 Gradle Wrapper。Gradle daemon 使用 **JDK 25**；源码通过 JDK 17 与 Jabel 编译为 Java 8 字节码。游戏运行环境请按所用 GTNH 实例配置。

```powershell
# Windows：构建并运行默认测试
.\gradlew.bat build --console=plain

# 额外启用规划挑战测试
.\gradlew.bat test -PplannerChallenge=true --console=plain
```

Linux / macOS 使用 `./gradlew` 替换 `.\gradlew.bat`。生成的 JAR 位于 `build/libs/`，依赖缓存齐全后可加 `--offline`。

开发环境默认加载 GregTech 及配套运行依赖；使用 `-PgtRuntime=false` 可关闭这组开发依赖，使用 `verifyGtRuntimeProfile` 可检查实际运行配置。编译依赖版本见 [gradle.properties](gradle.properties) 和 [build.gradle.kts](build.gradle.kts)；这些固定版本用于开发，整合包兼容性请查对应发布说明。

移植来源与早期调查归档于 [移植调查文档](docs/porting-1.7.10-20261025/README_ZH_CN.md)。

## 致谢与许可证

感谢原项目作者 **DancingSnow0517**、**ZhuRuoLing** 及其他贡献者；Minecraft 1.7.10 / GTNH 移植版由 **Yang120231** 维护。

感谢 GTNH 社区维护的 Applied Energistics 2、ModularUI 2、GTNHLib 及相关开发工具。本项目遵循 [GNU GPL v3.0](LICENSE) 许可证。
