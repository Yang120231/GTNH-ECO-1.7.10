# 大型集成工作站 CraftTweaker 接口

安装 Minecraft 1.7.10 的 CraftTweaker 后，可以使用 ZenScript 修改大型集成工作站的加工配方。CraftTweaker 是可选依赖。

脚本保存到游戏目录的 `scripts/*.zs`。示例见 [large_workstation.zs](examples/large_workstation.zs)。接口使用 1.7.10 的 MineTweaker 3 API，开发编译和自动化验证使用 GTNH CraftTweaker 3.4.8。

## 添加配方

```zenscript
import mods.neoecoae.LargeIntegratedWorkingStation;

// 四个铁锭加工成两个金锭，总计消耗 42000 AE，基础用时 40 tick。
LargeIntegratedWorkingStation.addRecipe(
    "my_pack:gold_from_iron",
    <minecraft:gold_ingot> * 2,
    [<minecraft:iron_ingot> * 4],
    42000,
    40
);

// 使用矿辞输入，并消耗 1000 mB 水，同时产出物品与 250 mB 熔岩。
LargeIntegratedWorkingStation.addRecipe(
    "my_pack:diamond_with_fluid",
    <minecraft:diamond>,
    [<ore:ingotIron> * 8],
    100000,
    100,
    <liquid:water> * 1000,
    <liquid:lava> * 250
);

// 纯流体配方：物品产物传 null，物品原料传空数组。
LargeIntegratedWorkingStation.addRecipe(
    "my_pack:water_to_lava",
    null,
    [],
    50000,
    100,
    <liquid:water> * 1000,
    <liquid:lava> * 1000
);
```

参数依次为：

| 参数 | 类型 | 含义 |
| --- | --- | --- |
| `id` | `string` | 唯一配方 ID，必须采用 `命名空间:路径`，例如 `my_pack:gold_from_iron` |
| `output` | `IItemStack` | 物品产物，纯流体产出时可为 `null` |
| `inputs` | `IIngredient[]` | 0～12 项物品原料；每项可用 `* 数量`，支持矿辞和条件原料 |
| `energy` | `int` | 每次加工的**总 AE 消耗**，不是每 tick 消耗；允许为 0 |
| `ticks` | `int` | 基础加工时间，1～200 tick；实际速度继续受工作站超频、并行和供能影响 |
| `inputFluid` | `ILiquidStack` | 可选，最多一种流体输入，数量单位为 mB；缺省为 `null` |
| `outputFluid` | `ILiquidStack` | 可选，最多一种流体产物，数量单位为 mB；缺省为 `null` |

必须至少有一种输入和一种产物；物品、流体数量必须为正数。若只设置流体产物而没有流体输入，第六个参数传 `null`。

物品匹配保留 CraftTweaker 原料的匹配条件，NBT 要求可使用 `.onlyWithTag(...)` 等条件原料。`.withTag(...)` 本身遵循 CraftTweaker 的原有语义，不会自动变成严格 NBT 条件。不支持原料转换、返还工具、流体输入 NBT 条件及无具体物品示例的通配原料；不支持的参数会写入 CraftTweaker 错误日志。

这些配方出现在 NEI 的大型集成工作站分类中，并参与通讯接口的加工样板识别与整数倍样板匹配。NEI 样板转移仍受目标 AE2 样板终端可用槽位数限制；超过终端槽位数的配方需要使用容量足够的终端。

## 删除与替换配方

```zenscript
import mods.neoecoae.LargeIntegratedWorkingStation;

// 按 ID 删除，可用于默认配方及脚本配方。
LargeIntegratedWorkingStation.removeRecipe("my_pack:gold_from_iron");

// 按产物删除，支持物品、矿辞条件或流体。
LargeIntegratedWorkingStation.remove(<minecraft:diamond>);
LargeIntegratedWorkingStation.remove(<liquid:lava>);

// 清空当前大型工作站配方表，包括默认配方；需要在添加自定义配方之前调用。
// LargeIntegratedWorkingStation.removeAll();
```

默认加工配方的 ID 对应 `data/neoecoae/recipe/` 下的路径。例如，`integrated_working_station/energized_crystal.json` 对应 `neoecoae:integrated_working_station/energized_crystal`。

重复添加同一个 ID 会报错。替换时先调用 `removeRecipe(id)`，再用相同 ID 调用 `addRecipe(...)`。

所有修改仅作用于大型集成工作站。单方块集成工作站的配方表不受影响；删除大型工作站继承的默认配方也不会删除单方块版本中的对应配方。

## 重载

使用 `/minetweaker reload` 重新加载脚本。添加、删除、清空操作都通过 CraftTweaker 的可撤销操作执行：重载先恢复脚本修改前的配方状态，再按脚本顺序应用修改，不会重复累积配方。

重载会刷新大型工作站配方缓存、通讯接口的兼容样板识别以及新打开的 NEI 配方查询。已经开始的加工任务继续使用原有任务参数，修改后的配方用于后续识别与加工。

自动化测试覆盖脚本编译执行、重载撤销、配方匹配及参数校验；实际客户端内的 NEI 显示和机器加工仍需游戏内验证。
