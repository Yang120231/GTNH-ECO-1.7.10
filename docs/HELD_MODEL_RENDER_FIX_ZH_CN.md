# ECO 手持模型穿面修复（2026-10-27）

本次修改集中在 `EcoModelRenderer`，覆盖机器方块、普通模型方块和计算元件共用的物品绘制入口。已在实际 1.7.10 客户端复现并修复手持方块像空壳、背面覆盖正面的现象。

`NativeMachineBlock` 和 `NativeSimpleModelBlock` 都通过 `getRenderBlockPass() == 1` 声明透明世界渲染支持。原版 `ItemRenderer` 因此在绘制手持方块时关闭深度写入。原有 ECO 绘制器同时关闭背面剔除，将全部面一次提交，又未恢复实体部分的深度写入，导致后提交的背面覆盖靠近相机的表面。

现在实体／镂空部分先启用深度写入和 Alpha Test 绘制；透明部分随后根据实际模型视图矩阵排序，从远到近混合，并关闭深度写入。绘制结束时通过属性栈恢复深度、混合、剔除、Alpha Test 和当前颜色状态；矩阵通过 `finally` 恢复。

实际 GPU 验证使用带深度附件的离屏 Framebuffer，在调用前主动关闭深度写入，以复现原版手持入口的条件。检查结果如下：

| 检查 | 修复前 | 修复后 |
| --- | --- | --- |
| 红色前表面遮挡绿色后表面 | 错误：RGB 0,255,0 | 正确：RGB 255,0,0 |
| 蓝色半透明前表面＋红色实体＋绿色透明后表面 | 错误：RGB 127,128,0 | 正确：RGB 127,0,128 |
| 两个半透明表面按视角排序 | 失败 | 通过 |
| 视角旋转 180 度后的透明排序 | 通过 | 通过 |
| 调用前关闭的深度写入得到恢复 | 通过 | 通过 |
| 调用前剔除与混合状态得到恢复 | 失败 | 通过 |

修复后客户端记录了 `occlusion=true`、`layers=true`、`depth_restored=true`、`glass_sorted=true`、`rotated_sorted=true`、`state_restored=true`，并正常退出，Gradle 返回 `BUILD SUCCESSFUL`。

实际截图覆盖存储外壳、合成样板总线、ECO 驱动器、L4 冷却控制器、集成工作站、L4 计算元件、计算外壳、大型工作站外壳和计算传输总线共 9 类物品，以及第三人称和物品栏。截图分别保存在 `run/held-model-before/screenshots/`、`run/held-model-after/screenshots/`；其中 5 类的局部对照见 `build/held-model-validation/held-model-comparison.png`。

本地验证入口为 `build/held-model-probe.init.gradle` 和 `build/held-model-probe-source/`。临时探针及其覆盖类没有放入 `src/main`，不参与发布 JAR 打包。验证使用 Java 25.0.3、LWJGL 3.4.2 和固定 GT 开发依赖，客户端加载了 53 个模组，其中包含 1 个临时验证模组。

当前工作区已有的 `CraftingCpuPublicationMixin` 未提交修改在固定 AE2 rv3-beta-990 开发环境中触发 `planWithLiteMode` 注入描述符错误。为完成渲染对照，两次验证均仅在临时探针类路径中覆盖为 HEAD 的该 Mixin；工作区中的合成 CPU 修改保持原样。这些截图和 GPU 检查验证了模型修复，不表示当前所有未提交改动的发布 JAR 已完成整体验收。第一次修复前采集结束后，临时探针退出流程出现空玩家异常；探针退出流程修正后，修复后的完整客户端验证正常结束。

完整构建命令为 `./gradlew.bat test build -PplannerChallenge=true --offline --console=plain`。最终 699 项测试中，697 项通过、2 项跳过；发布 JAR 的重混淆库存接口验证、Spotless、Checkstyle、组装与构建均通过。首次执行时，既有 `ECOProgressivePlanningTest.realSessionPublishesACompleteRouteThenImprovesItWithAnIndependentSession` 的规划改进路线断言失败；保留首次日志后，未修改规划器或测试代码，复跑同一命令得到上述通过结果。日志为 `build/held-model-build-first.log`、`build/held-model-build.log`。

安装包输出为 `build/libs/neoecoae-0.1.0-porting.jar`。替换旧包并重启客户端后，手持绘制修复才会在该客户端中生效。
