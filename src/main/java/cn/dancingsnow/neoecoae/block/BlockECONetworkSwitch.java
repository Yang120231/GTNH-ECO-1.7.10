package cn.dancingsnow.neoecoae.block;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.tile.ECOControllerSubsystem;
import cn.dancingsnow.neoecoae.tile.TileECOController;

public final class BlockECONetworkSwitch extends BlockDirectionalModernModel {

    private final ECOControllerSubsystem subsystem;
    private final boolean highEnergy;

    public BlockECONetworkSwitch(String id, ECOControllerSubsystem subsystem, boolean highEnergy) {
        super(
            id,
            id,
            new String[] {
                "neoecoae:block/network_switch/"
                    + (subsystem == ECOControllerSubsystem.CRAFTING ? "crafting" : "computation"),
                "neoecoae:block/network_switch/" + (highEnergy ? "power_light" : "light") });
        this.subsystem = subsystem;
        this.highEnergy = highEnergy;
    }

    public ECOControllerSubsystem getSubsystem() {
        return this.subsystem;
    }

    public int getMultiplier() {
        return this.highEnergy ? 8 : 2;
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public net.minecraft.tileentity.TileEntity createTileEntity(World world, int metadata) {
        return new cn.dancingsnow.neoecoae.tile.TileECONetworkSwitch();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        for (net.minecraftforge.common.util.ForgeDirection direction : net.minecraftforge.common.util.ForgeDirection.VALID_DIRECTIONS) {
            net.minecraft.tileentity.TileEntity tile = world
                .getTileEntity(x + direction.offsetX, y + direction.offsetY, z + direction.offsetZ);
            if (tile instanceof TileECOController controller && controller.getSubsystem() == this.subsystem
                && controller.getNetworkSwitch() == this
                && controller.isUseableByPlayer(player)) {
                controller.setNetworkFrequency(controller.getNetworkFrequency() + (player.isSneaking() ? -1 : 1));
                player.addChatMessage(
                    new net.minecraft.util.ChatComponentTranslation(
                        "chat.neoecoae.network_frequency",
                        controller.getNetworkFrequency()));
                return true;
            }
        }
        return false;
    }
}
