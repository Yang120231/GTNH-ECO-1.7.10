# AE2 接口终端接入检查（2026-11-09）

检查范围为本仓库全部实体样板库存、原生合成提供者、接口终端注册和相邻的样板上传发现路径。依据当前源码与固定依赖 AE2 `rv3-beta-1095-GTNH`；编译检查不等同于游戏内验收。

## 接口终端

| 对象 | 检查结果 |
| --- | --- |
| 合成子系统 FD 智能样板总线 | 本次补齐 `IInterfaceViewable`，注册实际实体类 `CraftingMachines.CraftingPatternBus`，按 7 行、每行 9 格暴露原有 63 槽库存。支持终端原生列表、搜索、定位与取放；标记为合成样板提供者，兼容终端的合成提供者筛选。 |
| 大型集成工作站通讯接口 | 已实现 `IInterfaceViewable`，已注册实际实体类 `WorkstationMachines.Interface`。行数跟随容量升级；可通过接口终端可见性设置隐藏。 |
| 合成通讯接口、网络交换机、控制器、工作核心、普通集成工作站 | 不独立保存样板，使用总线库存或接收派发任务，无需重复注册。每个总线仅显示自己的实际槽位，避免共享域的样板被重复展示。 |
| 计算子系统及存储子系统 | 计算盘、模糊规划样本、存储盘和无限组件库存不属于样板提供者，不注册到接口终端。 |

源码检索未发现其他遗漏的 ECO 实体样板容器。关键入口为 `CommonProxy.init`、`ECOCraftingPatternBusBlockEntity`、`ECOLargeIntegratedWorkingStationInterfaceBlockEntity`、`LargeWorkstationPatternProvider`。

接口终端使用 AE2 原生 `ContainerInterfaceTerminal` 的槽位操作，直接调用总线库存的 `isItemValidForSlot`、`setInventorySlotContents` 和 `decrStackSize`。总线只接受可解码的合成样板，现有库存回调负责保存及合成目录刷新。

## 自动样板上传

本次补齐大型集成工作站的 **ECO 自动样板上传目标列表**接入。`Ae2UploadDiscovery.add` 在通用通讯接口分支前识别大型工作站，复用 `Ae2InterfaceTarget` 和既有上传事务，不再被合成域类型检查提前排除。

上传前复用 `LargeWorkstationPatternProvider.accepts` 的非写入配方校验，拒绝不兼容样板和仅输入的隧道定义。实际写入使用原有样板库存，遵守容量升级所解锁的槽位；重复样板检查、同库存去重、提交时重新评估、写入回读及失败回滚均沿用现有框架。结构解体、节点离线或接口被替换后不再允许上传。自动上传发现遵守接口终端的可见性设置，并在实际上传前重新检查；隐藏的接口及样板输入端不会继续接收自动上传。

合成子系统已有自动上传域适配，本次新增终端注册不会改变上传域的去重及落槽方式。

本次检查范围内的两类 ECO 实体样板容器均已接入接口终端和自动上传发现，未发现其他遗漏。

## 验证边界

Gradle 构建、Checkstyle、Spotless 检查均已通过。已核对发布 JAR 中的接口实现、注册类、大型工作站上传发现分支、配方兼容校验及版本信息。尚未进行游戏内终端搜索、取放、自动上传、跨网络交换机展示及重载验收。
