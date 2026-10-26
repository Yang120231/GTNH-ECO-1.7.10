# 完整 Job 与执行契约持久化

本批从固定提交 `f6003889a1352ea0d7e2e23a3868eb69943789ca` 的原始 Git blob 迁入完整核心 `ExecutingCraftingJob` 和 `ECOExecutionPlanNbtCodec`。来源哈希、目标哈希及完整核心范围列于同名 JSON。可选联动专属的旧 ABI 父类、强制合成和外部自动样板包装分支按既定核心范围排除；精确任务、等待产物、延期库存、执行计划绑定、种子、游标、暂停／永久错误及恢复隔离均保留。

恢复所需原生世界、CPU 身份和样板解码通过 `CraftingJobPersistenceContext` 显式提供，以便后续真实 CPU 与隔离测试共用同一恢复路径。任务进度继续使用此前迁入的完整 `ECOExecutionTaskProgress`，不重复一份权威计数。

`CraftingLink` 将来源 UUID 映射为原生 AE2 字符串 ID，保存时保留两者；仍使用原生 CraftingLink/Nexus 处理完成、取消和真实 requester 的物品／流体交付。原生模式未使用的独立链接也绑定真实 nexus，使完成状态可持久化。

验证命令：`gradlew.bat spotlessApply test --tests '*ExecutingCraftingJobPersistenceTest' --max-workers=2 --offline --console=plain`，3 项测试全部通过：来源规划器生成的完整 Job 在部分推进后 NBT 往返，样板丢失时隔离，损坏契约不得回退为无序执行。实际 CPU、工作线程和下单运行仍待后续批次接入，本批不宣称整条订单完成。
