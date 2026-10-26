# 存储 UI 人工交互夹具

`-Dneoecoae.storageUiAcceptance=true` 单独启用此开发夹具。它新建 `ECO_Storage_UI_<时间戳>` 超平坦存档，调用已有真实 48 部件／12 驱动器的存储预览夹具，给测试玩家水桶、64 铁锭和备用盘，然后打开存储主页面。正常游戏未启用该参数时不注册夹具或命令。

夹具不自动点击页面，不调用 UI session 的按钮／过滤／取盘方法。真实鼠标操作与自动网络动作验收明确分开。

专用客户端命令：

- `/neoecoae_ui_storage host`：打开真实主机菜单。
- `/neoecoae_ui_storage interface`：打开真实存储接口菜单。
- `/neoecoae_ui_storage snapshot`：记录服务端状态并截取当前画面。
- `/neoecoae_ui_storage finish`：保存并退出测试存档。

每 100 tick 服务端记录 `NEOECOAE_STORAGE_UI_OBSERVE`：真实优先级、接口模式、两个开关、精确过滤 NBT、光标物品与样本物品、当前菜单类型。日志是观察证据，不以出现该日志替代手势成功断言。

`spotlessApply compileJava --offline --console=plain --max-workers=2` 已通过，日志 `build/ui-fixture-build.log`。客户端复现：

```powershell
.\gradlew.bat -I porting/run-client-acceptance.init.gradle runClient --mcJvmArgs=-Dneoecoae.storageUiAcceptance=true --offline --console=plain --max-workers=2 *> build/client-storage-ui.log
```

实际鼠标取盘／装盘、优先级增减与输入、接口模式及开关、物品／流体过滤和右键清空仍须下一批运行记录确认。
