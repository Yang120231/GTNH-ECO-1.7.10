# 当前移植状态与基础清理历史

2026-10-25 已进入正式业务导入。最新阶段已有 213 个业务文件、27 个来源测试文件，189 项测试与真实存储客户端网络往返通过，详情见 [存储阶段记录](MILESTONE_STORAGE_20261025_ZH_CN.md)。核心物品按用户排除大型工作站专用升级卡后为 65 项。完整移植与三件套下单存档仍在进行，其他机器尚未全部接入。来源见 [source-lock.json](source-lock.json)；旧 `business-import-state.json` 在本批完整构建/JAR 审计刷新前仅代表此前批次。

导入流程：

    python porting/import_business.py
    python porting/expand_imports.py
    ./gradlew.bat spotlessApply --offline --console=plain
    python porting/import_business.py --refresh-manifest
    ./gradlew.bat test build --offline --console=plain
    python porting/audit_business_import.py

默认编译覆盖全部已迁入源码。导入脚本拒绝覆盖与既有清单哈希不符的未记录工作；--refresh-manifest 只记录审查后的结果，不覆盖源码。来源采用固定 Git 提交，不读取工作区补丁作为默认输入。

专服复现使用独立的 run/porting-acceptance 目录，准备既有同版本 EULA 配置及仅监听本机的 server.properties 后执行：

    ./gradlew.bat -I porting/run-acceptance.init.gradle runServer --mcArgs=nogui --mcJvmArgs=-Dneoecoae.portingAcceptance=true --offline --console=plain

服务器进入世界后依次执行 neoecoae_port_check storage、save-all、stop；重启同一世界再执行 neoecoae_port_check reload、stop。验收命令仅在显式 JVM 属性启用时注册，权限等级 4。它验证真实盘 API、配方数据、同步值和保存重载，不代表完整主机页面或玩家手势已验收。

## 以下为基础清理结束时的历史快照

下文“业务尚未导入”、16 个源码及原始 JAR 数量均属于基础清理时的状态，不再代表当前工程规模。11 个模型文件继续按该快照哈希保留。

清理日期：2026-10-25。当前目录只包含构建环境、Forge 启动入口和现代模型链路，主体业务与新资源尚未导入。

## 保留范围

- `client/render/model` 的 11 个源码文件继续保留，其中 9 个与清理前哈希一致。
- `EcoModelRenderer` 和 `ModelLightSampler` 保持原绘制、剔除和光照算法，将旧业务查询改为 `platform/render/ModelRenderBridge`。
- `NeoECOAE` 与 `platform/forge` 提供 Forge 1.7.10 的 preInit、init、postInit 和客户端代理入口。
- GTNH AE2、MUI2、GTNHLib 的固定依赖、Jabel、Java 8 字节码目标、Gradle wrapper 和构建 CI 继续保留。
- 模型渲染层的 4 项独立测试，以及上个对话生成的调查、接口探针、逐文件清单和来源哈希继续保留。

旧业务目录、旧注册项、旧配置、旧配方、旧 UI、旧网络、旧 Mixin/AT、旧模型纹理和发布说明均已移除。资源从高版本来源一起导入，避免同时维护两套资源清单。

## 渲染接入

高版本业务的客户端适配器实现 `ModelRenderState`，在客户端初始化之后调用 `ModelRenderBridge.bind(renderState)`：

- `isHidden(world, x, y, z)` 提供当前位置是否在视觉上隐藏的只读查询。
- `isModelBlock(block)` 标识采用现代模型渲染的目标平台方块，供原光照采样算法使用。

渲染接口不保存多方块成型规则、控制器等级或业务状态；这些由导入的高版本实现维护。默认状态返回 false，基础工程没有注册业务方块。后续客户端适配器负责按世界生命周期更新状态，并在资源或世界卸载时释放相应数据。

