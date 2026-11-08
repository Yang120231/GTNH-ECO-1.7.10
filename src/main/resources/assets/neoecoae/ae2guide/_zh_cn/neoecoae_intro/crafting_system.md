---
navigation:
  position: 80
  title: 合成系统
  icon: neoecoae:crafting_system_l9
  parent: neoecoae_intro/index.md
item_ids:
  - neoecoae:crafting_system_l4
  - neoecoae:crafting_system_l6
  - neoecoae:crafting_system_l9
  - neoecoae:crafting_worker
  - neoecoae:crafting_pattern_bus
  - neoecoae:crafting_parallel_core_l4
  - neoecoae:crafting_parallel_core_l6
  - neoecoae:crafting_parallel_core_l9
  - neoecoae:crafting_interface
  - neoecoae:crafting_casing
  - neoecoae:crafting_vent
  - neoecoae:input_hatch
  - neoecoae:output_hatch
  - neoecoae:crafting_network_switch
  - neoecoae:crafting_high_energy_network_switch
---

# 合成系统

## 合成主机

<GameScene width="300" height="144" zoom="4" interactive={true}>
  <Block id="neoecoae:crafting_system_l9" meta="2" />
</GameScene>

## 合成系统结构

<GameScene width="300" height="220" zoom="3" interactive={true}>
  <ImportStructureLib controller="neoecoae:crafting_system_l9" tier="1" facing="north" />
</GameScene>

## 合成通讯端口

<GameScene width="300" height="144" zoom="4" interactive={true}>
  <Block id="neoecoae:crafting_interface" meta="2" />
</GameScene>

合成系统读取合成样板，使用工作核心批量完成制造。合成 CPU 负责派单，合成系统负责执行。

## 工作核心与并行核心

工作核心提供实际执行通道。增加工作核心，可以同时承接更多制造工作。

并行核心提供并行能力，并参与决定可用超频。结构长度、核心数量、供电和派单能力都会影响实际速度。

主机分为 F4、F6、F9 三档。批量容量、生效超频和实际占用，以当前机器界面为准。

## 超频与冷却

开启超频可以提高制造能力，也会提高供电要求。开启主动冷却并提供有效冷却液后，按冷却条件运行。

水支持较低超频，极寒之凛冰溶液支持最高九级超频。冷却液不足、等级不足或冷却产物无法排出时，应检查生效超频和停机提示。

冷却值缓冲表示已经转换好的冷却能力；输入仓中剩余多少液体，需要打开输入仓查看。

## 多机组网

F9 可以通过网络交换模块组网，共享样板并分配工作。成员需要处于同一维度、同一在线 ME 网络和同一逻辑频道，每组最多八台。

满足完整的高能满长组网条件后，可进入虚拟合成模式。具体缺少哪些条件，查看控制器的信息提示。
