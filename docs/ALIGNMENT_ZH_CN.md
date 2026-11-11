# 与 1.21.1 主线的对齐：GTNH 1.7.10

本文件记录 GTNH 版相对 1.21.1 主线的差异，分为特殊（不对齐）、落后（要补）、领先（主线没有）三类。主线的总表在 `NeoECOAEExtension-1.21.1/docs/ALIGNMENT_ZH_CN.md`。

- 调查日期：2026-11-11
- 本版基线：`aa7f568`（分支 `main`）
- 主线基线：`2e494131`
- 调查方法：目录、类名和提交标题对照，子系统级比较。没有逐行比对，没有运行构建或游戏。
- 路径均相对于 `src/main/java/cn/dancingsnow/neoecoae/`。

**移植方式**：本版用 1.21.1 的业务源码加一层 `platform/` 转接层，用 Jabel 编译到 Java 8，目录结构与主线一致（见 `docs/porting-1.7.10-20261025/README_ZH_CN.md`）。因此主线的规划、执行、存储改动大多可以按文件移植。

**水位**：规划器有文档记录的同步基线是主线 `29b8eacb`（21.2.12，见 `PLANNER_SYNC_20261028_ZH_CN.md`）。之后的同步没有文档，只能从提交标题推断：存储和规划性能项大多已跟进，工作站相关项基本没有。

## 特殊（不对齐）

| 项 | 位置 |
| --- | --- |
| AE2 rv3 的 `IAEStack` / `IItemList` 双通道，转接成现代 `AEKey` 外观 | `platform/ae2/`（122 个文件） |
| 界面用 ModularUI2，主线用 LDLib2 | `platform/ui/`（73 个文件） |
| 没有 datagen：配方、模型是静态 JSON | `core-content.json`、`recipe-catalog.json` |
| 配方脚本用 CraftTweaker，主线用 KubeJS | `compat/crafttweaker/`、`docs/CRAFTTWEAKER_ZH_CN.md` |
| NEI / NEE 代替 JEI / EMI，包括临时订单、书签取料、工作站覆盖层 | `compat/nei/` |
| Waila 代替 Jade | `compat/waila/` |
| GuideNH 读取同一份指南源 | `build.gradle.kts` |
| GT、GTNL、可编程仓室、AE2FC 联动 | `compat/{gregtech,gtnl,proghatches,ae2fc}`、`platform/gt/` |
| 接口终端通过 `IInterfaceViewable` 接入 | 主线等价物是 AE2 的 `PatternContainer` |
| 渲染保留现代模型加载器 | `client/render/model/ModernModelLoader.java`、`docs/rendering-model-pipeline.md` |
| Spotless 与 GTNH 约定插件 | `gtnhShared/` |
| 7.3.0-beta 旧存档兼容层 | `impl/storage/legacy`、`docs/STORAGE_7_3_0_BETA_COMPATIBILITY_ZH_CN.md` |
| 主线的现代模组联动（AE2LT、Thunderbolt、AdvancedAE、ExtendedAE、MEGA、BiggerAE2、AppFlux、AppMek 等） | 本版没有对应模组 |
| 大宗压缩存储（依赖 BiggerAE2） | 主线 `storage/bulk/` |
| 依赖可选模组的工作站配方源 | 主线 `recipe/` 16 个文件，本版 5 个 |

## 落后（主线有，本版要补）

### 子系统状态

| 子系统 | 状态 | 说明 |
| --- | --- | --- |
| 规划、循环、大订单 | 有 | `crafting/planner` 子包与主线相同 |
| CPU、发配、产物 | 有 | `crafting/execution` 结构一致，`api/me` 子包相同 |
| 样板存储 | 有 | 只按文件名确认 |
| 存储盘与精确数量 | 有 | `ECOBigIntegerStorage`、`HugeAmount`、`StorageListingDelta`、`NetworkInventoryCache` 都在 |
| 多方块与档位 | 有 | 含网络交换机、发信器 |
| 大型工作站 | 部分 | 主方块实体 2212 行，主线 2598 行 |
| 公开 API 与文档 | 部分 | `api/` 75 个文件，主线 111 个；没有 API 文档 |
| 成就 | 已补齐 ECO 展示进度 | 7 项 ECO 进度注册为 1.7.10 原生成就；配方书解锁进度不注册 |
| 测试 | 没有 | `b60f586` 删除 |

