package cn.dancingsnow.neoecoae.all;

import net.minecraft.item.Item;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraftforge.common.util.EnumHelper;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.item.ItemDebugStick;
import cpw.mods.fml.common.registry.GameRegistry;

public final class NEItems {

    public static final Item.ToolMaterial aluminumToolMaterial = EnumHelper
        .addToolMaterial("NEO_ECO_AE_ALUMINUM", 2, 220, 5.5F, 1.5F, 16);
    public static final Item.ToolMaterial tungstenToolMaterial = EnumHelper
        .addToolMaterial("NEO_ECO_AE_TUNGSTEN", 3, 1400, 7.5F, 2.5F, 12);
    public static final Item.ToolMaterial aluminumAlloyToolMaterial = EnumHelper
        .addToolMaterial("NEO_ECO_AE_ALUMINUM_ALLOY", 3, 900, 7.0F, 2.5F, 18);
    public static final Item.ToolMaterial blackTungstenAlloyToolMaterial = EnumHelper
        .addToolMaterial("NEO_ECO_AE_BLACK_TUNGSTEN_ALLOY", 4, 2200, 9.0F, 3.5F, 14);

    public static final Item aluminumIngot = simpleItem("aluminum_ingot");
    public static final Item aluminumDust = simpleItem("aluminum_dust");
    public static final Item rawAluminumOre = simpleItem("raw_aluminum_ore");
    public static final Item tungstenIngot = simpleItem("tungsten_ingot");
    public static final Item tungstenDust = simpleItem("tungsten_dust");
    public static final Item rawTungstenOre = simpleItem("raw_tungsten_ore");
    public static final Item aluminumAlloyIngot = simpleItem("aluminum_alloy_ingot");
    public static final Item aluminumAlloyDust = simpleItem("aluminum_alloy_dust");
    public static final Item blackTungstenAlloyIngot = simpleItem("black_tungsten_alloy_ingot");
    public static final Item blackTungstenAlloyDust = simpleItem("black_tungsten_alloy_dust");
    public static final Item debug = new ItemDebugStick();

    public static final Item aluminumAxe = axe("aluminum_axe", aluminumToolMaterial);
    public static final Item aluminumHoe = hoe("aluminum_hoe", aluminumToolMaterial);
    public static final Item aluminumPickaxe = pickaxe("aluminum_pickaxe", aluminumToolMaterial);
    public static final Item aluminumShovel = shovel("aluminum_shovel", aluminumToolMaterial);
    public static final Item aluminumSword = sword("aluminum_sword", aluminumToolMaterial);
    public static final Item tungstenAxe = axe("tungsten_axe", tungstenToolMaterial);
    public static final Item tungstenHoe = hoe("tungsten_hoe", tungstenToolMaterial);
    public static final Item tungstenPickaxe = pickaxe("tungsten_pickaxe", tungstenToolMaterial);
    public static final Item tungstenShovel = shovel("tungsten_shovel", tungstenToolMaterial);
    public static final Item tungstenSword = sword("tungsten_sword", tungstenToolMaterial);
    public static final Item aluminumAlloyAxe = axe("aluminum_alloy_axe", aluminumAlloyToolMaterial);
    public static final Item aluminumAlloyHoe = hoe("aluminum_alloy_hoe", aluminumAlloyToolMaterial);
    public static final Item aluminumAlloyPickaxe = pickaxe("aluminum_alloy_pickaxe", aluminumAlloyToolMaterial);
    public static final Item aluminumAlloyShovel = shovel("aluminum_alloy_shovel", aluminumAlloyToolMaterial);
    public static final Item aluminumAlloySword = sword("aluminum_alloy_sword", aluminumAlloyToolMaterial);
    public static final Item blackTungstenAlloyAxe = axe("black_tungsten_alloy_axe", blackTungstenAlloyToolMaterial);
    public static final Item blackTungstenAlloyHoe = hoe("black_tungsten_alloy_hoe", blackTungstenAlloyToolMaterial);
    public static final Item blackTungstenAlloyPickaxe = pickaxe(
        "black_tungsten_alloy_pickaxe",
        blackTungstenAlloyToolMaterial);
    public static final Item blackTungstenAlloyShovel = shovel(
        "black_tungsten_alloy_shovel",
        blackTungstenAlloyToolMaterial);
    public static final Item blackTungstenAlloySword = sword(
        "black_tungsten_alloy_sword",
        blackTungstenAlloyToolMaterial);

