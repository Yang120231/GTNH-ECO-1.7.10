package cn.dancingsnow.neoecoae.platform.structurelib;

import java.util.function.Consumer;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureElement;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts;
import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockRequirement;
import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockRequirements;
import cn.dancingsnow.neoecoae.multiblock.placement.WorldPlannedBlock;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.NativeStateBlock;
import cn.dancingsnow.neoecoae.platform.minecraft.NativeStateHolder;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.multiblock.NativePlacementPlatform;

/**
 * One already-rotated structure slot. It places the exact target state itself, because ItemBlock placement
 * would re-derive FACING from the player's yaw and the calculators would reject drives and vents.
 */
final class NEStructureElement implements IStructureElement<TileEntity> {

    private final BlockState target;
    private final ECOMultiblockRequirement requirement;
    private final ItemStack item;

    NEStructureElement(BlockState target, ECOMultiblockRequirement requirement, ItemStack item) {
        this.target = target;
        this.requirement = requirement;
        this.item = item;
    }

    BlockState target() {
        return target;
    }

    @Override
    public boolean check(TileEntity host, World world, int x, int y, int z) {
        return accepts(world, x, y, z);
    }

    @Override
    public boolean spawnHint(TileEntity host, World world, int x, int y, int z, ItemStack trigger) {
        StructureLibAPI.hintParticle(world, x, y, z, target.getBlock(), metadata(target));
        return true;
    }

    /** Creative builds and BlockRenderer6343 previews; an obstruction is left alone without aborting the walk. */
    @Override
    public boolean placeBlock(TileEntity host, World world, int x, int y, int z, ItemStack trigger) {
        if (accepts(world, x, y, z)) return true;
        Block existing = world.getBlock(x, y, z);
        if (!existing.isAir(world, x, y, z) && !existing.isReplaceable(world, x, y, z)) return true;
        world.setBlock(x, y, z, target.getBlock(), metadata(target), 3);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof NativeStateHolder) ((NativeStateHolder) tile).nativeBlockState(target);
        return true;
    }

    @Override
    public BlocksToPlace getBlocksToPlace(TileEntity host, World world, int x, int y, int z, ItemStack trigger,
        AutoPlaceEnvironment env) {
        return BlocksToPlace.create(item.copy());
    }

    @Override
    @SuppressWarnings("deprecation")
    public PlaceResult survivalPlaceBlock(TileEntity host, World world, int x, int y, int z, ItemStack trigger,
        IItemSource source, EntityPlayerMP actor, Consumer<IChatComponent> chatter) {
        return survivalPlaceBlock(
            host,
            world,
            x,
            y,
            z,
            trigger,
            AutoPlaceEnvironment.fromLegacy(source, actor, chatter));
    }

    @Override
    public PlaceResult survivalPlaceBlock(TileEntity host, World world, int x, int y, int z, ItemStack trigger,
        AutoPlaceEnvironment env) {
        if (accepts(world, x, y, z)) return PlaceResult.SKIP;
        WorldView level = WorldView.of(world);
        if (!(level instanceof ServerWorldView server) || !(env.getActor() instanceof EntityPlayerMP player))
            return PlaceResult.REJECT;
        BlockPos pos = new BlockPos(x, y, z);
        BlockState existing = level.getBlockState(pos);
        WorldPlannedBlock planned = new WorldPlannedBlock(pos, target, item.copy(), requirement);
        if (!server.hasChunkAt(pos) || !NativePlacementPlatform.insideWorldBounds(pos)
            || !NativePlacementPlatform.replaceable(level, pos, existing)
            || !NativePlacementPlatform.mayInteract(server, player, planned)) {
            env.getChatter()
                .accept(new ChatComponentTranslation("neoecoae.structure.blocked", x, y, z));
            return PlaceResult.REJECT_CONTINUE;
        }
        if (!env.getSource()
            .takeOne(item, true)) {
            env.getChatter()
                .accept(new ChatComponentTranslation("neoecoae.structure.missing_item", item.func_151000_E()));
            return PlaceResult.REJECT;
        }
        if (!NativePlacementPlatform.placeWithPermissionCheck(server, planned, player))
            return PlaceResult.REJECT_CONTINUE;
        if (!env.getSource()
            .takeOne(item, false)) {
            server.setBlock(pos, existing, 3);
            return PlaceResult.REJECT;
        }
        NativePlacementPlatform.playPlacementSound(server, planned);
        return PlaceResult.ACCEPT;
    }

    private boolean accepts(World world, int x, int y, int z) {
        WorldView level = WorldView.of(world);
        BlockPos pos = new BlockPos(x, y, z);
        BlockState actual = level.getBlockState(pos);
        Boolean role = ECOMultiblockParts.acceptsExpected(ECOMultiblockParts.context(level, pos), target);
        return ECOMultiblockRequirements.accepts(
            new ECOMultiblockRequirements.Context(level, pos, target, actual),
            role == null ? requirement.accepts(actual) : role);
    }

    private static int metadata(BlockState state) {
        return state.getBlock() instanceof NativeStateBlock ? ((NativeStateBlock) state.getBlock()).metadata(state) : 0;
    }
}
