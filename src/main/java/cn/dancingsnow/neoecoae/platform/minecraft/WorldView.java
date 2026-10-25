package cn.dancingsnow.neoecoae.platform.minecraft;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/** Modern world API over an actual Forge world. Read-only probes never request missing chunks. */
public class WorldView {

    private static final Map<World, WeakReference<WorldView>> VIEWS = new WeakHashMap<>();
    protected final World world;
    public final boolean isClientSide;

    protected WorldView(World world) {
        this.world = world;
        isClientSide = world.isRemote;
    }

    public static synchronized WorldView of(World world) {
        if (world == null) return null;
        WeakReference<WorldView> cached = VIEWS.get(world);
        WorldView result = cached == null ? null : cached.get();
        if (result == null) {
            result = world instanceof WorldServer ? new ServerWorldView((WorldServer) world) : new WorldView(world);
            VIEWS.put(world, new WeakReference<>(result));
        }
        return result;
    }

    public World nativeWorld() {
        return world;
    }

    public long getGameTime() {
        return world.getTotalWorldTime();
    }

    public boolean isClientSide() {
        return isClientSide;
    }

    public HolderLookup.Provider registryAccess() {
        return RegistryAccess.EMPTY;
    }

    public boolean hasChunkAt(BlockPos pos) {
        return world.getChunkProvider()
            .chunkExists(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public void markLoadedChunkDirty(BlockPos pos) {
        if (hasChunkAt(pos)) world.getChunkFromChunkCoords(pos.getX() >> 4, pos.getZ() >> 4)
            .setChunkModified();
    }

    public TileEntity getBlockEntity(BlockPos pos) {
        return hasChunkAt(pos) ? world.getTileEntity(pos.getX(), pos.getY(), pos.getZ()) : null;
    }

    public BlockState getBlockState(BlockPos pos) {
        if (!hasChunkAt(pos)) return BlockState.defaultState(net.minecraft.init.Blocks.air);
        var block = world.getBlock(pos.getX(), pos.getY(), pos.getZ());
        TileEntity tile = getBlockEntity(pos);
        if (tile instanceof NativeStateHolder) {
            BlockState state = ((NativeStateHolder) tile).nativeBlockState();
            if (state != null && state.is(block)) return state;
        }
        return block instanceof NativeStateBlock
            ? ((NativeStateBlock) block).fromMetadata(world.getBlockMetadata(pos.getX(), pos.getY(), pos.getZ()))
            : BlockState.defaultState(block);
    }

    public boolean setBlockAndUpdate(BlockPos pos, BlockState state) {
        return setBlock(pos, state, 3);
    }

    public boolean setBlock(BlockPos pos, BlockState state, int flags) {
        if (!hasChunkAt(pos) || pos.getY() < 0 || pos.getY() > 255) return false;
        int metadata = state.getBlock() instanceof NativeStateBlock
            ? ((NativeStateBlock) state.getBlock()).metadata(state)
            : 0;
        if (world.getBlock(pos.getX(), pos.getY(), pos.getZ()) != state.getBlock()) {
            if (!world.setBlock(pos.getX(), pos.getY(), pos.getZ(), state.getBlock(), metadata, flags)) return false;
        } else if (world.getBlockMetadata(pos.getX(), pos.getY(), pos.getZ()) != metadata) {
            if (!world.setBlockMetadataWithNotify(pos.getX(), pos.getY(), pos.getZ(), metadata, flags)) return false;
        }
        TileEntity tile = getBlockEntity(pos);
        if (tile instanceof NativeStateHolder) ((NativeStateHolder) tile).nativeBlockState(state);
        if ((flags & 2) != 0) world.markBlockForUpdate(pos.getX(), pos.getY(), pos.getZ());
        return true;
    }
}
