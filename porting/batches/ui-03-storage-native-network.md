# 存储页面原生网络运行验收

独立 Java 8 客户端使用 `-Dneoecoae.storageUiNetworkAcceptance=true`。该参数创建新的 `ECO_Storage_UI_1792983703715` 测试存档，运行真实 MUI2 容器／同步包和原生库存取放；**这是程序化原生交互，不是人工鼠标验收**。Windows 官方 computer-use 通道当时报告 native pipe 不可用，没有尝试替代输入工具。

2026-10-26 本机日志 11:02:20 出现 `NEOECOAE_STORAGE_UI_NETWORK_OK`，并于 11:02:22 正常保存退出，只关闭该独立客户端。没有关闭用户的 1.20.1 实例。运行日志为 `build/client-storage-ui-network.log`，摘录提交在 `porting/acceptance/storage-ui-network.log`。

实际检查：

- 服务端优先级 73 → 1073，直接输入两个 int 极值后继续增减均饱和，再恢复 73。
- 通过 MUI2 自定义包从真实物理驱动器取盘、盘进入真实玩家光标，再装回原位置、光标清空。
- 接口 STORAGE → INPUT → OUTPUT 均收到服务端同步；两个开关实际从 true 改为 false。
- 使用原生菜单库存包取水桶，设置液体键和 Shift 对应物品键；清空液体格，再取 64 铁锭设置第 27 格精确物品键。
- 所有取样均不改变实际光标数量；样本归还后水桶仍为 1、铁锭仍为 64，光标为空。
- 真实接口 `writeToNBT` 和 `TileEntity.createAndLoadEntity` 往返保留模式、两个开关和精确过滤配置。

六张运行图位于独立工作目录 `run/client-acceptance/screenshots/2026-10-26_11.02.02.png` 至 `2026-10-26_11.02.19.png`，已查看主页面、优先级窗口、接口初始及过滤后画面。接口布局与实际物品显示正常。优先级窗口中输入框之后的负数字和提示显示为实心方块，**视觉验收未通过**；已定位为自定义文字绘制缺少 MUI2 的 `Platform.setupDrawFont()` 前置，将另批修复并重跑截图。

源码变化只增加显式验收开关和 dev-only inspection；没有修改业务算法。`ui-01-storage-controls.json` 仍保留首批快照哈希，统一清单应读取当前目标文件记录后续变更。
