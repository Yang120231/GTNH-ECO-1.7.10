# 原生界面与 Java 25 客户端修复（2026-10-27）

## 网络频道

合成、计算控制器统一使用 `NativeHostSideBar.networkFrequency`。按钮右下角常显实际频道编号；悬浮提示分行显示标题和当前频道，编号直接附加到翻译标签后，不依赖资源包标题是否保留占位符。收到服务端快照之前、频率小于 1 时显示未分配，不误报频道 0。

切换仍走原有的服务端会话动作，频率分配和四频道规则不变。

## 语言检查

新增频道标题、当前频道、未分配三个键，补齐六种语言及 unfinished 对应文件，保持 JSON 与 1.7.10 `.lang` 同步。

扩展 `LanguageResourcesTest` 对 `tr`、绘制标题、统计行及动态拼接键的检查，覆盖工作站、控制器、接口和 AE2 合成状态标签。`NativeNetworkFrequencyTest` 在六种语言中验证频道 1–4 不丢失，并覆盖未分配状态。

当前源码和资源的扩大检查未发现其他缺失的完整语言键；这不表示用户原来安装的 JAR 或外部资源包已被更新。最终安装包需使用本次重新构建的 JAR。

## GuideNH

对照本地拉取的 `GTNewHorizons/GuideNH` 源码，使用其 `GuidesCommon.openGuide` 和 `PageAnchor.page`。五类机器的指南按钮在客户端检测到 GuideNH 后打开 `neoecoae:guidenh` 中的对应页面，未安装时保留文字回退。

GuideNH 为可选依赖，不打进 ECO JAR。编译 API 固定为 1.3.53。构建将现有 `ae2guide` 文档映射到 `assets/neoecoae/guidenh/_en_us/` 与 `_zh_cn/`，附带图片、结构和两个原生首页。每种语言各 11 页。源码继续只维护原有文档，避免两套正文漂移。

`NativeGuideResourcesTest` 检查全部机器入口、首页链接、图像与结构字节一致性，以及没有 GuideNH 时回退类仍能加载。

## runClient25

实际复现了两处启动失败：

1. `NoClassDefFoundError: com/glodblock/github/nei/object/IRecipeExtractor`：原生 GT 运行配置对 GT 关闭了传递依赖，但没有补齐其客户端需要的 AE2FC。按照 GT 5.09.52.398 的依赖声明添加 AE2FluidCraft-Rework 1.5.64-gtnh，以及它声明的 WirelessCraftingTerminal 1.12.12。
2. `TFBlockFluidEnder` 初始化时流体为空：该 GT++ 版本依赖 Thermal Foundation 注册流体，却没有声明两者的先后顺序。所有原生运行任务在启动前合并 Forge 的 `config/injectedDependencies.json`，补入 `miscutils` 在 `ThermalFoundation` 之后加载的规则，保留已有条目。

`verifyGtRuntimeProfile` 增加这两项依赖的版本、实际类及 SHA-256 检查。第二次修复后的真实客户端已完成模组加载，并在日志中记录单人世界启动和玩家登录。

可复现启动验收：

```powershell
.\gradlew.bat verifyGtRuntimeProfile runClient25 -PlaunchAcceptance=true --offline --console=plain
```

此模式使用独立的 `run/launch-acceptance/`，等待真正到达主菜单，检查当前客户端加载的 ECO GUI 语言键和频道文本，保存截图，然后正常退出。日志以 `NEOECOAE_LAUNCH_ACCEPTANCE_OK` 或 `NEOECOAE_LAUNCH_ACCEPTANCE_FAILED` 标记结果。普通 `runClient25` 不启用自动退出。

本次实际验收通过：Java 25.0.3、`zh_CN`、52 个模组完成加载，805 个 ECO GUI 键均可解析，频道 1–4 全部保留编号，截图位于 `run/launch-acceptance/screenshots/2026-10-27_14.08.01.png`。Gradle 返回 `BUILD SUCCESSFUL`，验收客户端正常退出。全套测试 666 项，665 项通过、1 项跳过。

## 验收边界

全套测试、资源检查、编译、安装包核对与真实启动分别记录，未把静态测试当成机器界面的目视验收。Windows Computer Use 服务在本次会话返回 native pipe 不可用，故没有通过该服务操作现有存档。GuideNH 原生页面尚未在安装了 GuideNH 的实际客户端中目视验收。

本次修改的 Spotless、全项目 Checkstyle、全套测试、打包及运行依赖检查均通过。未筛选文件的普通 `build` 仍因另一路正在修改的 `compat/nei/WorkstationRecipeHandler.java` 格式检查失败；该文件的改动保留，没有为本任务改写其格式。
