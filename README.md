# Neo ECO AE Extension · GTNH 1.7.10 正式移植

**已按用户要求暂停（2026-10-26）。** 当前主分支、独立工作树未提交代码、645 项测试通过但 Checkstyle 未过的状态，以及恢复顺序统一见最新停止交接记录（仅保存在本地 porting/CHECKPOINT_20261026_PAUSED_ZH_CN.md）。下文“继续移植”是暂停前的实施背景，不是当前执行指令。

当前按 UI、规划与执行、配方、GT 联动四个方向继续移植，完成一个可验证批次即本地提交，见四方向实施记录（仅保存在本地 porting/PARALLEL_IMPLEMENTATION_20261026_ZH_CN.md）。核心业务输入采用 1.21.1，GT 联动配方对照 1.20.1 主线，接入 Forge 1.7.10、GTNH AE2 和 GTNH MUI2。按用户最新要求，以编译通过验收，不要求真实鼠标操作或最终游戏存档。

本轮固定业务输入为 f6003889a1352ea0d7e2e23a3868eb69943789ca，未选择来源工作区补丁；GT 配方独立对照 1.20.1 提交 `024b73da2eed6113934d39f0a684e3dc92177c20`。标准盘、精确世界存储、存储主机与驱动器、共享多方块、规划与 CPU／工作线程、建造窗口及普通工作站加工和页面已迁入。逐文件数量、哈希与待迁路径见 source-lock.json（仅保存在本地 porting/source-lock.json），文件数量不换算为行为完成率。

保留的 11 个现代模型源码相对基础清理快照哈希一致。当前已补齐 70 个核心方块及 65 个独立物品注册；机器行为按批次接替初始注册壳，未接通部分继续明确记录。其它可选 Mod 联动与大型工作站七张专用模块卡仍不纳入范围。

```text
src/main/java/cn/dancingsnow/neoecoae/
  NeoECOAE.java              Forge Mod 入口
  api/                      迁入的业务与扩展契约
  crafting/                 精确数量、规划与执行元数据
  impl/storage/             高版本存储与迁移业务
  client/render/model/      保留的现代模型链路
  platform/forge/            Forge 生命周期与客户端代理
  platform/render/           只读渲染状态接入
src/test/java/               模型、高版本算法与平台契约测试
porting/                     本地来源锁、进度与运行证据（不纳入 Git）
docs/porting-1.7.10-20261025/ 已完成的移植调查和来源证据
```

构建环境沿用 GTNHGradle 2.0.29 和 Gradle 9.3.1：Gradle daemon 使用 JDK 25，Javac 17 配合 Jabel 输出 Java 8 字节码。

| 目标依赖 | 固定版本 |
| --- | --- |
| GTNH AE2 | rv3-beta-990-GTNH |
| GTNH MUI2 | 2.3.49-1.7.10 |
| GTNHLib | 直接声明 0.11.19；本轮传递依赖实际解析 0.11.37 |

```powershell
./gradlew.bat test build -PplannerChallenge=true --console=plain
```

依赖缓存齐全后可加 `--offline`。构建和测试不依赖本地 `porting/` 目录；来源覆盖审计与导入输出清理已从默认构建中移除。完整构建、测试、Checkstyle、Spotless 与开发版／安装版／源码包核对结果见 业务验证记录（仅保存在本地 porting/business-import-state.json）。默认 JavaCompile 覆盖全部已迁入业务，不通过排除目录获得成功。

开发环境默认加载固定版本的 GT5 及其运行依赖；运行 `./gradlew.bat verifyGtRuntimeProfile` 可检查实际开发客户端依赖。需要不加载 GT 的开发环境时，可使用 `-PgtRuntime=false`。ECO 在检测到 GregTech 时自动启用 GT 配方覆盖并注册原生机器配方；未安装 GregTech 时保留独立配方，GT 仍是可选依赖。

默认版本为 `7.3.1`，JAR、Mod 元信息与生成的 Tags.VERSION 一致。核心配方目录包含工作台、组装／拆解、炉、压印、工作站、冷却与世界转化。GT 配方使用独立来源目录及原生条件注册，配方 JSON 的打包不表示运行验收。

保留的 4 项模型测试和 26 项来源复杂订单挑战包含在完整构建中。既有专服与客户端日志保留为历史证据；当前交付标准是实现后的编译与构建检查，不把旧日志当作本次构建的游戏运行结果。审计脚本默认检查当前源码、字节码、资源和 JAR，`--include-historical-runtime` 仅用于显式重查旧日志。

续接顺序和边界见 正式移植进度（仅保存在本地 porting/PROGRESS_ZH_CN.md），导入与专服复现命令见 接入说明（仅保存在本地 porting/README_ZH_CN.md）。基础工程验证记录（仅保存在本地 porting/foundation-state.json） 与 [移植调查](docs/porting-1.7.10-20261025/README_ZH_CN.md) 保留历史证据。

清理前旧实现的远端提交为 `442305f9864623d54eb6bbb57139b2fb72dbbd0d`，可从 Git 历史查阅。项目采用 [GPL-3.0](LICENSE)。