    private NEItems() {}

    public static void register() {
        setRepairMaterials();

        register(aluminumIngot, "aluminum_ingot");
        register(aluminumDust, "aluminum_dust");
        register(rawAluminumOre, "raw_aluminum_ore");
        register(tungstenIngot, "tungsten_ingot");
        register(tungstenDust, "tungsten_dust");
        register(rawTungstenOre, "raw_tungsten_ore");
        register(aluminumAlloyIngot, "aluminum_alloy_ingot");
        register(aluminumAlloyDust, "aluminum_alloy_dust");
        register(blackTungstenAlloyIngot, "black_tungsten_alloy_ingot");
        register(blackTungstenAlloyDust, "black_tungsten_alloy_dust");
        register(debug, "debug");

        register(aluminumAxe, "aluminum_axe");
        register(aluminumHoe, "aluminum_hoe");
        register(aluminumPickaxe, "aluminum_pickaxe");
        register(aluminumShovel, "aluminum_shovel");
        register(aluminumSword, "aluminum_sword");
        register(tungstenAxe, "tungsten_axe");
        register(tungstenHoe, "tungsten_hoe");
        register(tungstenPickaxe, "tungsten_pickaxe");
        register(tungstenShovel, "tungsten_shovel");
        register(tungstenSword, "tungsten_sword");
        register(aluminumAlloyAxe, "aluminum_alloy_axe");
        register(aluminumAlloyHoe, "aluminum_alloy_hoe");
        register(aluminumAlloyPickaxe, "aluminum_alloy_pickaxe");
        register(aluminumAlloyShovel, "aluminum_alloy_shovel");
        register(aluminumAlloySword, "aluminum_alloy_sword");
        register(blackTungstenAlloyAxe, "black_tungsten_alloy_axe");
        register(blackTungstenAlloyHoe, "black_tungsten_alloy_hoe");
        register(blackTungstenAlloyPickaxe, "black_tungsten_alloy_pickaxe");
        register(blackTungstenAlloyShovel, "black_tungsten_alloy_shovel");
        register(blackTungstenAlloySword, "black_tungsten_alloy_sword");
    }

    private static Item simpleItem(String id) {
        return new Item().setUnlocalizedName(id)
            .setTextureName(NeoECOAE.MODID + ":" + id)
            .setCreativeTab(NECreativeTabs.NEO_ECO_AE);
    }

    private static Item axe(String id, Item.ToolMaterial material) {
        return new NEItemAxe(material).setUnlocalizedName(id)
            .setTextureName(NeoECOAE.MODID + ":" + id)
            .setCreativeTab(NECreativeTabs.NEO_ECO_AE);
    }

    private static Item hoe(String id, Item.ToolMaterial material) {
        return new ItemHoe(material).setUnlocalizedName(id)
            .setTextureName(NeoECOAE.MODID + ":" + id)
            .setCreativeTab(NECreativeTabs.NEO_ECO_AE);
    }

    private static Item pickaxe(String id, Item.ToolMaterial material) {
        return new NEItemPickaxe(material).setUnlocalizedName(id)
            .setTextureName(NeoECOAE.MODID + ":" + id)
            .setCreativeTab(NECreativeTabs.NEO_ECO_AE);
    }

    private static Item shovel(String id, Item.ToolMaterial material) {
        return new ItemSpade(material).setUnlocalizedName(id)
            .setTextureName(NeoECOAE.MODID + ":" + id)
            .setCreativeTab(NECreativeTabs.NEO_ECO_AE);
    }

    private static Item sword(String id, Item.ToolMaterial material) {
        return new ItemSword(material).setUnlocalizedName(id)
            .setTextureName(NeoECOAE.MODID + ":" + id)
            .setCreativeTab(NECreativeTabs.NEO_ECO_AE);
    }

    private static void register(Item item, String id) {
        GameRegistry.registerItem(item, id);
    }

    private static void setRepairMaterials() {
        aluminumToolMaterial.setRepairItem(new ItemStack(aluminumIngot));
        tungstenToolMaterial.setRepairItem(new ItemStack(tungstenIngot));
        aluminumAlloyToolMaterial.setRepairItem(new ItemStack(aluminumAlloyIngot));
        blackTungstenAlloyToolMaterial.setRepairItem(new ItemStack(blackTungstenAlloyIngot));
    }
}
