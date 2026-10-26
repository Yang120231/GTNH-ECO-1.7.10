# 规划器服务器生命周期

固定业务输入为 `f6003889a1352ea0d7e2e23a3868eb69943789ca`。本批保留来源规划器、预算与取消检查算法，仅补服务器宿主生命周期。

- `ECOPlanningExecutor` 跟踪前台及可选任务，停止时取消正在执行和排队的 Future，再关闭线程池；停止后的提交被拒绝。
- 新服务器生命周期重新创建线程池，同 JVM 重进世界可继续规划。
- `PlanningLifecycle` 在 Forge about-to-start/stopped 接入 generation 失效，主动清空 `CompiledStructureCache`、FastPath 和增长验证缓存，释放旧世界冻结样板与图结构。

验证：`gradlew.bat spotlessApply test --tests '*ECOPlanningLifecycleTest' --tests '*CompiledStructureCacheTest' --tests '*ECOPlanningServiceTest' --tests '*ECOProgressivePlanningTest' --offline --console=plain`。19 项测试全部通过，包含真实阻塞任务的中断、排队 Future 取消、停止后拒绝提交及同 JVM 第二次启动。

这不表示完整 CPU、Job 或实际订单链已完成；后续继续迁入执行主体。
