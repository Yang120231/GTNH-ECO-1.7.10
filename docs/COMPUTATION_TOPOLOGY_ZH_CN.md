# 计算子系统：结构验收与 ME 接入

计算子系统默认用 StructureLib 1.4.47 验证与投影仪共用的结构定义。区块加载和邻居通知在服务端 tick 尾合并；未加载的位置不会被当作有效部件，也不会为检测强加载区块。已有自定义计算器的 addon 可继续使用自己的验收规则。

## ME 接线

- 所有拓扑模式下，只有成型后的计算子系统通讯接口可接外部 ME 线缆。旧存档中接到主机、外壳或其他部件的线缆需要改接到通讯接口。
- 紧凑模式下，单点接入时只保留主机和通讯接口的节点；未接线时只保留主机节点。通讯接口和主机仍走 AE2 的频道和安全检查；已发现安全终端但权限缓存尚未就绪时，紧凑接入等待后重试。原部件的待机耗电汇总到主机；频道传输耗电按缩小后的 AE2 图原生计算。
- 通讯接口有多个候选接入面时，保留逐部件拓扑；非通讯接口部件只在同一结构内部连接。
- 同一控制器上的有效部件替换或扩缩优先保留运行中 CPU；移除承载该订单的线程核心或计算字节不足时，仍执行取消和库存回收。临时卸载走保存和恢复路径，挂起时不主动取消请求方的链接，但仍受 AE2 原生的请求超时规则约束。切换方式前请先备份存档并在副本验收。

## 调用链与批处理边界

- ECO：`NEBlockEntity.onReady/updateMultiBlock` → `MBCalculator.tick` → 发现边界 → `NEComputationClusterCalculator.verifyInternalStructure` → `NEStructureShape.check`。验收通过后创建或调整逻辑簇，再同步真正需要的 AE2 节点和连接面。
- AE2 `rv3-beta-1095-GTNH`：`AENetworkProxy.setValidSides` → `GridNode.updateState` → `FindConnections`。新增边可能合并网络；`GridConnection.destroy` 除了标记待寻路，还同步调用两端的 `validateGrid` 检查连通性。`MULTIBLOCK` 用于共享频道分配，原生合成 CPU 也用它；并不把多个节点变成一个节点。
- `PathGridCache.addNode/removeNode` 和连接变化会触发 `repath()`。它只标记待更新；`onUpdateTick()` 才分配频道，有在线 ME 控制器时走 `PathingCalculation.compute()`。因此不要把 `repath()` 次数当作实际寻路次数。
- `CraftingGridCache.pauseRebuilds()/unpauseRebuilds()` 合并样板缓存重建，不暂停拓扑拆分、供电或频道计算。ECO 另按 grid 合并 CPU 列表失效事件。这两类缓存应分别观察。

## 性能对照

在相同的存档副本上，修改 `config/neoecoae.cfg` 的 `multiblock.computationTopologyMode`，重新启动服务端后复现同一批区块加载或结构变动：

| 模式 | 检测 | 接入 |
| --- | --- | --- |
| `LEGACY` | 原有逐成员检测 | 逐部件保留节点，仅通讯接口对外接线 |
| `BATCHED` | 合并检测 | 逐部件保留节点，仅通讯接口对外接线 |
| `STRUCTURE_LIB` | 合并检测并共享 StructureLib 验收 | 逐部件保留节点，仅通讯接口对外接线 |
| `OPTIMIZED`（默认） | 合并检测并共享 StructureLib 验收 | 端口按需入网、有效变更保留现有簇 |

四种模式共用部分无变化过滤和缓存事件优化；`LEGACY` 不等同于改动前的 JAR。只比较 StructureLib 验收开销时，使用 `BATCHED` 和 `STRUCTURE_LIB`；对比整套优化时，另外用改动前的 JAR 作基线。

可在 JVM 加 `-Dneoecoae.profileMultiblocks=true`，短时间记录检测次数和耗时；5 ms 预算在两次检测之间检查，不是单次检测的耗时上限。日志本身会增加开销；正式对比峰值 MSPT 时关闭该选项，用同样的采样配置。服务端 `/debug start` 与 `/debug stop` 可查看 `ecoStructureCheck`、`ecoStructureValidation`、`ecoComputationTopology` 和 `ecoComputationGroups`。

记录主线程 MSPT、ME 网络拆分/合并、频道重新分配、合成样板与 CPU 缓存刷新。若服务端 tick 未明显变慢而终端/CPU 离线，优先检查拓扑和频道；若 MSPT 同时尖峰，按采样栈区分结构检测、图遍历和缓存刷新。小型隔离服能验证节点与生命周期，不足以判断玩家存档里数秒停顿的根因。

最低验证矩阵：普通与镜像布局、跨区块加载、低等级核心、两类交换器、外壳旧接线不再连接、通讯接口单面与多面接入、安全终端、运行中玩家与机器订单。以连接关系、有效频道、耗电、任务库存与结果回填为正确性基线，再比较 MSPT。
