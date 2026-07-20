package cn.dancingsnow.neoecoae.block;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.gui.mui.NeoEcoGuiData;
import cn.dancingsnow.neoecoae.gui.mui.NeoEcoUiFactory;
import cn.dancingsnow.neoecoae.tile.ECOControllerSubsystem;
import cn.dancingsnow.neoecoae.tile.TileECOInterface;

public class BlockECOInterface extends BlockDirectionalModernModel {

    private final ECOControllerSubsystem subsystem;

    public BlockECOInterface(String id, String modelName, String[] textureNames, ECOControllerSubsystem subsystem) {
        super(id, modelName, textureNames);
        this.subsystem = subsystem;
    }

    public ECOControllerSubsystem getSubsystem() {
        return this.subsystem;
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileECOInterface(this.subsystem);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileECOInterface)) {
            return false;
        }
        TileECOInterface ecoInterface = (TileECOInterface) tile;
        if (this.subsystem != ECOControllerSubsystem.STORAGE) {
            return false;
        }
        if (!world.isRemote) {
            NeoEcoUiFactory.openTile(player, NeoEcoGuiData.Kind.STORAGE_INTERFACE, ecoInterface);
        }
        return true;
    }
}
