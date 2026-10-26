# 存储接口与优先级页面

业务输入固定为 `f6003889a1352ea0d7e2e23a3868eb69943789ca`；逐个来源与原生转接哈希见同名 JSON。现代 LDLib2 控件没有作为整文件迁入计数。本批使用既有来源布局 JSON、图集及已落地宿主能力，提供原生 MUI2 页面。

- 存储接口现在可以右键打开，显示真实的三种模式、无限结构可用性、网络连通与每 5 tick 采样的转移速度。切换模式、忽略创意供给、排除存储总线容器调用既有服务端业务并沿既有字段保存。
- 27 格过滤栏传输精确资源键。左键手持样本设置，右键清空；容器优先取液体，Shift 取物品本身，NEI 幽灵拖放只发送样本。取样先复制容器，不转移物品、不灌注或排出液体。失效的持久化过滤保留“资源不可用”提示，不降级成允许全部。
- 存储主页面增加优先级按钮，打开来源布局的独立可拖动弹窗。八个增减按钮及有符号 int 输入操作真实优先级；增减保留来源的整数边界饱和，并触发已有存储挂载刷新。标题映射到 1.7.10 AE2 的语言键，新增两条中英文提示解释写入／抽取顺序。
- 所有延迟操作校验同一菜单、同一物理 TileEntity、同一服务端世界、距离和菜单有效性。MUI2 通用操作和既有取盘／装盘会话捕获 `ServerTasks.Session`，防止旧世界回调进入重开的存档。

验证命令：

```powershell
.\gradlew.bat spotlessApply test --tests '*NativeStorageFiltersTest' --tests '*NativeUiAccessTest' --tests '*StorageInterfaceFilterTest' --offline --console=plain --max-workers=2
```

编译及 7 项定向测试通过，日志 `build/ui-storage-build.log`。纯 JVM 流体样本使用模拟 FluidStack，避免触发未引导的 Forge 全局 FluidRegistry；真实水桶、菜单显示及鼠标动作须在后续独立客户端完成，本批尚未宣称这些运行验收完成。

依赖执行方向的规划生命周期及 `ServerTasks.Session` 两批（原始提交 `c60eb1e`、`b400673`）。尚未提供构建／引导、合成／计算／工作站与订单确认页面。
