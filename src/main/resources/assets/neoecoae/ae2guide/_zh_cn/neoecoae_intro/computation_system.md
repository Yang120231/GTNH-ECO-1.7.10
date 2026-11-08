---
navigation:
  position: 20
  title: 计算系统
  icon: neoecoae:computation_system_l9
  parent: neoecoae:neoecoae_intro/index.md
item_ids:
  - neoecoae:computation_system_l4
  - neoecoae:computation_system_l6
  - neoecoae:computation_system_l9
  - neoecoae:computation_drive
  - neoecoae:computation_transmitter
  - neoecoae:computation_threading_core_l4
  - neoecoae:computation_threading_core_l6
  - neoecoae:computation_threading_core_l9
  - neoecoae:computation_parallel_core_l4
  - neoecoae:computation_parallel_core_l6
  - neoecoae:computation_parallel_core_l9
  - neoecoae:computation_cooling_controller_l4
  - neoecoae:computation_cooling_controller_l6
  - neoecoae:computation_cooling_controller_l9
  - neoecoae:computation_interface
  - neoecoae:computation_casing
  - neoecoae:eco_computation_cell_l4
  - neoecoae:eco_computation_cell_l6
  - neoecoae:eco_computation_cell_l9
  - neoecoae:computation_network_switch
  - neoecoae:computation_high_energy_network_switch
---

# 计算系统

## 计算主机

<BlockImage id="neoecoae:computation_system_l9" meta="2" scale="3" />

## 计算系统结构

<GameScene width="300" height="220" zoom="3" interactive={true}>
  <ImportStructureLib controller="neoecoae:computation_system_l9" tier="1" facing="north" />
</GameScene>

## 计算通讯端口

<BlockImage id="neoecoae:computation_interface" meta="2" scale="3" />

计算系统充当 ME 网络中的合成 CPU，负责安排合成任务。

## 三种能力

- 线程：决定可以同时运行多少个任务。
- 并行加速：提高任务向机器发配工作的能力。
- 任务存储：决定能承载多大的合成计划。

增加其中一项，不会自动补足另外两项。

## 基本使用

主机分为 C4、C6、C9 三档。界面显示线程、存储和并行情况，扩建时按实际缺少的能力补充部件。

## 请求来源

CPU 可以设置为接受全部请求、仅玩家请求或仅机器请求。无法选择或启动任务时，检查请求来源设置及空闲资源。

## 多机组网

C9 可以安装普通或高能网络交换模块。成员需要位于同一维度、同一在线 ME 网络，并选择相同的逻辑频道。每组最多八台，至少两台成员才能获得组网加成。

组网可汇总并增强线程、并行和任务存储，同时提高耗电。先保证单台运行正常，再按信息提示补齐组网条件。
