---
navigation:
  title: 大型集成工作站
  icon: neoecoae:integrated_working_station
  position: 30
  parent: neoecoae_intro/index.md
item_ids:
  - neoecoae:large_integrated_working_station_casing
  - neoecoae:large_integrated_working_station_input_hatch
  - neoecoae:large_integrated_working_station_output_hatch
  - neoecoae:large_integrated_working_station_interface
---

# 大型集成工作站

<GameScene width="300" height="220" zoom="3" interactive={true}>
  <ImportStructureLib controller="neoecoae:integrated_working_station" tier="1" facing="north" />
</GameScene>

大型集成工作站批量执行受支持的工作站加工配方。支持的配方可在物品管理器中查看，整合包也可通过脚本调整。

## 基本使用

通讯接口默认提供三十六个样板槽，最多安装四张容量升级卡，扩展到七十二槽。

## 并行与温度

默认启用温度机制。开启超频后，实际加工会升温；右键超频按钮可启动或停止电预热。

| 温度条件 | 单批并行上限 |
| --- | --- |
| 常规运行 | 256 |
| 达到20% | 16,384 |
| 达到50% | 65,536 |
| 达到80% | 262,144 |

高档并行需要开启超频。并行提高后，仍需按实际加工数量支付能量和原料。

温度下降导致原档位不再满足时，已有任务可分段继续；恢复条件后可重新使用原档位。

## 冷却与过热

主动冷却需要持续提供冷却液，并保持副产物输出畅通。冷却不保证温度只降不升，应观察实际温度变化。

达到百分之百会过热锁定。开启主动冷却，连续提供极寒之凛冰溶液十五秒，完成恢复；共需三万升。中断会使恢复重新计时。

## 冷却液模式

服务器关闭温度机制后，工作站按冷却液条件提供高并行，无需预热。需要同时开启超频和主动冷却，并持续供应符合要求的液体。
