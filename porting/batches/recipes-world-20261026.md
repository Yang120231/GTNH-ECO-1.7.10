# 2026-10-26 世界转化与原生全目录验收入口

本批完成剩余六个核心 `ae2:transform` 定义的真实世界绑定。配方来自固定业务提交 `f6003889a1352ea0d7e2e23a3868eb69943789ca`；触发、邻域搜索和逐份认领算法来自固定现代 AE2 `db17504a86128fdf3dae31f5fb7a112a646e0b93` 的 `TransformRecipe`、`TransformLogic.tryTransform` 和 `ItemEntityMixin`。现有 `META-INF/licenses/ae2-modern-LGPL-3.0.txt` 继续覆盖这些派生适配。

四条水中配方只由第一种输入触发，物品实体连续位于有效流体超过 60 tick 后尝试，即首次合法处理时间为 61 tick。离开水或尝试时材料不足会重置等待时间。两条爆炸配方允许任意输入作为锚点，在实际 Forge `ExplosionEvent.Detonate` 中执行；成功时只从本次爆炸的受伤列表移除锚点，保留其未使用部分。没有把世界配方改为工作台配方或工作站配方。

邻域为锚点坐标各方向一格。每个来源 ingredient 消耗一个物品，先完成全部输入认领才修改实体；同一物品实体可满足多个 ingredient，但不能超用其数量。配方、实体和 ingredient 遍历顺序保留来源规则，包括重叠谓词的原有顺序行为。输出物品数和随机位置／速度沿用来源。常见无关掉落物先用物品身份集合过滤，不为每个物品遍历完整配方表；服务器处理后通过原生实体同步下发结果。世界卸载清理计时，监听器没有持有已卸载世界。

完成的定义：

- `transform/chipped_budding_energized_crystal`：水中；受损母岩与通能水晶。
- `transform/damaged_budding_energized_crystal`：水中；通能水晶块与通能水晶。
- `transform/flawed_budding_energized_crystal`：水中；碎裂母岩与通能水晶。
- `transform/energized_fluix_crystal`：水中；通能水晶粉与福鲁伊克斯水晶。
- `transform/crystal_ingot`：爆炸；四种来源粉料／基材。
- `transform/energized_superconductive_ingot`：爆炸；四种来源粉料／基材。

## 真实 FML 与世界验收

使用 JVM 参数 `-Dneoecoae.recipeAcceptance=true`：

1. `SourceRecipes.register` 安装完毕后调用 `NativeRecipeAcceptance.checkRegistered()`。逐条核对全部 164 个定义的原生注册或机器目录、实际输入匹配和来源产物；工作站另查数量不足拒绝、能量和时间，高炉另查 100 tick 与经验，冷却另查消耗量、冷却量、超频门槛，九个存储盘另查完整拆解返回。蓝冰必须由九个浮冰在真正的 `CraftingManager` 中得到独立新物品。成功标记为 `NEOECOAE_NATIVE_RECIPES_OK`。
2. 进入主世界后，世界 tick 自动依次测试六个配方。在出生点附近原本为空气的高空小区域，建立临时封闭水槽，实际投放输入并等待服务器 tick；第 60 tick 必须没有输出，第 61 tick 必须正确产出。爆炸配方调用真实 `World.newExplosion`，检查精确消耗与锚点剩余物免伤。每个实验结束清理自己建立的临时方块和实体。全部成功标记为 `NEOECOAE_NATIVE_WORLD_RECIPES_OK transformations=6 water=4 explosion=2 water_delay=61 exact_consumption=true anchor_survives=true`。

此开关只用于独立验收存档。源配方目录中 `installed:workstation_directory`、`installed:cooling_directory`、`installed:blasting_directory` 仍只证明相应宿主可查询真实配方对象；机器实际执行、库存事务和订单交付由后续工作站／GT／执行批次证明。

## 本批构建证据

```powershell
.\gradlew.bat spotlessJavaApply test --tests '*NativeTransformRecipeTest' --tests '*NativeMachineRecipesTest' --tests '*SourceShapelessRecipeTest' --offline --console=plain --max-workers=2
```

原生主源码和测试源码编译成功，11 项定向测试全部通过。新增四项覆盖第一输入／任意输入的触发差异、水／熔岩身份、同实体多份消耗、失败无部分消耗及来源顺序。日志为本分支 `build/recipe-world-test.log`。提交时尚未运行上述真实 FML／世界验收；由主控在统一原生实例运行并汇总证据，不能把这 11 项单元测试写成原生世界已经通过。
