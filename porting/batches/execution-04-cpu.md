# 第四批：完整 ECO CPU 核心、调度、记账与保存恢复

业务来源固定为 1.21.1 `f6003889a1352ea0d7e2e23a3868eb69943789ca`。本批导入完整 CPU、普通/批量派工、加工样板自适应扩量、任务生命周期、优化后计划替换、大订单分段、产物交付与持久化。来源文件的 SHA-256 从 `git show` 原始 blob 字节计算，逐文件记录在 `execution-04-cpu.json`，目标哈希包括原生适配与格式化。

CPU 使用来源规划/执行算法，通过显式的 `ECOCraftingCPUHost`、`ECOCraftingCPUOwner` 和工作者接口接入真实机器。原生 AE2 CPU 发布层由 `ECOCraftingNativeRuntime` 读取实际库存、等待产物、待派工、完成量和链接，并以世界和 tick 共同避免重复推进。空闲容量 CPU 提交必须由宿主分配真实线程核心，返回所分配 CPU 的真实链接。

原生边界补充了 AE2 的初始抽料、样板耗能、链接数据、安全来源和玩家标识；保留来源可变计划计数器的语义。初始抽料失败时，只扣除真正插回网络的数量，无法退回部分仍由 CPU 持有并保存。原生加工扩量仅允许可核验完整插入的固定 AE2 Interface 实现；稳定缓存原生提供器身份，使跨 tick 扩量/回退状态有效。任务终止登记使用主世界 `WorldSavedData`，不按时间丢弃仍可能被卸载工作者引用的终止记录。

移除明确排除范围的可选 mod 传输、记账和 ABI 分支；核心 AE2 加工扩量、事务、waiting 记账、精确数量与订单归属均保留。任务通知适配为原生翻译聊天消息，UI 资源另批提供相应翻译。

验证：

- `spotlessApply test build -PplannerChallenge=true --max-workers=2 --offline --console=plain`：成功；92 个测试类，共 555 项测试，0 失败/错误/跳过。
- 本批 27 项定向测试覆盖来源 dispatch accounting、plan updater、精确库存生命周期和 job 恢复，并新增真实来源 planner → CPU → dispatch controller 的两阶段订单、取消不重复退款、三类原生库存视图、CPU 保存恢复不重复抽料、缺料时无法回滚物品的保管。
- `verifyMigratedSourceCoverage`、Checkstyle、Spotless、重混淆 JAR 构建通过。

本批尚未注册真实合成/运算机器或线程宿主；不代表游戏内下单验收完成。来源 `ECOExactInventoryLifecycleTest` 中的真实线程核心拆卸用例在清单中明确标记 partial，待真实核心迁入后恢复。宿主、多方块、工作者与原生 CPU 缓存发布将继续分批接通，不以原生旧 AE2 执行算法代替 ECO 业务。
