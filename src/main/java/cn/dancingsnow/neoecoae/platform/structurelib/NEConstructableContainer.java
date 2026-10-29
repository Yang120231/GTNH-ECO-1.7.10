package cn.dancingsnow.neoecoae.platform.structurelib;

import java.util.function.Function;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.alignment.constructable.IMultiblockInfoContainer;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;

import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockStateProperties;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;

/**
 * Hologram projector and BlockRenderer6343 entry for one controller class. The projector's stack size (or the
 * "length" channel) selects the length, the "mirror" channel mirrors, and facing comes from the controller itself.
 */
final class NEConstructableContainer implements IMultiblockInfoContainer<TileEntity> {

    private final net.minecraft.block.Block controller;
    private final Function<TileEntity, MultiBlockDefinition> definitions;

    NEConstructableContainer(net.minecraft.block.Block controller,
        Function<TileEntity, MultiBlockDefinition> definitions) {
        this.controller = controller;
        this.definitions = definitions;
    }

    @Override
    public void construct(ItemStack trigger, boolean hintsOnly, TileEntity host, ExtendedFacing ignored) {
        NEStructureShape shape = shape(trigger, host);
        if (shape == null) return;
        if (hintsOnly) {
            shape.hints(host, trigger);
            return;
        }
        World world = host.getWorldObj();
        if (world.isAirBlock(host.xCoord, host.yCoord, host.zCoord)) {
            // BlockRenderer6343 builds around a bare tile and reads the origin block as the preview's item; the
            // controller sits outside the shape, so place it here. A real controller always occupies its own spot.
            world.setBlock(host.xCoord, host.yCoord, host.zCoord, controller, 0, 2);
            world.setTileEntity(host.xCoord, host.yCoord, host.zCoord, host);
        }
        shape.build(host, trigger);
        rebuild(host);
    }

    /** Returns -2 off the logical server so BlockRenderer6343 falls back to {@link #construct}. */
    @Override
    public int survivalConstruct(ItemStack trigger, int budget, ISurvivalBuildEnvironment env, TileEntity host,
        ExtendedFacing ignored) {
        World world = host.getWorldObj();
        if (world == null || world.isRemote || !(env.getActor() instanceof EntityPlayerMP)) return -2;
        NEStructureShape shape = shape(trigger, host);
        if (shape == null) return -1;
        int built = shape.survivalBuild(host, trigger, budget, env);
        if (built != 0) rebuild(host);
        return built;
    }

    @Override
    public String[] getDescription(ItemStack trigger) {
        return new String[] { StatCollector.translateToLocal("neoecoae.structure.desc.length"),
            StatCollector.translateToLocal("neoecoae.structure.desc.mirror") };
    }

    private NEStructureShape shape(ItemStack trigger, TileEntity host) {
        if (host.getWorldObj() == null) return null;
        MultiBlockDefinition definition = definitions.apply(host);
        if (definition == null) return null;
        int length = ChannelDataAccessor.hasSubChannel(trigger, NEStructureLib.CHANNEL_LENGTH)
            ? ChannelDataAccessor.getChannelData(trigger, NEStructureLib.CHANNEL_LENGTH)
            : trigger.stackSize;
        int repeats = Math.max(definition.getExpandMin(), Math.min(definition.getExpandMax(), length));
        boolean mirrored = ChannelDataAccessor.hasSubChannel(trigger, NEStructureLib.CHANNEL_MIRROR)
            && ChannelDataAccessor.getChannelData(trigger, NEStructureLib.CHANNEL_MIRROR) != 0;
        return NEStructureShape.of(definition, repeats, facing(host), mirrored);
    }

    private static Direction facing(TileEntity host) {
        Direction facing = WorldView.of(host.getWorldObj())
            .getBlockState(new BlockPos(host.xCoord, host.yCoord, host.zCoord))
            .getOptionalValue(BlockStateProperties.HORIZONTAL_FACING)
            .orElse(Direction.NORTH);
        return facing.getAxis()
            .isHorizontal() ? facing : Direction.NORTH;
    }

    private static void rebuild(TileEntity host) {
        // Preview worlds (BlockRenderer6343) are neither remote nor a WorldServer; never form a multiblock there.
        if (!(host.getWorldObj() instanceof net.minecraft.world.WorldServer)) return;
        if (host instanceof NEBlockEntity) ((NEBlockEntity<?, ?>) host).rebuildMultiblock();
        else if (host instanceof ECOLargeIntegratedWorkingStationBlockEntity)
            ((ECOLargeIntegratedWorkingStationBlockEntity) host).rebuildMultiblock();
    }
}
