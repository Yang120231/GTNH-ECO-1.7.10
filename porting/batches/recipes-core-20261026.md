# 2026-10-26 核心机器配方目录与材料修复

固定业务来源为 `NeoECOAEExtension-1.21.1@f6003889a1352ea0d7e2e23a3868eb69943789ca`。本批没有选择来源工作区补丁，也没有重新套用全量适配。`IntegratedWorkingStationRecipe` 保留来源的计数匹配、每个输入槽的剩余数量、流体门槛、默认 100 tick 和能量总预算；`CoolingRecipe` 保留来源的消耗量、冷却量、最大超频和副产物类型匹配。现代编解码器、游戏配方类型和同步接口由原生平台目录承担，空栈使用 1.7.10 的 `null`。原生可变堆栈在构造和输出时复制，避免机器或查看器改坏公共配方。

`SourceRecipes` 将 30 条 `neoecoae:integrated_working_station` 和 2 条 `neoecoae:cooling` 编译为可查询的机器对象。机器通过 `WorkstationRecipes.getAll/getById/find` 取得工作站配方，通过 `cooling/findCooling` 取得冷却配方；数量均保持物品个数和流体 mB。目录注册不代表机器执行、订单或玩家界面已经验收。对应真实宿主由 GT／工作站批次接入。

8 条 `minecraft:blasting` 保持独立类型，进入 `BlastingRecipes.getAll/getById/find`。每条保留原来的输入谓词、产物、100 tick 和经验；没有注册成普通熔炉。`installed:blasting_directory` 明确表示宿主能够取得真实配方对象，运行效果仍须由实际高炉／GT 宿主证明。

1.7.10 没有蓝冰。三个来源配方 `crafting_vent`、`storage_vent`、`computation_cooling_controller_l4` 使用 `minecraft:blue_ice`，本批增加独立平台方块 `neoecoae:platform_blue_ice` 及九个浮冰合成一个蓝冰的工作台配方。来源 ID 显式解析到此方块，没有把浮冰当作蓝冰。它不属于固定来源的 70 个核心方块，也不改变核心创造栏数量。材质为不融化的浮冰材质，硬度 2.8，爆炸抗性 2.8，摩擦系数 0.989，玻璃音效；贴图取本地 Minecraft 1.21.1 客户端中的 `assets/minecraft/textures/block/blue_ice.png`，SHA-256 `7170dfc70083df07ee61cd65aeaeb7add46c9d11397e401babc2a36d82599b07`。增加六种语言名称。

`SourceFluids` 在 preInit 注册真实 Forge 流体 `neoecoae:cryotheum_solution`，供工作站输出、流体仓和 AE2 流体键使用；水和熔岩显式映射到原生 `FluidRegistry.WATER/LAVA`。输入标签只接受已声明的水和熔岩，未知标签或组件不会猜测替换。

## 原 49 条 pending 的完整归因

| 原因 | 数量 | 本批处理 |
| --- | ---: | --- |
| 缺少工作站配方类型适配 | 30 | 原生工作站目录与数量匹配 |
| 缺少冷却配方类型适配 | 2 | 原生冷却目录与门槛匹配 |
| 缺少蓝冰物品 | 3 | 独立蓝冰平台方块与原版合成 |
| 缺少 blasting 类型适配 | 8 | 独立 100 tick 类型目录，宿主另验 |
| 缺少 AE2 世界转化类型适配 | 6 | 下一批处理水中／爆炸实际触发 |

原始证据为主工作区 `run/client-acceptance/logs/fml-client-latest.log` 的 2597 至 2646 行（2026-10-26 10:00:35）：164 条定义、115 installed、49 pending。本批预期目录为 158 条已安装、6 条待世界转化；这不是新的原生运行结果。

## 验证

定向测试覆盖工作站重复输入不复用同一物料、输入不足、流体身份与 mB、不修改输入、输出副本隔离、冷却副产物仓规则、100 tick 高炉经验契约。构建与测试命令：

```powershell
.\gradlew.bat spotlessJavaApply test --tests '*NativeMachineRecipesTest' --tests '*SourceShapelessRecipeTest' --offline --console=plain --max-workers=2
```

本批的验证结果记录在对应提交正文中。下一批必须通过真实注册与世界行为夹具确认所有定义进入实际目录；完整工作站、GT 加工、保存重载和下单交付另有独立验收边界。