### 具体缺口

1. **智能冷却模块**（主线 21.2.13，及 `7952b49f` 的配方）。全仓搜不到 `SmartCooling`。
2. **工作站供能卡片**（主线 `6198946a`）。全仓搜不到 `PowerPriority`。
3. **工作站模块库存与机壳方块实体**。缺主线的 `LargeWorkstationModuleInventory`、`ECOLargeIntegratedWorkingStationCasingBlockEntity`。
4. **工作站模型和动态配色**（主线 `7d7c001a`、`2a15bf47`）。没有对应类；渲染管线不同，是否要做需要单独决定。
5. **精简合成模式**（主线 `ec35bf8f`、`7c7acbd4`）。`AcyclicCraftingSolver` 里没有 lite 分支，没有 `ECOAutoCraftingPlanning`。
6. **修复批量发配**（主线 `2e494131`）。`ECOBatchAdmission`、`ECOProcessingPatternDispatcher` 都在，是否包含修复未核实；其中 ae2lt 部分属于特殊。
7. **规划清理与统一**（主线 `9fa1d04b`、`1572c494`、`ac366d29`）、**存储同步与持久化**（主线 `9ea8a6b4`）。没有同名提交，只比过标题。
8. **精确数量换成 U128**（主线 `1f07ea30`）、**递归视图与合成报告同步**（主线 `b35f498f`、`b2f96739`）。本版有 U126 和 Recursive 相关文件，是否已跟进到 U128 未核实。
9. **工作站运行规则**。主线 21.2.12 之后的“运行 1 tick、温度控并行”等规则，本版是否一致未核实。
10. **智能冷却面板、多方块建造预览面板**（主线 21.2.15）。推断没有。
11. **附属扩展 API**。`api/` 比主线少 36 个文件，具体缺哪些没有逐个列出；参考主线 `api/extension/`、`api/capability/ECOFeature`、`api/multiblock/`、`api/power/`、`api/workstation/`。
12. **API 文档**。没有 `docs/API_ZH_CN.md`。

## 领先（本版有，主线没有）

| 项 | 位置 | 备注 |
| --- | --- | --- |
| 样板上传系统：按玩家保存配方类别到接收端的映射、多个等价接收端自动选择、满了顺延或溢出、工作站上传 | `crafting/upload/`（15 个文件）、`platform/ae2/upload/`、`platform/gt/upload/` | 主线只有编码终端上的上传按钮（`PatternEncodingTermMenuMixin`），处理样板直接返回。多数接收目标是 GTNH 专有，可回流的只有通用 AE2 接口的路由与选择 |
| AE2 原生计划转 ECO 确认页报告与统计（`dfafec9`、`0a0010b`） | `platform/ae2/crafting/confirm/NativeCraftConfirmReports.java` | 主线只找到反方向的 `AE2CraftingPlanBridge`，未完全排除在别处实现 |

以下不算领先，主线已有：网络拉取卡（主线 `NETWORK_PULL_CARD`，`d76bdc41`）、存储接口增量传输（主线 `5cea6cfc`）、网络交换机与发信器（主线 `1c1e084b`）、合成清单图片导出、确认页重新规划。

## 未核实

- `29b8eacb` 之后规划器的实际同步程度。
- ModularUI2 界面覆盖了主线哪些面板。
- 七张工作站专用升级卡的状态（移植调查文档写的是“已排除”）。
- 调查之后提交的 `44a9b45`（修复样板自动上传目标选择）没有纳入本次对比。
