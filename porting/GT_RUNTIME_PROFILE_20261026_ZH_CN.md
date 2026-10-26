# 原生 GT 运行配置与桥接批次

本批补齐可选原生 GT 开发运行配置、真实基础机器状态读取和缺席 GT 时的类加载隔离。没有启动 GT 客户端、代替 GT 加工、创建最终下单存档，不能作为完整 GT 联动验收。

## 固定输入与实际解析结果

GT 源码最新远端已经核对为 `7dde8831cd84c54fbd69a9705dc2f9d41bc3fe11`（`5.09.54.248`）。该版本依赖的 AE2／MUI2 已超出现有固定目标，当前桥接明确采用可验证的 `5.09.52.398`，没有冒报使用最新 GT 运行包。其 tag／采用提交为 `a40199ca4267862155728c85c4d0db38b84503b0`；读取对应 tag 的 `IGregTechTileEntity` 与 `MTEBasicMachine` 原始 Git blob，对照实际 dev JAR 的 `javap` 方法、字段验证。

| 原生组件 | 实际版本／来源 |
| --- | --- |
| GT5-Unofficial | `5.09.52.398` dev JAR |
| AE2 | `rv3-beta-990-GTNH`，维持既定版本 |
| MUI2 | `2.3.49-1.7.10`，维持既定版本 |
| GTNHLib | 实际解析 `0.11.37` |
| StructureLib | `1.4.39` |
| ModularUI 1 | `1.3.2` |
| NEI | `2.8.105-GTNH` |
| Waila | `1.19.22` |
| IC2 | `curse.maven:ic2-242638:2353971` 经 RFG 反混淆，包内版本为 `2.2.827-experimental` |

IC2 最初声明沿用 GT 的 `2.2.828-experimental:dev`，但 GTNH 构建约定实际会替换到上表中的 CurseForge 文件。检查实际运行 JAR 后改为直接锁定真实来源；不能把声明的 `828` 当成已加载的版本。GT 此 tag 的 FML 依赖仅要求存在 IC2，没有 `828` 最低版本约束。其 API／实机兼容仍须在后续真实启动、加工时验收。

## 使用方式

默认运行不增加 GT。显式启用时执行：

```powershell
.\gradlew.bat verifyGtRuntimeProfile -PgtRuntime=true --offline --max-workers=2 --console=plain
.\gradlew.bat -I porting/run-gt-acceptance.init.gradle runClient -PgtRuntime=true --offline --max-workers=2 --console=plain
```

初次安装依赖可去掉 `--offline`。专用 init 脚本分别使用 `run/gt-acceptance` 和 `run/gt-server-acceptance`，便于后续统一建立并交付实际功能存档。

`verifyGtRuntimeProfile` 读取真实 `runClient` 的 `JavaExec.classpath`，逐一检查六种原生 Mod 的代表类各有一份，计算实际 JAR 的 SHA-256，并检查解析模块版本及 IC2 包内版本。完整生成报告为 `build/reports/native-gt-runtime.json`，可提交证据摘录为 [native-gt-runtime-profile.log](acceptance/native-gt-runtime-profile.log)。来源、平台适配文件及实际 JAR 指纹记录于 [gt-02 批次](batches/gt-02-native-runtime.json)。

## 原生桥接与验证边界

`NativeGregTech` 不在公共签名中引用 GT 类型，仅在 FML 确认 `gregtech` 存在后进入独立桥接类。`inspect(TileEntity)` 对真实 `IGregTechTileEntity`／`MTEBasicMachine` 读取 meta ID、机器类、活动状态、已存 EU、EU 容量、进度、总时长和 EU/t；其他机器返回无可用快照，不填造替代状态。机器配方、耗能、加工及产出始终由 GT 原生机器负责。

已通过：实际 GT API 编译、运行配置解析及实际 JAR 指纹检查、默认无 GT 的隔离类加载测试 1 项、主源码及测试 Checkstyle、Spotless。类加载测试显式禁止所有 `gregtech.*` 及桥接实现类，仍可加载公共入口与全部公共签名。

下一批仍需真实普通／大型工作站宿主、配方消费及 UI 对接；最终三件套＋GT＋原生样板供应器要在完整订单链集成后验证真实下单、GT 加工、产出归属、取消／断网和保存重载。
