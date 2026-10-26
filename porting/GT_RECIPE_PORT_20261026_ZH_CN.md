# GT 联动配方迁入记录（2026-10-26）

本次用户明确要求 GT 联动配方对照 1.20.1，并以编译作为验收，不启动客户端、专服或鼠标验收。其它业务继续锁定 1.21.1 `f6003889a1352ea0d7e2e23a3868eb69943789ca`。

## 来源与范围

- 已刷新 `NeoECOAEExtension-1.20.1` 的 origin 与 yang120231，采用主线 `origin/1.20.1` = `024b73da2eed6113934d39f0a684e3dc92177c20`；不采用另行演进的 GTO 分支。
- 配方源为该提交 `src/main/resources/recipe_profiles/gtm` 和 `gt_legacy`；前者定义 GTM 进度覆盖，后者定义原生 GT 机器配方。
- GTNH 使用现有固定编译依赖 `GT5-Unofficial:5.09.52.398`，对应源码标签提交 `a40199ca4267862155728c85c4d0db38b84503b0`。已刷新 GT5 仓库再阅读，同时按该固定标签核查实际 API。
- `neoecoae/gt-1201/catalog.json` 记录来源提交、99 个实际复制文件的源路径和 SHA-256，以及 19 个排除文件的源路径、SHA-256 与原因。复制文件保持原文字节，平台在加载时转换字段名。

## 实际接入

安装 gregtech 时启用 GTM 覆盖；未安装时不加载任何 GT 适配实现，继续使用原独立配方。FML annotation 与 `mcmod.info` 都声明可选的 GT 加载顺序，GT 不进入 requiredMods。

- 19 条 GT 原生配方写入 `RecipeMaps.mixerRecipes`、`alloySmelterRecipes`、`blastFurnaceRecipes`、`autoclaveRecipes`、`circuitAssemblerRecipes`、`assemblerRecipes`、`maceratorRecipes`。源定义共 20 条，其中空模块卡那一条按用户要求排除。
- 30 条核心工作站配方保留源输入数量、输出数量、AE 能量、100 tick 加工时间及流体数量；保留包括无限组件 128 个 256M 组件、64000 mB 冷却液等大数量。
- 其余 GTM 源覆盖保留 27 条明确禁用的旧配方、材料转换、工具、外壳与压印器配方，以及 6 个私有材料标签。覆盖在独立配方注册之前应用，避免保留低成本旧路线。
- GT 原生配方保持源时长及 EU/t，黑钨合金 3600 K 炉温与处理器洁净室条件分别映射 `COIL_HEAT` 和 `CLEANROOM`。GT EU 与工作站 AE 能量分开处理。
- GTM HV/EV/IV/ZPM 电路标签分别对应 GTNH `circuitAdvanced`/`circuitData`/`circuitElite`/`circuitUltimate`，保留 EV、IV、ZPM 分级。
- 铝、钨、钛、不锈钢、塑料、硅、超导材料、原生部件及氩气、蒸馏水、熔融焊锡均通过真实 GTNH 物品、OreDictionary 与流体身份解析，不注册替代占位物。

## 明确的平台差异与排除

1. GTNH 合金炉要求至少两个输入槽，并拒绝单个金属粉配方。`b03_aluminum_alloy_ingot` 增加零消耗铸锭模具；仍消耗 1 个铝合金粉，产出 1 个铝合金锭，保留 200 tick、120 EU/t。
2. GTM 私有超导标签将汞钡钙铜氧锭标为 optional。GTNH 没有相同材料，跳过该可选成员，保留真实的铌钛、钒镓、钇钡铜氧三种原有选择。
3. 用户既有排除继续适用：4 个 GTM 专用模块配方、1 个 GT 原生空模块卡配方、2 个 Advanced AE 反应室配方、12 个依赖 AE2OmniCells/Mekanism 的可选存储配方均只保留排除清单，不复制、注册或打包配方原文。
4. GTL 不是 GTNH 平台；其依赖与配方包不导入。`gt_modern` 是同一批 GTM 机器配方的另一套 serializer schema，GTNH 直接使用 RecipeMaps，不重复导入该序列化变体。

## 验证边界

`compileJava --offline` 已通过；`GTRecipeProfileTest` 与 `NativeGregTechLoadingTest` 共 4 个测试通过，覆盖来源字节校验、实际授权配方数量、数量/能量/时长不变、炉温/洁净室/EV 门槛、GTNH 电路命名和完全缺失 GT 类时公共入口加载。

既有 GT runtime 依赖修正和 opt-in `NativeGTMachineAcceptance` 夹具予以保留。本次未运行任何游戏进程或验收夹具；编译与静态测试不宣称游戏内结果。
