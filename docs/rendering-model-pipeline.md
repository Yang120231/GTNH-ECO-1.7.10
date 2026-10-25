# 保留的现代模型链路

当前 `client/render/model` 保留模型解析、数据对象、四朝向烘焙、四边形绘制、光照采样、渲染层和 icon 名称转换。

`ModernModelLoader` 读取 `assets/<namespace>/models/block/*.json`，支持 parent、cube_all、纹理引用、cuboid elements、UV 与面旋转、children composite 展开、模型/面 render_type，以及 neoforge_data/forge_data 满亮度数据。非零 element rotation 仍会跳过。

世界绘制通过 `EcoModelRenderer.renderWorld` 使用 Forge 1.7.10 的 Tessellator、IIcon 和渲染 pass；物品绘制入口为 `renderInventoryBlock` 和 `renderItemModel`。`BakedEcoModel` 预烘焙四个水平朝向，避免绘制时重新解析 JSON。

旧成型缓存和旧控制器查询已改接 `platform/render/ModelRenderState`。实现与绑定方式见 [当前骨架说明](../porting/README_ZH_CN.md)。后续方块、物品与资源注册由高版本业务的目标平台适配器完成。

现代贴图标识 `neoecoae:block/...` 通过 `ModernIconRegistrar` 转为 1.7.10 的 icon 标识 `neoecoae:...`，目标纹理放在 `assets/neoecoae/textures/blocks/...`。当前旧模型与纹理已清空，正式导入时从高版本资源清单生成目标布局。

当前保留测试验证 render_type 到两个世界 pass 的映射、烘焙时保留渲染层、composite 子模型渲染层和面级覆盖。模型显示、真实材质、方向、UV 和交互的客户端验收属于后续正式移植。
