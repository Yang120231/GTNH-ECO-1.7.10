---
navigation:
  position: 50
  title: 存储系统
  icon: neoecoae:storage_system_l9
  parent: neoecoae_intro/index.md
item_ids:
  - neoecoae:storage_system_l4
  - neoecoae:storage_system_l6
  - neoecoae:storage_system_l9
  - neoecoae:eco_drive
  - neoecoae:storage_interface
  - neoecoae:storage_casing
  - neoecoae:storage_vent
  - neoecoae:energy_cell_l4
  - neoecoae:energy_cell_l6
  - neoecoae:energy_cell_l9
  - neoecoae:eco_item_storage_cell_16m
  - neoecoae:eco_item_storage_cell_64m
  - neoecoae:eco_item_storage_cell_256m
  - neoecoae:eco_fluid_storage_cell_16m
  - neoecoae:eco_fluid_storage_cell_64m
  - neoecoae:eco_fluid_storage_cell_256m
---

# 存储系统

## 存储主机

<GameScene width="300" height="144" zoom="4" interactive={true}>
  <Block id="neoecoae:storage_system_l9" meta="2" />
</GameScene>

## 存储系统结构

<GameScene width="300" height="220" zoom="3" interactive={true}>
  <ImportStructureLib controller="neoecoae:storage_system_l9" tier="1" facing="north" />
</GameScene>

## 存储通讯端口

<GameScene width="300" height="144" zoom="4" interactive={true}>
  <Block id="neoecoae:storage_interface" meta="2" />
</GameScene>

存储系统把 ECO 存储矩阵中的物品和流体提供给 ME 网络。

主机分为 L4、L6、L9 三档。需要更多存储时，可以增加驱动器并装入更多矩阵。

## 通讯接口模式

- 存储模式：供网络正常访问本机矩阵。
- 输入模式：把网络库存转入本机。
- 输出模式：把本机库存转移到网络中的其他存储。

日常使用选择存储模式。搬运库存时，先确认接收侧有足够空间，再切换输入或输出模式。

过滤列表为空时允许全部资源；设置样本后，只搬运符合过滤条件的资源。

## 无限存储

L9 主机支持无限存储模式。需要在同一台机器中装入至少十二个符合迁移条件的 L9 矩阵，并放入六十四个无限存储组件。

按主机界面的提示完成迁移。进入后保留原成员矩阵；缺失成员矩阵会影响库存访问。

退出前，先把库存完整恢复到容量和类型均合适的普通矩阵，再取出组件。
