package cn.dancingsnow.neoecoae.platform.forge;

import java.util.ArrayList;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.WorldServer;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.platform.recipe.SourceRecipes;
import cn.dancingsnow.neoecoae.platform.registry.BasicBlocks;
import cn.dancingsnow.neoecoae.platform.registry.NativeContent;
import cpw.mods.fml.common.registry.GameRegistry;

/** Real native content/recipe assertions, called only by the explicit development acceptance modes. */
public final class PortingContentFixture {

    private PortingContentFixture() {}

    public static void check(WorldServer world) {
        require(
            BasicBlocks.blocks()
                .size() == 13,
            "Thirteen source material blocks");
        for (var entry : BasicBlocks.blocks()
            .entrySet()) {
            require(
                GameRegistry.findBlock("neoecoae", entry.getKey()) == entry.getValue(),
                "Registered material block " + entry.getKey());
            require(
                GameRegistry.findItem("neoecoae", entry.getKey()) != null,
                "Registered block item " + entry.getKey());
        }
        require(
            cn.dancingsnow.neoecoae.blocks.CasingBlock.SHAPE.boxes()
                .size() == 13,
            "Complete source casing shape");
        var frame = BasicBlocks.blocks()
            .get("aluminum_alloy_casing");
        var collision = new ArrayList<AxisAlignedBB>();
        frame.addCollisionBoxesToList(
            world,
            0,
            240,
            0,
            AxisAlignedBB.getBoundingBox(.45, 240.05, .45, .55, 240.15, .55),
            collision,
            null);
        require(collision.isEmpty(), "Source casing lower central gap");
        frame.addCollisionBoxesToList(
            world,
            0,
            240,
            0,
            AxisAlignedBB.getBoundingBox(.45, 240.45, .45, .55, 240.55, .55),
            collision,
            null);
        require(collision.size() == 1, "Source casing central core");
        var table = table();
        for (int slot = 0; slot < 9; slot++)
            table.setInventorySlotContents(slot, new ItemStack(NativeContent.item("aluminum_ingot")));
        var block = requiredRecipe("aluminum_block");
        require(
            block.matches(table, world) && block.getCraftingResult(table)
                .getItem()
                == net.minecraft.item.Item.getItemFromBlock(
                    BasicBlocks.blocks()
                        .get("aluminum_block")),
            "Nine-ingot source tag recipe");
        table = table();
        table.setInventorySlotContents(
            8,
            new ItemStack(
                BasicBlocks.blocks()
                    .get("aluminum_block")));
        var unpack = requiredRecipe("aluminum_ingot");
        require(
            unpack.matches(table, world) && unpack.getCraftingResult(table).stackSize == 9,
            "Native storage block unpacking");
        table = table();
        var damaged = new ItemStack(NativeContent.item("aluminum_pickaxe"), 1, 37);
        damaged.setStackDisplayName("NBT retained");
        damaged.getTagCompound()
            .setLong("neoecoaeAcceptance", 123456789L);
        table
            .setInventorySlotContents(0, new ItemStack(NativeContent.item("aluminum_alloy_upgrade_smithing_template")));
        table.setInventorySlotContents(4, damaged);
        table.setInventorySlotContents(8, new ItemStack(NativeContent.item("aluminum_alloy_ingot")));
        var upgrade = requiredRecipe("aluminum_alloy_pickaxe_smithing");
        require(upgrade.matches(table, world), "Native smithing ingredients accept damaged tool");
        var result = upgrade.getCraftingResult(table);
        require(
            result.getItem() == NativeContent.item("aluminum_alloy_pickaxe") && result.getItemDamage() == 37
                && ItemStack.areItemStackTagsEqual(damaged, result),
            "Smithing keeps base damage/name/custom NBT");
        result.getTagCompound()
            .setLong("neoecoaeAcceptance", 0L);
        require(
            damaged.getTagCompound()
                .getLong("neoecoaeAcceptance") == 123456789L,
            "Smithing output owns its NBT");
        require(
            "installed:inscriber".equals(
                SourceRecipes.status()
                    .get("inscriber/superconducting_processor")),
            "Source processor inscriber recipe installed");
        require(
            GameRegistry.findItem("neoecoae", "integrated_working_station_module_empty") == null,
            "Large workstation cards excluded");
        NeoECOAE.LOG.info(
            "NEOECOAE_NATIVE_CONTENT_OK core_items=65 material_blocks=13 source_shape_boxes=13 crafting={} smithing_nbt=true processor_inscriber=true",
            SourceRecipes.crafting()
                .size());
    }

    private static net.minecraft.item.crafting.IRecipe requiredRecipe(String id) {
        var recipe = SourceRecipes.crafting()
            .get(id);
        if (recipe == null) throw new IllegalStateException(
            "Source recipe missing: " + id
                + " "
                + SourceRecipes.status()
                    .get(id));
        return recipe;
    }

    private static InventoryCrafting table() {
        return new InventoryCrafting(new Container() {

            @Override
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
                return true;
            }
        }, 3, 3);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
