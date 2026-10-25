# Neo ECO AE Extension · GTNH 1.7.10 移植骨架

当前仓库已清理为后续移植的基础工程。业务代码将采用 1.21.1 的实现，参考 1.20.1 的转接方式，接入 Forge 1.7.10、GTNH AE2 和 GTNH MUI2。

保留现代模型加载、烘焙和绘制链路。旧合成、存储、计算、多方块、UI、配方、网络、配置、Mixin、注册项和旧资源已移除，高版本业务尚未导入。

当前源码包含 16 个主 Java 文件：11 个模型文件、Mod 入口、两个 Forge 代理和两个只读渲染接口文件。资源目录包含 Mod 元信息和许可证；模型、纹理与页面 JSON 随高版本源码一起迁入。

```text
src/main/java/cn/dancingsnow/neoecoae/
  NeoECOAE.java              Forge Mod 入口
  client/render/model/      保留的现代模型链路
  platform/forge/            Forge 生命周期与客户端代理
  platform/render/           只读渲染状态接入
src/test/java/               保留的模型渲染层测试
porting/                     当前骨架与后续接入说明
docs/porting-1.7.10-20261025/ 已完成的移植调查和来源证据
```

构建环境沿用 GTNHGradle 2.0.29 和 Gradle 9.3.1：Gradle daemon 使用 JDK 25，Javac 17 配合 Jabel 输出 Java 8 字节码。

| 目标依赖 | 固定版本 |
| --- | --- |
| GTNH AE2 | rv3-beta-990-GTNH |
| GTNH MUI2 | 2.3.49-1.7.10 |
| GTNHLib | 0.11.19 |

```powershell
./gradlew.bat test build --console=plain
```

依赖缓存齐全后可加 `--offline`。2026-10-25 已通过干净构建和后续离线构建，开发版与安装版 JAR 均已核对元信息、Java 8 字节码和保留范围，完整证据见 [基础工程验证记录](porting/foundation-state.json)。

默认构建版本为 `0.1.0-porting`。需要开发客户端时运行 `./gradlew.bat runClient`；客户端目前加载基础 Mod，业务方块、物品和 UI 将在后续导入时注册。

保留的 4 项模型测试已通过，仅覆盖渲染层与 composite 数据传递。旧工程的 69 项业务测试随旧实现移除，后续应迁入高版本算法测试并验证目标平台接口。当前未进行客户端或专用服务端启动验收。

后续接入方式见 [骨架说明](porting/README_ZH_CN.md)，完整调查见 [移植调查](docs/porting-1.7.10-20261025/README_ZH_CN.md)。正式迁入前先更新并核对源仓库远端，再固定本轮使用的源码和补丁。

清理前旧实现的远端提交为 `442305f9864623d54eb6bbb57139b2fb72dbbd0d`，可从 Git 历史查阅。项目采用 [GPL-3.0](LICENSE)。
