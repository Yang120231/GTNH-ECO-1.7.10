package cn.dancingsnow.neoecoae.block;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

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
}
