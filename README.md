# Neo ECO AE Extension · GTNH 1.7.10 正式移植

当前已进入业务迁入阶段。业务输入采用 1.21.1，参考 1.20.1 的平台转接组织方式，接入 Forge 1.7.10、GTNH AE2 和 GTNH MUI2。完整移植仍在进行。

本轮固定业务输入为 f6003889a1352ea0d7e2e23a3868eb69943789ca，未选择来源工作区补丁。已迁入 158 个业务文件、15 个高版本测试文件和 2,066 个有效来源资源。标准物品/流体盘、精确世界存储、主要规划算法及平台基础已落地。逐文件哈希与待迁路径见 [source-lock.json](porting/source-lock.json)。

保留的 11 个现代模型源码相对基础清理快照哈希一致。主源码由保留基础、新迁入业务及 platform 转接组成；ECO 主机/驱动器、多方块、CPU 接管、计算、工作站和完整 MUI2 页面仍待继续迁入，完整状态见 [正式移植进度](porting/PROGRESS_ZH_CN.md)。

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
porting/                     来源锁、进度与运行证据
docs/porting-1.7.10-20261025/ 已完成的移植调查和来源证据
```

构建环境沿用 GTNHGradle 2.0.29 和 Gradle 9.3.1：Gradle daemon 使用 JDK 25，Javac 17 配合 Jabel 输出 Java 8 字节码。

| 目标依赖 | 固定版本 |
| --- | --- |
| GTNH AE2 | rv3-beta-990-GTNH |
| GTNH MUI2 | 2.3.49-1.7.10 |
| GTNHLib | 直接声明 0.11.19；本轮传递依赖实际解析 0.11.37 |

```powershell
./gradlew.bat test build --console=plain
```

依赖缓存齐全后可加 `--offline`。本批完整构建、118 项测试、Checkstyle、Spotless 和来源编译覆盖检查均通过；开发版、安装版与源码包核对见 [业务验证记录](porting/business-import-state.json)。默认 JavaCompile 覆盖全部已迁入业务，不通过排除目录获得成功。

默认版本为 `0.1.0-porting`，JAR、Mod 元信息与生成的 Tags.VERSION 一致。6 种标准物品/流体盘及其组件/外壳已注册，6 项组装及拆解定义已接入。其他配方 JSON 的打包不表示它们已在目标游戏注册。

保留的 4 项模型测试包含在本批 118 项测试中。Java 1.8.0_492 独立专服已验证真实双通道存取、封存、物品 NBT、组装配方、MUI2 同步值的服务端加载，以及 10^40 + 48 的世界保存和跨进程重载。完整客户端画面/手势、ECO 主机、原生 CPU 任务与普通安装尚未验收。

续接顺序和边界见 [正式移植进度](porting/PROGRESS_ZH_CN.md)，导入与专服复现命令见 [接入说明](porting/README_ZH_CN.md)。[基础工程验证记录](porting/foundation-state.json) 与 [移植调查](docs/porting-1.7.10-20261025/README_ZH_CN.md) 保留历史证据。

清理前旧实现的远端提交为 `442305f9864623d54eb6bbb57139b2fb72dbbd0d`，可从 Git 历史查阅。项目采用 [GPL-3.0](LICENSE)。
