# 规划器同步 2026-10-28

业务来源从 `f6003889a1352ea0d7e2e23a3868eb69943789ca` 推进到 1.21.1 `29b8eacb`（21.2.12），只同步 `crafting/planner`、`crafting/amount` 与 `api/me/planning`。下次同步以 `29b8eacb` 为基线。

| 1.21.1 提交 | 内容 |
| --- | --- |
| `84c66119` | 库存恰好满足需求时不再耗尽循环合成种子 |
| `c0715834` | 提前识别循环缺失启动材料造成的死锁（`BoundedCycleSolver`、`CycleSolveDiagnostic`） |
| `a5f3a80d` | 规划数量 `PlannerAmount` 引入 `U126` 中间层 |
| `3049385c` | 按完整物料键索引规划需求，优化副产物信用分配（`MaterialProvenance`） |
| `dae06aef` | 备选路线数量剪枝：`OptimisticSupplyModel`、`RouteQuantityProof`，`RouteAvailabilityProof` 改用乐观供给模型，外部需求过滤路线缓存 |

做法：两端源文件先用本仓库 Spotless 格式化，再以 `f6003889` 为共同祖先对每个文件三方合并，保留 1.7.10 的导入与 `JdkCompat` 适配。冲突只有导入（保留本地包路径）和两处上游重写（`ExternalDemandPlanner.filteredRoute`、`RouteAvailabilityProof`，取上游后改回本地 API）。

Java 8 适配：新增记录加 `@Desugar`；`List/Set/Map.of`、`copyOf`、`Stream.toList` 改为 `JdkCompat`；`Math.multiplyHigh` 改为 `U126.multiplyHigh`（JDK 的 32 位拆分算法，测试以 BigInteger 逐位对照）。

未同步：同一批提交里的无限存储 `HugeAmount` U126 层、执行侧（`62e10637` 等）与界面改动。`U126TierTest` 相应去掉 `HugeAmount` 用例。

测试：上游新增/修改的 12 个规划测试全部迁入（`Items` 改 `TestItems`，`CountedKey` 按本地 `AEKey` 契约重写），规划与数量测试 375 项通过。
