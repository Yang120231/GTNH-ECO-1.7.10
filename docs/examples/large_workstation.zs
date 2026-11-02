// 将此文件复制到游戏目录 scripts/large_workstation.zs。
// 以下配方仅作接口示例，请按整合包需求修改材料、能量与时间。
import mods.neoecoae.LargeIntegratedWorkingStation;

LargeIntegratedWorkingStation.addRecipe(
    "my_pack:gold_from_iron",
    <minecraft:gold_ingot> * 2,
    [<minecraft:iron_ingot> * 4],
    42000,
    40
);

LargeIntegratedWorkingStation.addRecipe(
    "my_pack:diamond_with_fluid",
    <minecraft:diamond>,
    [<ore:ingotIron> * 8],
    100000,
    100,
    <liquid:water> * 1000,
    <liquid:lava> * 250
);

LargeIntegratedWorkingStation.addRecipe(
    "my_pack:water_to_lava",
    null,
    [],
    50000,
    100,
    <liquid:water> * 1000,
    <liquid:lava> * 1000
);

// 可选的删除操作，取消注释后生效。
// LargeIntegratedWorkingStation.removeRecipe("my_pack:gold_from_iron");
// LargeIntegratedWorkingStation.remove(<minecraft:diamond>);
// LargeIntegratedWorkingStation.remove(<liquid:lava>);
