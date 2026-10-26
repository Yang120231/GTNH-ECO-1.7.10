# 第五批：完整工作线程与产物归属、保管、恢复

从固定来源 `f6003889a1352ea0d7e2e23a3868eb69943789ca` 的原始 Git blob 迁入完整 `ECOCraftingThread`、`ECOCraftingFastPathVerifier`、`ECOCraftingThreadOutputDiagnostics` 和 `NEFakePlayer`，保留普通、批量、虚拟与精确 lane 的准入、验证、能量/冷却暂停恢复、进度、输出交付、取消、卸载保管、重试与损坏 NBT 隔离行为。线程通过真实 worker/controller 宿主接口调用世界、安全来源、集群和机器回调；宿主实现继续下一批迁入。

适配使用原生 ItemStack 的空值语义、原生 crafting inventory、Forge FakePlayer、FML crafting event、NBT 和服务器 tick。空闲显示、空槽快照以及无 item event 输出的批量安装使用 null-safe copy。诊断设置通过 Forge configuration 加载；其开关只影响日志。普通 crafting service 输出接口委托固定版本 AE2 的真实 `CraftingGridCache.injectItems`，精确订单归属仍由 CPU 发布层提供的 router 接入；不广播有 owner 的订单产物。

恢复来源 `ECOExactVirtualLedgerTest.exactLaneUsesOneSlotAndRejectsBusyOrNonVirtualWorker`，删除此前对应的 partial-import 限制，两个来源测试现均编译运行。新增 5 项运行链测试直接使用完整来源线程与原生组装网格，覆盖：部分 owner 交付后的 NBT 往返不重复/不泄漏、断网暂停后交付、冷却/分数能量暂停和保存恢复、完工前回收输入只执行一次、损坏进度隔离后回收输入。

验证命令：`spotlessApply test --tests '*ECOCraftingThreadRuntimeTest' --tests '*ECOExactVirtualLedgerTest' --max-workers=2 --offline --console=plain`，7 项全部通过；`test build -PplannerChallenge=true --max-workers=2 --offline --console=plain`，全量 561 项测试、Spotless、Checkstyle、来源覆盖检查与 JAR 构建通过。

本批验证是来源业务和原生 API 边界测试。真实合成/计算机器、集群和节点尚在下一批接入，不代表游戏内点击下单、机器运行或鼠标手势验收。
