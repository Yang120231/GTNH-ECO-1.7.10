# 1.7.10 Lightweight Modern Model Pipeline

本文档记录 Neo ECO AE Extension 1.7.10 移植版里的轻量现代模型管线。它的目标不是完整复刻 1.20.1/1.21.1 的模型系统，而是在 Forge 1.7.10 中用可控的小底层渲染 Blockbench 风格的 `models/block/*.json` 方块模型。

## 目标

- 读取现代版本常见的 block model JSON。
- 将 element/face 烘焙成 `Tessellator` 可以直接提交的 quad。
- 世界中通过 `ISimpleBlockRenderingHandler` 渲染静态模型。
- 物品栏、CreativeTab、手持和掉落物通过原版 `RenderBlocks.renderBlockAsItem` 转入 block inventory renderer，外层姿态交给 1.7.10 原版处理。
- 不依赖 GTNH 渲染辅助库，底层只使用 Forge 1.7.10、`Tessellator`、`IIcon`、`GL11`。

当前已经接入两类用法：

- `eco_drive`、`computation_drive`：有朝向、有 empty/full 两份模型，后续可以接 TileEntity 状态。
- `aluminum_alloy_casing`、`black_tungsten_alloy_casing`、`storage_casing`、`computation_casing`、`crafting_casing`、`storage_vent`、`crafting_vent`、`crafting_pattern_bus`、`input_hatch`、`output_hatch`：普通静态模型方块。

## 资源布局

现代 JSON 保留在本 mod 自己的资源目录中：

```text
src/main/resources/assets/neoecoae/models/block/*.json
```

贴图仍按 1.7.10 的 block 贴图规则放在：

```text
src/main/resources/assets/neoecoae/textures/blocks/**/*.png
```

现代模型里的贴图 id 按下面规则映射到 1.7.10 icon：

```text
neoecoae:block/storage/drive/drive_north
-> neoecoae:storage/drive/drive_north
-> assets/neoecoae/textures/blocks/storage/drive/drive_north.png
```

也就是说，JSON 中继续写 `neoecoae:block/...`，但是 `BlockModelDrive` 和 `BlockModernModel` 注册 icon 时会去掉 `block/`，交给 1.7.10 的 `registerIcon` 解析到 `textures/blocks`。

## 支持的 JSON 子集

入口类是 `cn.dancingsnow.neoecoae.client.render.model.ModernModelLoader`。

已支持：

- `parent`：支持短 parent 链；`block/block` 会被当成空父级跳过。
- `block/cube_all` / `minecraft:block/cube_all`：会生成标准 16x16x16 六面模型，使用 `#all` 贴图。
- `textures`：支持 `#key` 引用解析。
- `elements`：支持 axis-aligned cuboid。
- `from` / `to`：按 0-16 模型坐标读取，烘焙时缩放到 0-1。
- `faces`：支持六个方向的 face。
- `uv`：支持普通 UV 和倒置 UV，例如 `[16, 0, 1, 16]`。
- `texture`：支持 `#texture_key` 到实际贴图 id 的解析。
- `cullface`：世界渲染中用于邻接实心方块遮挡判断。
- face `rotation`：支持 0/90/180/270 的面 UV 旋转。
- `display.gui`：会被解析并保留；当前默认物品路径走 1.7.10 原版方块物品姿态，不主动套用该 transform。

暂不支持：

- multipart / blockstate variant 系统。
- Forge custom model loader。
- tint index。
- ambient occlusion 语义。
- 透明层、发光层、多 pass 渲染。
- 非 0 的 element rotation。当前遇到非 0 旋转会记录 warning 并跳过该 element。

## 核心类职责

模型数据层：

- `ModernModel`：解析后的模型结构，包含 textures、elements、display transform。
- `ModelElement`：单个 cuboid element 的 `from/to` 和 faces。
- `ModelFace`：单个 face 的方向、贴图引用、UV、cullface、face rotation。
- `ModelDisplayTransform`：当前主要保存 `display.gui`。
- `ModelFacing`：水平朝向枚举，当前用 meta `0/1/2/3` 表示 north/east/south/west。

加载与烘焙：

