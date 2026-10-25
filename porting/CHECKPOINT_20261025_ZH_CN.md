# 2026-10-25 暂停交接记录

用户要求“先停止。整理文档，记录进度”。正式移植已暂停，后续等待用户明确继续。本次收尾只整理文档、读取现有清单和验证证据；没有继续迁入或修改业务／平台代码，没有运行构建、刷新 JAR 审计或启动游戏，没有暂存、提交或推送。

## 已确定的任务边界

- 业务规则和算法采用固定的 1.21.1 来源，参考 1.20.1 的平台组织方式，适配 Forge 1.7.10、GTNH AE2 和 GTNH MUI2。平台差异集中在 `platform`。
- 保留现代模型加载、烘焙和绘制链路的 11 个源码文件；暂停时逐文件 SHA-256 与 `foundation-state.json` 一致。
- 全部可选 Mod 联动均排除，包括联动盘、机器、配方、资源、兼容实现及 Mixin；必要 AE2／MUI2 平台桥接继续保留。原生 AE2 创意盘和核心隐藏旧散装盘不属于联动盘。
- 大型集成工作站不注册七张专用升级卡，不设置其卡片门槛、槽位或配方。普通小工作站的原生 AE2 升级规则保留。
- 最终仍须交付 ECO 存储／合成／计算三件套、GT 机器及原生样板供应器组成的实际下单测试存档。**该存档尚未创建；隔离验收世界不能作为最终交付。**

范围授权和逐路径排除依据见 [content-overrides.json](content-overrides.json)、[范围说明](SCOPE_CORE_ONLY_20261025_ZH_CN.md) 及 [source-lock.json](source-lock.json)。

## 暂停时的实际规模

以下数量重新读取当前文件和清单得到，不代表整体行为完成率。

| 项目 | 数量 | 依据 |
| --- | ---: | --- |
| 已迁入业务文件 | 318 | `source-lock.json.imported` 中 `kind=business` |
| 已迁入来源测试文件 | 79 | 同清单中 `kind=test`，部分文件按成员迁入 |
| 来源资源 | 1,320 | 同清单中 `kind=resource` |
| 平台资源别名 | 432 | 同清单中 `kind=platform_resource_alias` |
| 主 Java 文件 | 540 | `src/main/java` 实际文件数 |
| 测试 Java 文件 | 93 | `src/test/java` 实际文件数 |
| 核心待迁主源码路径 | 463 | `source-lock.json.pending_business`；包含待替换平台／入口，不换算百分比 |
| 现代依赖原语适配 | 12 | `dependency-adapters.json` |
| 核心配方定义／物品标签 | 161／44 | `src/main/resources/neoecoae/recipe-catalog.json` |
| 已原生安装／剩余核心配方 | 92／69 | 既有注册和专服证据；打包 JSON 不等于已安装 |
| 已注册核心独立物品／材料方块／存储机器方块 | 65／13／10 | 既有内容夹具和原生注册 |

`core-content.json` 追溯 72 个来源物品定义，其中七张大型工作站卡禁用；不能据此宣称注册了 72 个物品。

## 已落地的业务与转接

- 存储：普通盘、创意资源盘、精确数量、世界域持久化、无限迁移／恢复、存储主机／驱动器、共享多方块基础、原生双通道和存储主页面已接入。接口、优先级、构建／引导页面及完整玩家手势仍有待迁部分。
- 规划：来源规划入口、循环／路线／联合优化、任意精度库存、快照和渐进规划已迁入；服务器线程冻结原生样板、候选及发射器，后台规划只读快照。拓扑、样板及候选集合变化使缓存失效。驱动器类别别名和原生 ME 箱第 1 槽映射已修正。
- 执行基础：批次准入、输入租约、一次性结算、能量预付／退款、FastPath、任务附件／生命周期、调度策略、输入预览／解析／缓存、供应器游标、精确工作账本和堆栈编解码已迁入。
- 执行游标：完整 `ECOExecutionRuntime` 的阶段推进、启动种子保护、重复循环及精确进度已迁入。`ECOExecutionTaskProgress` 抽取来源 Job 的完整进度算法并保留旧 ABI 父类的 `public long value` 字段；不是完整 Job／CPU 宿主实现。
- 平台：Java 8 API、原生 NBT、资源键、网络队列、原生供应器、MUI2 同步和材料渲染已按已用范围接入。NBT long 数组用 `TAG_List<TAG_Long>` 无损承载，极值二进制往返已测试。现代 AE2 原语的来源哈希及许可证见 `dependency-adapters.json` 和 `dependency-licenses.json`。
- 注册／配方：65 个核心独立物品、13 个材料方块和 10 个存储机器方块已注册。来源配方已安装 92 条，其余 69 条待对应核心机器与工作站业务接入。

