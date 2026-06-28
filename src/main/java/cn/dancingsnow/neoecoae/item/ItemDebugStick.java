package cn.dancingsnow.neoecoae.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cn.dancingsnow.neoecoae.multiblock.ECODebugStructureBuilder;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationResult;
import cn.dancingsnow.neoecoae.tile.TileECOController;

public class ItemDebugStick extends Item {

    public ItemDebugStick() {
        this.setUnlocalizedName("debug");
        this.setTextureName("stick");
        this.setCreativeTab(NECreativeTabs.NEO_ECO_AE);
        this.setMaxStackSize(1);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileECOController)) {
            return false;
        }

        TileECOController controller = (TileECOController) tile;
        if (!world.isRemote) {
            if (player.isSneaking()) {
                ECOFormationResult result = controller.scanFormation();
                sendStatus(player, controller, result, -1);
            } else {
                ECODebugStructureBuilder.BuildResult buildResult = ECODebugStructureBuilder.buildDefault(controller);
                sendStatus(player, controller, buildResult.getFormationResult(), buildResult.getPlacedBlocks());
            }
        }
        return true;
    }

    private static void sendStatus(EntityPlayer player, TileECOController controller, ECOFormationResult result,
        int placedBlocks) {
        player.addChatMessage(new ChatComponentText("ECO Controller"));
        player.addChatMessage(
            new ChatComponentText(
                "System: " + controller.getSubsystem()
                    .getId()));
        player.addChatMessage(
            new ChatComponentText(
                "Tier: " + controller.getTier()
                    .getId()));
        player.addChatMessage(
            new ChatComponentText(
                "Facing: " + controller.getFacing()
                    .name()
                    .toLowerCase()));
        if (placedBlocks >= 0) {
            player.addChatMessage(
                new ChatComponentText(
                    "Generated: length " + ECODebugStructureBuilder.DEFAULT_LENGTH
                        + ", changed "
                        + placedBlocks
                        + " blocks"));
        }
        player.addChatMessage(new ChatComponentText("Formed: " + controller.isFormed()));
        player.addChatMessage(new ChatComponentText("Mirrored: " + controller.isMirrored()));
        player.addChatMessage(new ChatComponentText("Scan: " + result.getMessage()));
    }
}