- `ModernModelLoader`：从 `assets/neoecoae/models/block/*.json` 读取 JSON 并构造成 `ModernModel`。
- `BakedEcoModel`：把 `ModernModel` 预烘焙成四个水平朝向的 quad 列表。渲染时不再做顶点旋转。
- `BakedQuad`：一次提交给 `Tessellator` 的四边形数据，包含顶点、UV、normal、cull direction、texture id。

渲染：

- `EcoModelRenderer.renderWorld`：世界渲染入口。负责平移到方块坐标、cullface 判断、亮度、颜色和 quad 提交。
- `EcoModelRenderer.renderInventoryBlock`：`ISimpleBlockRenderingHandler.renderInventoryBlock` 的绘制入口。CreativeTab、背包、手持和掉落物都会经由原版 `RenderBlocks.renderBlockAsItem` 转到这里。

注册与接入：

- `ClientProxy.registerRenderers`：分配 render id，加载模型，注册 block renderer。
- `BlockModelDrive`：驱动器类方块基类，统一处理朝向 metadata、icon 注册、particle icon 和 empty/full 模型名。
- `DriveModels`：按驱动器模型名缓存 empty/full 两份 `BakedEcoModel`。
- `DriveRenderHandler`：驱动器类方块的世界/block inventory 渲染。
- `ModernBlockRenderHandler`：普通现代模型方块的世界/block inventory 渲染。

## 驱动器模型方块接入

驱动器类方块使用 `BlockModelDrive`。`BlockEcoDrive` 和 `BlockComputationDrive` 只是薄包装，分别提供自己的注册名、empty/full 模型名、贴图列表和 particle 贴图。

关键行为：

- `onBlockPlacedBy` 根据玩家 yaw 写入 metadata。
- metadata `0/1/2/3` 映射到 `ModelFacing.NORTH/EAST/SOUTH/WEST`。
- `renderWorldBlock` 读取 metadata，选择对应 baked 朝向。
- 当前世界中默认渲染 `DriveVisualState.EMPTY`。
- `DriveModels` 同时加载 empty 和 full 模型，为后续 TileEntity 状态切换预留。
- 碰撞盒仍保持完整方块，避免凹槽模型影响交互、寻路和邻接判断。

当前驱动器样例：

- `eco_drive`：`eco_drive_empty` / `eco_drive_full`。
- `computation_drive`：`computation_drive_empty` / `computation_drive_full`。

如果放置朝向不对，优先检查：

- `BlockModelDrive.getFacingMetaFromYaw`
- `ModelFacing.fromMeta`
- `BakedEcoModel.rotateY`
- `BakedEcoModel.rotateDirection`
- `DriveRenderHandler.renderWorldBlock`

如果 CreativeTab/背包方向不对，优先检查：

- `EcoModelRenderer.renderInventoryBlock`
- `DriveRenderHandler.renderInventoryBlock`
- 原版 `RenderBlocks.renderBlockAsItem` 进入自定义 render id 后的坐标约定

## 普通现代模型方块接入

普通方块使用 `BlockModernModel`，适合没有状态、没有 TileEntity、没有额外动态层的静态模型。
如果模型需要在放置时按玩家水平朝向旋转，使用 `BlockDirectionalModernModel`；它和驱动器共用 `0/1/2/3 = north/east/south/west` 的 metadata 规则。

新增一个模型方块时通常做这些事：

1. 把 JSON 放到 `assets/neoecoae/models/block/<model_name>.json`。
2. 把贴图放到 `assets/neoecoae/textures/blocks/...`。
3. 在 `NEBlocks` 中用 `modelBlock(id, modelName, textures)` 创建方块；方向型方块用 `directionalModelBlock(id, modelName, textures)`。
4. 在 `NEBlocks.register` 中用 `ItemBlockModernModel.class` 注册。
5. 在 `ClientProxy.registerRenderers` 中调用 `ModernBlockModels.load(modelName)`。
6. 在 `en_US.lang` 和 `zh_CN.lang` 中补名字。

`BlockModernModel` 的 `textures` 数组必须包含模型会用到的所有现代贴图 id。原因是 1.7.10 的 icon 需要在 `registerBlockIcons` 阶段集中注册，渲染时只从 map 中查 `IIcon`。

## 明暗和遮挡

世界渲染和物品栏渲染的明暗是分开的。

世界渲染：

