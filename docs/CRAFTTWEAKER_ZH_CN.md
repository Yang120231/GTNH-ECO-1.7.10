# 集成工作站 CraftTweaker 接口

安装 Minecraft 1.7.10 的 CraftTweaker 后，可以使用 ZenScript 分别修改单方块集成工作站与大型集成工作站的加工配方。CraftTweaker 是可选依赖。

脚本保存到游戏目录的 `scripts/*.zs`，可参考下文的单方块与大型工作站示例。接口使用 1.7.10 的 MineTweaker 3 API，编译依赖为 GTNH CraftTweaker 3.4.8。

| 工作站 | 导入的脚本类 | 物品原料项数 |
| --- | --- | --- |
| 单方块集成工作站 | `mods.neoecoae.IntegratedWorkingStation` | 0～9 项 |
| 大型集成工作站 | `mods.neoecoae.LargeIntegratedWorkingStation` | 0～12 项 |

两种接口的方法和参数相同，但修改各自独立的配方表。大型工作站仍继承模组内置的默认配方；小集成的脚本修改不会自动传给大集成。需要同时调整两个阶段时，请分别调用两个接口。

## 添加配方

单方块集成工作站：

```zenscript
import mods.neoecoae.IntegratedWorkingStation;

IntegratedWorkingStation.addRecipe(
    "my_pack:gold_from_iron",
    <minecraft:gold_ingot> * 2,
    [<ore:ingotIron> * 4],
    42000,
    40,
    <liquid:water> * 1000
);
```

大型集成工作站：

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
| `id` | `string` | 当前工作站配方表内的唯一 ID，必须采用 `命名空间:路径`，例如 `my_pack:gold_from_iron` |
| `output` | `IItemStack` | 物品产物，纯流体产出时可为 `null` |
| `inputs` | `IIngredient[]` | 单方块 0～9 项、大型工作站 0～12 项物品原料；每项可用 `* 数量`，支持矿辞和条件原料 |
| `energy` | `int` | 每次加工的**总 AE 消耗**，不是每 tick 消耗；允许为 0 |
| `ticks` | `int` | 基础加工时间，1～200 tick；实际速度继续受对应工作站的加速、超频、并行和供能影响 |
| `inputFluid` | `ILiquidStack` | 可选，最多一种流体输入，数量单位为 mB；缺省为 `null` |
| `outputFluid` | `ILiquidStack` | 可选，最多一种流体产物，数量单位为 mB；缺省为 `null` |

必须至少有一种输入和一种产物；物品、流体数量必须为正数。若只设置流体产物而没有流体输入，第六个参数传 `null`。

小集成仍受九个输入槽、一个物品输出槽以及输入、输出各 64000 mB 储罐的限制，配方数量应能装入机器；脚本不会扩大机器容量。

物品匹配保留 CraftTweaker 原料的匹配条件，NBT 要求可使用 `.onlyWithTag(...)` 等条件原料。`.withTag(...)` 本身遵循 CraftTweaker 的原有语义，不会自动变成严格 NBT 条件。不支持原料转换、返还工具、流体输入 NBT 条件及无具体物品示例的通配原料；不支持的参数不会应用配方变更。

这些配方出现在 NEI 中对应工作站的分类中。大集成配方还参与通讯接口的加工样板识别与整数倍样板匹配。NEI 样板转移仍受目标 AE2 样板终端可用槽位数限制；超过终端槽位数的配方需要使用容量足够的终端。

## 删除与替换配方

下面以大集成为例；修改小集成时，将导入和调用中的 `LargeIntegratedWorkingStation` 换为 `IntegratedWorkingStation`。

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

每个接口只修改自己的工作站配方表。删除或替换其中一方的默认配方，不会改变另一方；两种工作站也可以使用相同 ID 注册不同的加工配方。若要禁用某条配方的两种加工途径，需要分别删除。

例如，同时移除两个工作站中的默认充能水晶配方，再按整合包流程分别添加替代配方：

```zenscript
import mods.neoecoae.IntegratedWorkingStation;
import mods.neoecoae.LargeIntegratedWorkingStation;

IntegratedWorkingStation.removeRecipe("neoecoae:integrated_working_station/energized_crystal");
LargeIntegratedWorkingStation.removeRecipe("neoecoae:integrated_working_station/energized_crystal");
```

## 重载

使用 `/minetweaker reload` 重新加载脚本。两个接口的添加、删除、清空操作都通过 CraftTweaker 的可撤销操作执行：重载先恢复脚本修改前的配方状态，再按脚本顺序应用修改，不会重复累积配方。

小集成会重新匹配当前输入，并唤醒因无配方而休眠的机器。当前匹配配方被删除、替换或因脚本重载重新创建时，其加工进度归零，尚未消耗的物品与流体保留；未变化的内置配方保留加工进度。新打开的 NEI 配方查询使用重载后的配方表。

大集成继续刷新配方缓存和通讯接口的兼容样板识别；已经开始的加工任务使用原有任务参数完成，修改后的配方用于后续识别与加工。

实际客户端内的脚本重载、NEI 显示和机器加工需要游戏内验证。
