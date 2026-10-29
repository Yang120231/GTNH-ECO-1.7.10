# 计算传输总线世界凹槽缺面修复（2026-10-29）

`computation_transmitter` 未成型、单独放置时，凹槽的地板、顶棚和玻璃窗没有绘制，从斜上方可以透过本应被地板封闭的区域看到草地。

## 根因和修复

`EcoModelRenderer.renderWorld` 原先使用 `MinecraftForgeClient.getRenderPass()` 选择模型通道。这是实体/TESR 的 pass；实际区块网格烘焙时日志读到的是 `-1`，经 `ModelRenderLayer.fromRenderPass(-1)` 退回 CUTOUT。方块虽然允许在 pass 0 和 1 绘制，绘制器始终选择基底，TRANSLUCENT 的三张面从未提交。

Forge 1.7.10 在 `WorldRenderer.preRenderBlocks` / `postRenderBlocks` 中通过 `ForgeHooksClient.onPreRenderWorld` / `onPostRenderWorld` 维护独立的区块 pass。修复改用 `ForgeHooksClient.getWorldRenderPass()`，模型 JSON、贴图和原有 cutout/translucent 分工均保持原样。

实际区块烘焙探针记录如下，NORTH、EAST、SOUTH、WEST 均相同：

| 阶段 | pass 读取 | 选择结果 |
| --- | --- | --- |
| 修复前 | entity pass = -1 | CUTOUT，40 张候选面，未提交玻璃面 |
| 修复后 | world pass = 0 | CUTOUT，40 张候选面 |
| 修复后 | world pass = 1 | TRANSLUCENT，3 张面（UP 地板、DOWN 顶棚、朝外窗户） |

40 是剔除前的数量；放在草块上的外部底面会正常剔除。`body` 的 north 面在修复前也确实提交了，NORTH 朝向的平面为 z=0.375，EAST 朝向为 x=0.625。该面没有独立的运行时跳过问题；从斜上方看到草地的缺口源于缺少凹槽地板。

最小开发客户端未安装 ArchaicFix/Hodgepodge 即可复现，因此它们不是触发本次缺面的必要条件。本次没有在用户完整整合包中做兼容性验收。

## 真实客户端验收

新增 `src/acceptance` 下的 `NativeTransmitterRenderAcceptance`，由 `AcceptanceHooks` 按系统属性启用，不进入发布 JAR。

验收先在真实游戏环境中执行通道回归：强制 entity pass 为 -1，分别设置 world pass 0 和 1，直接调用生产 `EcoModelRenderer.renderWorld` 并检查实际 Tessellator 队列。模拟后壁和三张玻璃面的几何在四个朝向中分别提交 4 / 12 个顶点，共 8 项检查；结束时恢复两个 pass。此检查需要已初始化的 Forge 客户端，因此放在客户端验收中。

随后创建独立平坦世界，在草块上放置未成型总线，固定时间、镜头和视野，使用 `ScreenShotHelper` 截取六张世界画面：NORTH 斜上方、平视、斜下方，以及 EAST、SOUTH、WEST 斜上方。每次截图前核对客户端方块实体的未成型状态和实际朝向。截图用于人工视觉验收，日志中的 CAPTURED 表示采集完成。

复跑命令：

```powershell
.\gradlew.bat --offline -PgtRuntime=false -PtransmitterRenderAcceptance=true -PtransmitterRenderLabel=after runClient
```

固定运行目录为 `run/transmitter-render-acceptance/`；截图为 `screenshots/transmitter-after-0.png` 至 `transmitter-after-5.png`。可去掉 `-PgtRuntime=false` 使用完整的固定 GT 开发依赖。

本次证据：

- 修复前截图：`run/transmitter-render-before/screenshots/transmitter-before-0.png` 至 `-2.png`（后三张旧采集未同步方块实体朝向，不用于朝向验收）。
- 带临时逐面探针的四朝向修复后截图：`run/transmitter-render-world-pass/screenshots/`。
- 移除临时探针后的最终六视角截图：`run/transmitter-render-acceptance/screenshots/`。
- 临时逐面日志：`.gradle/transmitter-render-before.log`、`.gradle/transmitter-render-world-pass.log`。
- 最终客户端与相关单元测试日志：`.gradle/transmitter-render-final.log`。
- 凹槽局部对照：`build/transmitter-render-validation/comparison.png`。

最终截图确认凹槽地板、顶棚、玻璃窗和后壁显示正常，四个水平朝向无草地透穿。相关 10 项单元测试、客户端内 8 项实际顶点队列检查及六视角采集通过。临时逐面日志代码已移除，未替换用户实例中的 JAR。
