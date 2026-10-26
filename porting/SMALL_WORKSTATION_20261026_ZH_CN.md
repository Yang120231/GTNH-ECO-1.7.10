# 普通工作站真实加工批次

原有 `integrated_working_station` ID 现由真实 `ECOIntegratedWorkingStationBlockEntity` 承载，保留固定来源的 9 格输入、1 格输出、64,000 mB 双流体仓、16,000,000 AE 私有能源仓、4 个原生 AE2 速度卡位，以及来源的能量预算和速度因子 `2/3/5/10/50`。输入仅允许自动化插入，输出仅允许自动化抽取；流体插入进入输入仓、抽取来自输出仓。自动输出按照启用的相对面使用原生 AE2 库存适配器和 Forge 流体接口。

物品和流体输出先共同模拟，全部可容纳才提交本次结果及对应输入消耗。库存、能源、进度、配方 ID、流体、卡片及自动输出设置写入原生 NBT；重新加载相同配方不会把已付费进度清零。开采时掉落真实物品库存和卡片。UI 由 UI 方向独立对接，现代记忆卡组件导入／导出尚未迁入，因此本来源文件在批次清单中保留 `complete=false`。

`CoreBlocks` 保留来源目录中的历史 `pending_behavior` 声明，但对已由 `WorkstationMachines` 注册的原生块核对精确对象、角色、模型与真实实体工厂，动态减少待行为数，其他任意重复 ID 仍报错。普通工作站令待行为数由 47 降为 46。工作站方块状态定点保留 `module=regular` 的来源分支，避免加载已按用户范围排除的可选联动模型；原 facing／formed／working 规则与 11 个保留模型实现均未修改。

## 验证

定向宿主测试 3 项、主源码及测试 Checkstyle、Spotless 全部通过，构建日志 `build/workstation-small-build.log`。

实际 Java 8 专服在 `run/workstation-acceptance/ECO_Workstation_Real_Processing` 中，将 30 条核心配方逐一交给真实工作站和 AE2 设备调度加工：首条不装卡，100 tick 完成；其余使用四张原生速度卡，各 4 tick 完成。每条核对真实物品／流体输入消耗、物品／流体结果和本机 AE 扣费，包括 64,000 mB 输入、纯流体结果和单次 8,000,000 AE 的高能网络交换模块。过程中通过原生注册实体 ID 和 NBT 还原库存、能源、配方与已付费进度。

首轮虽然完成加工，但完整日志发现私有能源仓错误发出了公共充电事件，AE2 输出 `Attempt to ask the IEnergyGrid to charge a non public energy store`。修正后在同一存档重跑全部 30 条，确认该异常为 0，验收标记成功并正常保存三维度退出；未以首轮成功标记掩盖错误。最终日志 `build/workstation-small-server.log`，提交摘录见 [small-workstation-processing.log](acceptance/small-workstation-processing.log)，原始日志 SHA-256 和逐文件来源见 [gt-03 清单](batches/gt-03-small-workstation-host.json)。开发环境原有的 FML 二进制签名／ANTLR 扫描及 AE2 示例黑白名单日志不属于本批机器加工结果。

```powershell
.\gradlew.bat spotlessApply test --tests '*NativeSmallWorkstationTest' checkstyleMain checkstyleTest --offline --max-workers=2 --console=plain
.\gradlew.bat -I porting/run-workstation-acceptance.init.gradle runServer --mcArgs=nogui --offline --max-workers=2 --console=plain
```

运行前在该独立目录设置 `eula.txt` 和 `server.properties`，监听 `127.0.0.1:25578`。init 脚本显式开启工作站验收和成功后正常停服开关。此存档是普通工作站专用验收，**不是最终三件套＋GT＋原生样板供应器交付存档**；大型工作站、GT 实际加工、完整订单及最终存档仍在后续批次。