**完整 `ECOCraftingCPULogic`、Job、工作线程／宿主、合成与计算集群、原生 CPU 发布和订单提交／确认尚未接通。** 不得以原生 AE2 执行算法、空壳机器注册或算法测试通过替代来源业务的完整迁入。

## 现有验证证据

暂停前最后一次完整构建使用：

```powershell
.\gradlew.bat test build -PplannerChallenge=true --offline --console=plain
```

暂停时重新读取 `build/test-results/test/TEST-*.xml`：85 个测试套件、523 项测试，失败／错误／跳过均为 0；其中来源 `TechModPlannerChallengeTest` 为 26 项。编译覆盖、Checkstyle 和 Spotless 在该次完整构建通过。未启用 `plannerChallenge` 时会按来源开关跳过挑战，正式验收应保留该参数，不修改算法预算或放宽断言。

最后一次独立 Java 8 专服的原始证据：

关键行已保留为 [专服证据摘录](acceptance/core-planning.log)，这是暂停时整理的既有运行证据，不是新一轮运行或 JAR 审计。

| 文件 | SHA-256 |
| --- | --- |
| `build/server-core-planning.log` | `6d4ac62019000fb54714fca8df58aec35042b627a69afbca30295af1af890e1e` |
| `build/server-core-planning-minecraft.log` | `ce94d991d0510afd5a7ee3804749504102d2d6f71f68468b1705e5d9e4d1d706` |

日志日期为 2026-10-25，以下时分秒直接沿用日志，未推定时区：

- 21:06:47，`NEOECOAE_PORT_PLANNING_OK`：来源异步规划、驱动器／ME 箱创意盘、原配方重算、纯发射器快照、真实接口向相邻箱子投递一份输入、样板移除失效全部通过；`provider_dispatch=1`。
- 21:06:47，普通存储主机通过：48 部件、12 驱动器、504 个装载物品、抽取 17、优先级 73、原生 NBT 和 UI 快照。
- 21:06:55，无限存储主机迁移通过：12 部件、迁移 487、抽取 23、主机域 NBT。
- 21:09:32，原生内容与 9 盘双通道夹具通过，精确数量为 `10^40 + 48`。
- 21:18:08，服务器正常停止，保存三个维度。

现有客户端证据只覆盖材料图集、配方／NBT 断言、存储主页面及实际 MUI2 取盘／装回网络动作；不代表人工鼠标手势、完整 CPU 订单或最终存档已验收。历史详情见 [材料与执行基础记录](MILESTONE_EXECUTION_FOUNDATION_20261025_ZH_CN.md)。

**JAR 总审计尚未刷新。** `business-import-state.json.verified_at` 仍为 `2026-10-25T12:08:25.187536+00:00`，其中为 292 个业务文件、40 个来源测试文件及 248 项测试的旧快照。最后完整构建之后还刷新过核心内容范围授权哈希并运行专服；安装包、开发包和源码包不能被当作已经按当前全部文件重新核对一致。暂停收尾没有运行审计，也没有改写旧审计结果。

## 固定来源与本次远端检查

| 用途 | 固定采用提交 |
| --- | --- |
| 1.21.1 业务 | `f6003889a1352ea0d7e2e23a3868eb69943789ca` |
| 1.20.1 平台参考 | `36dada343abff7533f63cce0d5729950342488f5` |
| GTNH AE2 `rv3-beta-990-GTNH` | `565607ef63af09c743dc7c721e841bf1b4bc1c66` |
| GTNH MUI2 `2.3.49-1.7.10` | `b29dd89b52766541dcb995f56d492156572b4d55` |
| 现代 AE2 原语 `neoforge/v19.2.18` | `db17504a86128fdf3dae31f5fb7a112a646e0b93` |