- `EcoModelRenderer.renderWorld` 使用 `block.getMixedBrightnessForBlock` 取邻接方向亮度。
- `getWorldShade` 按 face normal 乘一个固定 shade。
- `cullface` 只在世界渲染里生效，邻接方块 `isOpaqueCube()` 时跳过对应 face。
- 破坏裂纹渲染时会尊重 `RenderBlocks.overrideBlockTexture`，否则自定义模型会在挖掘阶段显示成过亮的原贴图。

物品栏渲染：

- `renderInventoryBlock` 使用 `getInventoryShade`。
- 物品栏的下表面 shade 比世界更亮，避免模型底部过黑。
- 物品栏渲染会关闭 `GL_CULL_FACE`，避免角度变化时背面消失。
- 手持、掉落物和 GUI 都先走原版 `RenderBlocks.renderBlockAsItem`，因此缩放、旋转和 GUI 位置尽量保持 1.7.10 原版方块行为。
- 如果未来要强制使用 JSON `display.gui` 做专用预览，应新建一个很薄的 GUI-only item renderer，而不是重新接管手持和掉落物姿态。

如果模型下部偏暗，优先调：

- `EcoModelRenderer.getInventoryShade`
- `EcoModelRenderer.getWorldShade`
- `renderWorld` 中取亮度的邻接坐标

如果模型被错误遮挡，优先检查：

- JSON face 的 `cullface`
- `BakedEcoModel.rotateDirection`
- `EcoModelRenderer.shouldCull`
- 方块自己的 `isOpaqueCube`

## 性能策略

当前管线有几个刻意的限制：

- 模型在 client init 或第一次访问时解析。
- 每个模型会缓存四个水平朝向的 baked quad。
- 渲染时不解析 JSON、不旋转顶点，只做 icon 查询、亮度计算和 quad 提交。
- 静态外壳走 `ISimpleBlockRenderingHandler`，不使用 TESR。

后续如果要加动态内容，建议只把动态小层交给 TileEntity/TESR，例如：

- ECO Drive / Computation Drive 插槽里的 cell 可见状态。
- LED 灯。
- 发光或闪烁效果。

静态外壳仍然保留在当前 baked model 管线里。

## 已知限制和后续工作

- Drive 类方块还没有接 TileEntity，`full` 模型暂时只是预加载备用。
- 没有实现 cell 插入后的局部 overlay。
- 没有实现 LED 发光、fullbright 或 bloom 类效果。
- 没有实现非 0 element rotation，遇到这类模型需要先在 Blockbench 中转成 axis-aligned element，或者扩展 `BakedEcoModel`。
- 没有实现现代 blockstate variant，因此同一个 block 的多状态模型需要先由代码选择不同 `BakedEcoModel`。
- 没有实现透明/半透明 pass，需要额外接 Minecraft 1.7.10 的 render pass 逻辑。

## 快速排查表

| 现象 | 优先检查 |
| --- | --- |
| 紫黑缺失贴图 | `BlockModelDrive` 子类贴图列表 / `BlockModernModel.textureNames` 是否包含 JSON 实际引用的贴图 id |
| 动画贴图紫黑 | 对应 `textures/blocks/*.png.mcmeta` 是否随 PNG 一起迁移 |
| 世界模型方向错误 | `getFacingMetaFromYaw`、`ModelFacing.fromMeta`、`BakedEcoModel.rotateY` |
| 物品栏模型方向错误 | `EcoModelRenderer.renderInventoryBlock`、`RenderBlocks.renderBlockAsItem` 的坐标约定 |
| 挖掘时整块发白 | `EcoModelRenderer.renderWorld` 是否拿到了 `RenderBlocks.overrideBlockTexture` |
| 模型底部太暗 | `getInventoryShade` 或 `getWorldShade` |
| 相邻方块导致凹槽消失 | JSON 的 `cullface` 是否只写在外壳外侧面 |
| 世界能看到，CreativeTab 看不到 | `shouldRender3DInInventory`、`renderInventoryBlock`、`getRenderType` |
| CreativeTab 能看到，世界看不到 | block render id、`ISimpleBlockRenderingHandler` 注册、`getRenderType` |

## 编译验证

代码或资源调整后运行：

```bat
.\gradlew.bat build
```

如果只改本文档，不需要重新编译。渲染相关改动建议同时用 `runClient` 做实机检查，因为方向、UV、cullface 和明暗问题很难只靠编译发现。
