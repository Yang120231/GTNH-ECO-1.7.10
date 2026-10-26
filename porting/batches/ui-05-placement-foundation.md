# 真实多方块搭建基础

固定来源 `f6003889a1352ea0d7e2e23a3868eb69943789ca`，本批 16 个业务文件、1 个完整来源测试文件的路径和哈希见同名 JSON。不是空壳页面或免费放置实现。

- 保留来源的构建计划、缺失／冲突／复用判断、精确物品身份合并、方向和镜像转换、逐方块延迟会话及构建控制器。创造模式可立即搭建，生存模式按实际成功放置扣材料；搭建中阻塞、材料不足或玩家下线会停止，已放置方块由后续预览复用。
- 原生边界校验已加载区块、边界、出生点／冒险权限，发送 Forge `PlaceEvent`。被取消时强制恢复先前 `BlockSnapshot` 的方块、元数据和 NBT；这与 1.7.10 的默认 `restore(false)` 不同，后者拒绝覆盖已变化的方块。
- 1.7.10 没有副手栏和通用 ItemStack 物品处理能力。主物品栏使用实际 `InventoryPlayer`；嵌套容器通过可选的原生 `ItemInventoryProvider` 暴露真实处理器，来源的递归及共享处理器去重算法保留。未新增任何可选 Mod 集成。
- 保留多方块需求替代项和扩展定义注册。原生等级描述符新增来源 `getBuildDefinition` 默认入口，以便插件提供实际定义。
- 三种存储主机定义保留来源全部几何与朝向。`NEMultiBlocks` 的合成／计算／大型工作站定义，以及 `MultiBlockContext` 的现代 LDLib2 虚拟世界预览，按 `partial-imports.json` 明确留待后续，未宣称整文件完成。

验证：

```powershell
.\gradlew.bat spotlessApply test --tests '*MultiBlockPlacementServiceTest' --tests '*NativeStorageFiltersTest' --tests '*NativeUiAccessTest' --offline --console=plain --max-workers=2
```

主源码与测试编译、7 项定向测试通过。来源 3 项回归覆盖冲突时零放置、材料不足时零世界修改，以及预览之后已出现的正确方块复用且不扣材料。日志 `build/ui-placement-foundation-build.log`。

本批提供业务基础，尚未把存储主机的构建委托和原生构建窗口接上，也尚未运行生存搭建存档；后续须验证真实扣料、取消事件恢复与保存后继续搭建。构建／渲染前景各自完成后再判断整体 UI 验收。
