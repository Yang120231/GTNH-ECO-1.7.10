# 2026-10-26 核心内容注册与创造栏排序

按用户“先给剩余的物品注册”的要求，补齐固定来源 `NEBlocks` 中其余 47 个核心方块及对应物品。此前排除的全部可选 Mod 联动、大型工作站七张专用升级卡继续排除。原有三个隐藏旧散装盘仍保持来源的隐藏规则。

本次只推进注册、创造栏、名称、稀有度、默认模型和原生放置。新注册机器没有伪造 CPU、工作线程、集群、库存或加工逻辑；合成、计算和工作站的完整功能仍按原交接记录继续迁入。

## 注册范围

| 本次补齐 | 数量 |
| --- | ---: |
| 合成主机、并行核心、工作器、监控核心、样板总线、仓口、外壳、散热及网络交换模块 | 16 |
| 计算主机、线程／并行核心、冷却控制器、驱动器、接口、发射器、外壳及网络交换模块 | 18 |
| 小型工作站及大型工作站外壳、输入仓、输出仓、接口 | 5 |
| 四种母岩、三种晶芽及晶簇 | 8 |

已有 13 个材料方块和 10 个存储机器方块继续使用原实现，共计 **70 个核心方块**。65 个独立核心物品中有三个隐藏旧盘，因此实际 ECO 创造栏共有 **132 个可见条目**。

按用户追加要求，创造栏固定按 **存储 → 合成 → 计算 → 工作站 → 材料 → 工具** 排列。同系列机器按 L4／L6／L9，盘和组件按 16M／64M／256M；材料按同种矿物及加工阶段相邻，工具按材质分组。排序只作用于原生创造栏收集到的显示列表，不修改注册顺序、数字 ID 或隐藏规则。新增未知条目保留在末尾，不因排序被过滤。

`core-registration.json` 由 `generate_block_catalog.py` 从固定来源的 70 个 `BlockEntry` 提取，逐项记录字段、行号、基础属性、默认方块状态、模型和资源哈希。重复注册、缺少既有实现或缺少方块物品会直接报错；后续功能迁入时应将相应条目从 `pending_behavior` 转为真实实现，不能重复注册同一 ID。

晶芽使用原生交叉渲染和来源纹理；其他新增方块使用原有模型加载、烘焙与绘制链路的未成型模型。工作站引用的三张 AE2 通用贴图来自既有固定 AE2 提交，按 1.7.10 图集路径提供别名，原图哈希和既有 LGPL 许可证记录于 `registration-textures.json`。11 个保留模型源码未修改。另修正创造栏标题翻译键及方块物品名称被二次翻译后追加 `name` 的问题。

## 验证与证据

独立 `run/client-acceptance` 客户端在新建的 `ECO_Content_Acceptance_1792980034558` 存档中验证：

- 真实 FML 注册包含 70 个核心方块，每个都有对应物品及创造栏入口。
- ECO 创造栏实际列出 132 个无重复条目，70 个方块均在其中；名称翻译及方块实际显示名称匹配。
- 实际列表验证六组顺序、等级／容量递增及重复读取的一致性，日志标记为 `NEOECOAE_CONTENT_ORDER_OK`。
- 新增 47 个方块均实际放置，客户端加载模型；保存展示场景和四个滚动位置的创造栏截图。
- 10:00:49 出现 `NEOECOAE_CONTENT_ACCEPTANCE_OK`，之后正常保存关闭。时间沿用 2026-10-26 的本机日志。

完整日志：`build/client-content-registration.log`；可提交摘录：[注册验收日志](acceptance/content-registration.log)。截图位于 `run/client-acceptance/screenshots/2026-10-26_10.00.45.png` 至 `2026-10-26_10.00.49.png`，已查看展示场景、排序后的第一页及中段机器页面。

完整构建使用 `test build -PplannerChallenge=true --offline --console=plain`。523 项测试、Checkstyle、Spotless 和迁入源码编译覆盖检查通过；构建日志为 `build/content-registration-build.log`。安装包、开发包和源码包按最新文件重新核对，结果见 `business-import-state.json`。当前原生配方运行记录为 164 条定义、115 条已安装、49 条待对应配方适配，不能以注册机器或生成配方物品替代运行功能验收。

复现命令：

```powershell
python porting/generate_block_catalog.py
.\gradlew.bat test build -PplannerChallenge=true --offline --console=plain *> build/content-registration-build.log
.\gradlew.bat -I porting/run-client-acceptance.init.gradle runClient --mcJvmArgs=-Dneoecoae.contentAcceptance=true --offline --console=plain *> build/client-content-registration.log
python porting/audit_business_import.py --build-log build/content-registration-build.log --registration-log build/client-content-registration.log
```

注册补齐时已 fetch 核对目标仓库、1.21.1 来源及现代 AE2 来源；当时 1.21.1 远端为 `b9914229cffc53f20cf5c0cb7837bcd70b17207c`，其 `NEBlocks` 与固定移植输入 `f6003889a1352ea0d7e2e23a3868eb69943789ca` 相同。排序收尾时再次 fetch 因 `127.0.0.1` 代理未连通失败，本次排序没有读取新的来源业务或改变固定输入，也没有选择来源补丁。规划生命周期和完整订单执行的续接点仍见 [原暂停交接记录](CHECKPOINT_20261025_ZH_CN.md)。
