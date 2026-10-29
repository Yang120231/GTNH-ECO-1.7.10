---
navigation:
  title: 使用 StructureLib 搭建
  icon: neoecoae:crafting_system_l9
  parent: neoecoae_intro/index.md
---

# 使用 StructureLib 搭建

ECO 多方块使用 StructureLib 的结构全息投影仪搭建，与 GregTech 多方块使用同一工具。

支持[存储](storage_system.md)、[计算](computation_system.md)、[合成](crafting_system.md)系统与[大型集成工作站](large_integrated_working_station.md)。

## 使用投影仪

先放置控制器并确认朝向。

手持投影仪右键控制器显示全息投影，潜行右键控制器放置方块。

创造模式一次放置整个结构；生存模式每次点击放置一批方块并从物品栏扣除材料，重复点击直到完成。

## 长度与镜像

| 设置 | 作用 |
|------|------|
| 堆叠数量 | 决定结构长度，超出范围时自动限制 |
| `length` 通道 | 覆盖堆叠数量，可设置超过 64 的长度 |
| `mirror` 通道 | 设为 `1` 时搭建镜像结构 |

通道在投影仪自身的设置界面中配置。

## 占用与缺料

已有的正确方块会保留。被其他方块占用的位置会跳过并在聊天栏提示，清理后再次点击即可。材料不足时停止搭建并提示缺少的物品。

## NEI 结构预览

安装 BlockRenderer6343 后，NEI 的多方块预览页会显示各 ECO 结构及所需方块。