GT5 上轮已无冲突快进至 `f987adc45c793ff9b96b44fcf8ee7e01d1866519`。来源工作区已有未提交修改必须保留；本轮未选择任何来源工作区补丁，不能用工作区最新文件覆盖固定输入。

暂停前尝试重新 fetch 1.21.1、1.20.1、GTNH AE2、现代 AE2、MUI2 和 GT5，均因本机 `127.0.0.1` 代理未连通失败。随后仅对 1.21.1 命令临时关闭代理尝试直连 GitHub，也连接失败；未修改全局代理配置、合并来源分支或变更固定提交。本次不能确认这些远端仍为最新。恢复工作时先重新完成远端检查，再按迁移契约记录采用的提交及补丁。

## 尚未实现的首个续接点

暂停前仅阅读、尚未修改的生命周期问题：

- `crafting/planner/ECOPlanningExecutor.java` 的两个静态线程池没有公开停止入口。
- `platform/forge/StorageLifecycle.stopped()` 当前仅清存储，未触发规划任务取消／失效。
- `platform/ae2/crafting/PatternRuntime.onRecipeReloadOrServerReload()` 会递增 generation，并清 FastPath 与增长验证缓存；当前没有接到该停止路径。
- `crafting/planner/compile/CompiledStructureCache.java` 的 owner 为弱引用，但条目仍持有结构／样板；尚未实现停止时的主动释放。
- `NeoECOAE.aboutToStart()`／`stopped()` 已调用 `platform/network/ServerTasks` 的启停，后续应核对异步完成回调与新世界的边界。

这属于待核对／待修复事项，未宣称已发生资源泄漏或已修复。适配应集中在平台生命周期，保持来源调度与规划算法；不能永久关闭线程池后使同一 JVM 再进存档无法规划。

## 恢复后的顺序与保护要求

1. 重新核对远端，保留来源和当前工程全部未提交工作；按固定提交与明确选定补丁读取源码。
2. 先完成上述规划生命周期取消、缓存失效和同 JVM 重进存档的核对／适配。
3. 审查改动后刷新哈希，重新完整构建并运行 JAR 审计：

   ```powershell
   .\gradlew.bat spotlessApply --offline --console=plain
   python porting/import_dependency_adapters.py --refresh-manifest
   python porting/import_business.py --refresh-manifest
   .\gradlew.bat test build -PplannerChallenge=true --offline --console=plain
   python porting/audit_business_import.py
   ```

4. 按依赖继续迁入完整 CPU／Job／工作线程、持久化和取消，再接合成／计算集群、注册、CPU 服务发布及提交／确认。
5. 完成对应 MUI2 页面、网络同步、剩余存储交互、工作站、69 条剩余核心配方和渲染状态绑定。
6. 启用 GT 运行环境，创建三件套＋GT＋样板供应器的独立存档；验证真实下单、执行、产出归属、取消／断网和保存重载后交付。

不要全局执行 `--reapply-adapters`，也不要对全部目标源码重新套用现代包替换。已有手工适配和原生测试导入必须保留；定点重建先审查，再用 `--reapply-path`。`--refresh-manifest` 只记录经审查的文件，不代替审查、构建或验证。

逐成员边界见 [partial-imports.json](partial-imports.json)：精确账本仍缺真实线程宿主用例；副产物对照仍缺目标原生随机对照；创意供给的有限饱和盘用例仍是核心待迁，不得当作可选联动排除。`ECOFastPathFacade`、存储主机和部分页面也尚未完整迁入。

来源已知缺口见 [source-gaps.json](source-gaps.json)：三个隐藏旧散装盘的来源容量参数拒绝首次插入，不能改规则掩盖；来源还缺无限动画 PNG。

目标 Forge 实例没有 mcpfabric 桥接，不调用其工具。不要关闭用户另外运行的 1.20.1 实例；进程检查不输出可能包含账户凭据的完整启动参数。普通执行沙箱本轮初始化失败，必要的只读核对和文档写入通过获准的外部执行进行。
