# 2026-10-26 大型工作站完整数量合同与整批倍率

本批以固定业务提交 `f6003889a1352ea0d7e2e23a3868eb69943789ca` 的 `LargeWorkstationRecipe` 为基础，保留原有完整输入／输出合同、附加资源、附加流体／产物、正整数倍率、处理时间上限与预算校验。没有导入任何可选 Mod 的配方解码器，也没有接入已排除的七张大型工作站专用卡。

`LargeWorkstationRecipes` 只抽取来源的 `find(List,...)`、`findScaled(List,...)`、`COST_ORDER`、`compareCosts`、`extraCost`、`energyKind`、`isLightning` 和 `PatternMatch` 成员。原生目录适配来自当前真实的 `WorkstationRecipes`，每个来源 ID 仍指向原来的输入、输出和 AE 总预算。当前固定核心目录包含 30 条小型工作站来源配方，大型工作站可以按来源规则执行它们。

对 GT／大型工作站宿主的接口：

```java
List<LargeWorkstationRecipe> LargeWorkstationRecipes.getAll(World world);
LargeWorkstationRecipe LargeWorkstationRecipes.getById(World world, ResourceLocation id);
LargeWorkstationRecipe LargeWorkstationRecipes.find(World world, KeyCounter inputs, KeyCounter outputs);
LargeWorkstationRecipes.PatternMatch LargeWorkstationRecipes.findScaled(
    World world, KeyCounter inputs, KeyCounter outputs);
// PatternMatch: recipe(), multiplier()
```

`matchingMultiplier` 要求每一种输出共用相同正整数倍率，完整输入也必须等于该倍率所需数量；不足、多余输入、额外输出和分数操作均不接受。来源的整数最大流处理重叠标签，不逐份展开大数量，也不贪心占用后续必须使用的物料。成本选择按实际倍率比较总预算，预算或 tick 乘法溢出的候选不能挡住另一个可执行候选。大型处理时间最多 200 tick，不改写小型原配方时间或总能量。

平台差异只有原生资源键／可变堆栈与 Java 8 兼容：流体输出转换为原生 AE2 流体键；当前流体谓词只包含明确的流体身份，因此通过原生流体身份判断，避免为一次匹配建立无意义的临时流体堆栈；数量仍由原最大流算法独立核验。`KeyCounter.removeAll` 展开为逐项删除，保持相同有符号差额。配方目录只缓存不可变配方，没有持有世界。

来源 `LargeWorkstationRecipeTest` 选入五个纯核心测试及两个辅助成员：重叠标签重新分配、巨大计数和多余输入拒绝、完整输出合同、分数倍率／输入倍率拒绝、多种具体物品满足不同整批操作。可选联动与模块卡测试未迁入。另有四个原生边界测试验证物品／流体共同倍率、溢出候选回退、实际缩放能量比较及 200 tick 上限。本批也在世界卸载时显式移除开发验收完成标记，便于同 JVM 再次进入世界重新验收。

构建与测试结果见本批提交正文。机器仓库事务、网络下单、GT 加工及工作站物理宿主仍由对应方向实施；此批提供它们必须使用的来源数量与查找算法。
