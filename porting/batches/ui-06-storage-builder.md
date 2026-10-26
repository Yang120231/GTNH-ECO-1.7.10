# 存储主机真实建造窗口

固定来源 `f6003889a1352ea0d7e2e23a3868eb69943789ca`。来源路径及本批文件哈希见同名 JSON。

- 存储主机迁入来源的建造参数字段、14 个 `MultiBlockBuildController.Host` 方法与服务端 construction 阶段，保存长度和镜像设置。
- 原生 MUI2 浮动窗口显示服务端实际生成的缺失、冲突、复用、材料需求和玩家真实库存。长度／镜像改变通过当前物理主机菜单执行，创建／退出存档后的过期会话不接受修改。
- 24 个材料格显示实际所需数量和材料足量状态，冲突提示显示前 8 个坐标及剩余数量。建造按钮调用来源控制器，遵守形成状态、权限、材料与逐方块放置规则。
- 存储主机工具栏新增建造入口。原生替换组件记为平台适配，没有把现代 LDLib2 布局源码算作整文件迁移；可选集成仍保持排除。

验证命令：

```powershell
.\gradlew.bat spotlessApply test --tests '*NativeBuilderSessionTest' --tests '*MultiBlockPlacementServiceTest' --tests '*NativeStorageFiltersTest' --tests '*NativeUiAccessTest' --offline --console=plain --max-workers=2
```

主源码及测试编译、11 项定向测试通过。4 项新增测试覆盖库存变化与不扣样品、创造模式仍拒绝冲突、形成／建造状态优先级和有界坐标预览。日志 `build/ui-storage-builder-test.log`。

此批完成实际状态和操作接线，真实存档中的生存搭建／事件取消恢复及窗口截图尚待下一批验证，不把编译或程序化 packet 验证称为真实鼠标操作验收。