`ModernModelLoader`、`BakedEcoModel` 与 `EcoModelRenderer` 仍位于原路径，后续目标方块或物品渲染入口可直接调用。旧 `ModernBlockRenderHandler`、驱动器专用注册、计算元件专用渲染与旧成型缓存已退出项目。

## 正式导入顺序

1. 按 [调查契约](../docs/porting-1.7.10-20261025/migration-contract.json) 更新并核对来源仓库远端，固定采用的 1.21.1 提交及明确选择的补丁。1.20.1 用作转接方式参考。
2. 迁入高版本业务目录、资源和算法测试清单。保留当前 Forge 入口与模型链路，逐项适配高版本客户端初始化和注册调用。
3. 在 `platform` 下实现 Java 8、Minecraft、注册、NBT、网络、同步、AE2、MUI2、配方和渲染接口。业务决策继续使用高版本实现。
4. 按调查中的 P2 至 P5 完成可运行的纵向路径，并验证资源打包、专用服务端、客户端、保存重载和真实交互。

当前构建成功仅证明基础工程可编译打包。没有通过排除新业务目录取得编译结果；新业务目录尚未迁入。调查中的 69 项旧业务测试是历史基线，不能作为新移植的完成证据。

## 本次收尾验证

2026-10-25 已完成基础工程检查，状态和来源哈希见 [foundation-state.json](foundation-state.json)：

- 干净构建执行 `./gradlew.bat test build --stacktrace --console=plain` 成功，29 项任务执行完成。
- 续接时执行 `./gradlew.bat test build --offline --console=plain` 成功，27 项任务复用已有结果；此前缺少的工具缓存已补齐。
- 保留的模型测试共 4 项，失败、错误和跳过均为 0。
- 11 个模型源码已同时对照 Git 原始内容和调查快照哈希，9 个完全保留，2 个仅改接只读渲染接口。主源码只包含预定的入口、模型与平台接口，旧业务引用为 0。

| 构建产物 | 大小 | 内容 |
| --- | ---: | --- |
| `neoecoae-0.1.0-porting.jar` | 38,925 字节 | 安装版，22 个 Java 8 class |
| `neoecoae-0.1.0-porting-dev.jar` | 38,549 字节 | 开发版，22 个 Java 8 class |
| `neoecoae-0.1.0-porting-sources.jar` | 19,795 字节 | 16 个当前主源码文件 |

开发版与安装版的 classfile major 均为 52，Mod 元信息中的名称、版本与 Minecraft 版本已展开。两个成品包只包含预定类、许可证和 Mod 元信息，没有旧业务类、旧资源或项目 Mixin 入口。源码包保留原资源模板，逐项对照当前源码也已通过。

记录包含各 JAR 的 SHA-256、模型哈希及本机构建日志位置。客户端、专用服务端、真实材质和业务交互的验收留待正式导入；本次未启动游戏。

## 旧资料位置

旧源码已核对远端备份，提交为 `442305f9864623d54eb6bbb57139b2fb72dbbd0d`，当前清理分支为 `codex/1710-port-foundation`。例如可以通过下面命令查阅旧文件：

```powershell
git show 442305f9864623d54eb6bbb57139b2fb72dbbd0d:src/main/java/cn/dancingsnow/neoecoae/tile/TileECOController.java
```

本机开发目录和独立 HTML 移出项目，集中在 `E:/Minecraft Project/_diagnostics/neoecoae-1710-foundation-20261025-01a1712e/`：

- `previous-run/` 保存原客户端存档和服务端测试世界。
- `previous-generated-files/` 保存清理前的构建缓存、IDE 文件、临时解包文件、旧分析和日志。
- `pelican-bicycle.html` 是与 Mod 无关的原独立文件。
- `cleanup-baseline.json` 记录清理前提交、规模和原模型哈希。

这些本机资料不参与新工程编译和打包。后续 Gradle 生成的 `build`、`.gradle` 与 `run` 继续由 `.gitignore` 排除。
