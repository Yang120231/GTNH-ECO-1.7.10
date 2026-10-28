package cn.dancingsnow.neoecoae.compat.waila;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.platform.network.ServerTasks;
import cn.dancingsnow.neoecoae.platform.network.ServerThread;
import cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

/** WAILA counterpart of the 1.21 Jade plugin. Loaded only through WAILA's IMC callback. */
public final class NEWailaPlugin implements IWailaDataProvider {

    private static final NEWailaPlugin INSTANCE = new NEWailaPlugin();
    /** Written only on the server thread; snapshots are never mutated after publication. */
    private static final Map<TileEntity, NBTTagCompound> SNAPSHOTS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<TileEntity> PENDING = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void callbackRegister(IWailaRegistrar registrar) {
        // WAILA asks for server data by block or tile; every ECO machine shares this block base class.
        registrar.registerBodyProvider(INSTANCE, NativeMachineBlock.class);
        registrar.registerNBTProvider(INSTANCE, NativeMachineBlock.class);
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public List<String> getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        NBTTagCompound data = accessor.getNBTData();
        if (data != null && data.hasKey(ECOWailaData.TAG))
            ECOWailaData.appendLines(data.getCompoundTag(ECOWailaData.TAG), tip);
        return tip;
    }

    @Override
    public List<String> getWailaTail(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return tip;
    }

    /**
     * WAILA answers on the netty thread, but reading a drive flushes its cell and the host builds a cached snapshot,
     * so the values are always computed on the server thread and the request returns the latest one. WAILA polls
     * several times a second, so the first hover is filled in on the next request.
     */
    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x,
        int y, int z) {
        if (tile == null || !ServerTasks.isRunning()) return tag;
        NBTTagCompound snapshot;
        if (ServerThread.isCurrent()) snapshot = ECOWailaData.write(tile);
        else {
            snapshot = SNAPSHOTS.get(tile);
            if (PENDING.add(tile)) ServerTasks.execute(() -> {
                try {
                    if (!tile.isInvalid()) SNAPSHOTS.put(tile, ECOWailaData.write(tile));
                } finally {
                    PENDING.remove(tile);
                }
            });
        }
        if (snapshot != null) tag.setTag(ECOWailaData.TAG, snapshot.copy());
        return tag;
    }
}
