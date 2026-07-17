package cn.dancingsnow.neoecoae.storage.ae2;

import java.util.List;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import cn.dancingsnow.neoecoae.storage.domain.ECOStorageHostMode;
import cn.dancingsnow.neoecoae.storage.domain.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cn.dancingsnow.neoecoae.tile.TileECOInterface;

/**
 * Bounded INPUT/OUTPUT bridge for the legacy AE2 storage API.
 *
 * <p>
 * The bridge deliberately follows the 1.20/1.21 transfer transaction: simulate the destination,
 * modulate the source, modulate the destination, and return a remainder to the source when a
 * destination changes between the two calls. The key budget is shared by item and fluid channels
 * so a busy network cannot make a single interface consume an unbounded amount of server time.
 * </p>
 */
public final class ECOStorageInterfaceTransfer {

    public static final int MAX_KEYS_PER_TICK = 64;

    private ECOStorageInterfaceTransfer() {}

    public static long transfer(TileECOInterface interfaceTile, TileECOController controller) {
        if (interfaceTile == null || controller == null
            || interfaceTile.getWorldObj() == null
            || interfaceTile.getWorldObj().isRemote
            || !controller.isFormed()
            || controller.getHostMode() == ECOStorageHostMode.MIGRATING_TO_INFINITE
            || !interfaceTile.isStorageTransferMode()) {
            if (interfaceTile != null && interfaceTile.getWorldObj() != null && !interfaceTile.getWorldObj().isRemote) {
                interfaceTile.recordStorageInterfaceTransfer(0L);
            }
            return 0L;
        }
        IStorageGrid grid = interfaceTile.getStorageGridForTransfer();
        if (grid == null) {
            interfaceTile.recordStorageInterfaceTransfer(0L);
            return 0L;
        }

        BaseActionSource source = new MachineSource(interfaceTile);
        int remainingKeys = MAX_KEYS_PER_TICK;
        long transferred = 0L;
        ECOStorageInterfaceMode mode = interfaceTile.getStorageInterfaceMode();
        if (mode == ECOStorageInterfaceMode.OUTPUT) {
            IMEMonitor<IAEItemStack> items = grid.getItemInventory();
            long itemAmount = exportItems(interfaceTile, controller, items, source, remainingKeys);
            transferred = saturatedAdd(transferred, itemAmount);
            remainingKeys -= lastVisited;
            if (remainingKeys > 0) {
                IMEMonitor<IAEFluidStack> fluids = grid.getFluidInventory();
                long fluidAmount = exportFluids(interfaceTile, controller, fluids, source, remainingKeys);
                transferred = saturatedAdd(transferred, fluidAmount);
                remainingKeys -= lastVisited;
            }
        } else if (mode == ECOStorageInterfaceMode.INPUT) {
            IMEMonitor<IAEItemStack> items = grid.getItemInventory();
            long itemAmount = importItems(interfaceTile, controller, items, source, remainingKeys);
            transferred = saturatedAdd(transferred, itemAmount);
            remainingKeys -= lastVisited;
            if (remainingKeys > 0) {
                IMEMonitor<IAEFluidStack> fluids = grid.getFluidInventory();
                long fluidAmount = importFluids(interfaceTile, controller, fluids, source, remainingKeys);
                transferred = saturatedAdd(transferred, fluidAmount);
            }
        }
        interfaceTile.recordStorageInterfaceTransfer(transferred);
        return transferred;
    }

    /*
     * The transfer methods are called on the server thread. Keeping this tiny state local to the call
     * avoids allocating four result objects on every tick while still sharing the key budget.
     */
    private static int lastVisited;

    private static long exportItems(TileECOInterface interfaceTile, TileECOController controller,
        IMEMonitor<IAEItemStack> target, BaseActionSource source, int maxKeys) {
        lastVisited = 0;
        if (target == null || maxKeys <= 0) {
            return 0L;
        }
        List<IMEInventoryHandler> handlers = interfaceTile.getStorageTransferHandlers(controller, StorageChannel.ITEMS);
        long moved = 0L;
        for (IMEInventoryHandler rawHandler : handlers) {
            if (lastVisited >= maxKeys) {
                break;
            }
            @SuppressWarnings("unchecked")
            IMEInventoryHandler<IAEItemStack> handler = (IMEInventoryHandler<IAEItemStack>) rawHandler;
            IItemList<IAEItemStack> available = StorageChannel.ITEMS.createList();
            handler.getAvailableItems(available, 0);
            for (IAEItemStack stack : available) {
                if (lastVisited >= maxKeys) {
                    break;
                }
                long amount = positiveAmount(stack == null ? 0L : stack.getStackSize());
                if (amount <= 0L) {
                    continue;
                }
                lastVisited++;
                IAEItemStack request = (IAEItemStack) stack.copy()
                    .setStackSize(amount);
                IAEItemStack targetRemainder = target.injectItems(request.copy(), Actionable.SIMULATE, source);
                long accepted = acceptedAmount(amount, targetRemainder);
                if (accepted <= 0L) {
                    continue;
                }
                IAEItemStack extracted = handler.extractItems(
                    request.copy()
                        .setStackSize(accepted),
                    Actionable.MODULATE,
                    source);
                if (extracted == null || extracted.getStackSize() <= 0L) {
                    continue;
                }
                IAEItemStack insertedRemainder = target.injectItems(extracted, Actionable.MODULATE, source);
                long inserted = acceptedAmount(extracted.getStackSize(), insertedRemainder);
                if (inserted < extracted.getStackSize()) {
                    long remainder = extracted.getStackSize() - inserted;
                    handler.injectItems(
                        extracted.copy()
                            .setStackSize(remainder),
                        Actionable.MODULATE,
                        source);
                }
                moved = saturatedAdd(moved, inserted);
            }
        }
        return moved;
    }

    private static long importItems(TileECOInterface interfaceTile, TileECOController controller,
        IMEMonitor<IAEItemStack> sourceStorage, BaseActionSource source, int maxKeys) {
        lastVisited = 0;
        if (sourceStorage == null || maxKeys <= 0) {
            return 0L;
        }
        List<IMEInventoryHandler> handlers = interfaceTile.getStorageTransferHandlers(controller, StorageChannel.ITEMS);
        IItemList<IAEItemStack> available = StorageChannel.ITEMS.createList();
        sourceStorage.getAvailableItems(available, 0);
        long moved = 0L;
        for (IAEItemStack stack : available) {
            if (lastVisited >= maxKeys) {
                break;
            }
            long amount = positiveAmount(stack == null ? 0L : stack.getStackSize());
            if (amount <= 0L) {
                continue;
            }
            lastVisited++;
            IAEItemStack request = (IAEItemStack) stack.copy()
                .setStackSize(amount);
            long accepted = insertItems(handlers, request, Actionable.SIMULATE, source);
            if (accepted <= 0L) {
                continue;
            }
            IAEItemStack extracted = sourceStorage.extractItems(
                request.copy()
                    .setStackSize(accepted),
                Actionable.MODULATE,
                source);
            if (extracted == null || extracted.getStackSize() <= 0L) {
                continue;
            }
            long inserted = insertItems(handlers, extracted, Actionable.MODULATE, source);
            if (inserted < extracted.getStackSize()) {
                long remainder = extracted.getStackSize() - inserted;
                sourceStorage.injectItems(
                    extracted.copy()
                        .setStackSize(remainder),
                    Actionable.MODULATE,
                    source);
            }
            moved = saturatedAdd(moved, inserted);
        }
        return moved;
    }

    private static long exportFluids(TileECOInterface interfaceTile, TileECOController controller,
        IMEMonitor<IAEFluidStack> target, BaseActionSource source, int maxKeys) {
        lastVisited = 0;
        if (target == null || maxKeys <= 0) {
            return 0L;
        }
        List<IMEInventoryHandler> handlers = interfaceTile
            .getStorageTransferHandlers(controller, StorageChannel.FLUIDS);
        long moved = 0L;
        for (IMEInventoryHandler rawHandler : handlers) {
            if (lastVisited >= maxKeys) {
                break;
            }
            @SuppressWarnings("unchecked")
            IMEInventoryHandler<IAEFluidStack> handler = (IMEInventoryHandler<IAEFluidStack>) rawHandler;
            IItemList<IAEFluidStack> available = StorageChannel.FLUIDS.createList();
            handler.getAvailableItems(available, 0);
            for (IAEFluidStack stack : available) {
                if (lastVisited >= maxKeys) {
                    break;
                }
                long amount = positiveAmount(stack == null ? 0L : stack.getStackSize());
                if (amount <= 0L) {
                    continue;
                }
                lastVisited++;
                IAEFluidStack request = (IAEFluidStack) stack.copy()
                    .setStackSize(amount);
                IAEFluidStack targetRemainder = target.injectItems(request.copy(), Actionable.SIMULATE, source);
                long accepted = acceptedAmount(amount, targetRemainder);
                if (accepted <= 0L) {
                    continue;
                }
                IAEFluidStack extracted = handler.extractItems(
                    request.copy()
                        .setStackSize(accepted),
                    Actionable.MODULATE,
                    source);
                if (extracted == null || extracted.getStackSize() <= 0L) {
                    continue;
                }
                IAEFluidStack insertedRemainder = target.injectItems(extracted, Actionable.MODULATE, source);
                long inserted = acceptedAmount(extracted.getStackSize(), insertedRemainder);
                if (inserted < extracted.getStackSize()) {
                    long remainder = extracted.getStackSize() - inserted;
                    handler.injectItems(
                        extracted.copy()
                            .setStackSize(remainder),
                        Actionable.MODULATE,
                        source);
                }
                moved = saturatedAdd(moved, inserted);
            }
        }
        return moved;
    }

    private static long importFluids(TileECOInterface interfaceTile, TileECOController controller,
        IMEMonitor<IAEFluidStack> sourceStorage, BaseActionSource source, int maxKeys) {
        lastVisited = 0;
        if (sourceStorage == null || maxKeys <= 0) {
            return 0L;
        }
        List<IMEInventoryHandler> handlers = interfaceTile
            .getStorageTransferHandlers(controller, StorageChannel.FLUIDS);
        IItemList<IAEFluidStack> available = StorageChannel.FLUIDS.createList();
        sourceStorage.getAvailableItems(available, 0);
        long moved = 0L;
        for (IAEFluidStack stack : available) {
            if (lastVisited >= maxKeys) {
                break;
            }
            long amount = positiveAmount(stack == null ? 0L : stack.getStackSize());
            if (amount <= 0L) {
                continue;
            }
            lastVisited++;
            IAEFluidStack request = (IAEFluidStack) stack.copy()
                .setStackSize(amount);
            long accepted = insertFluids(handlers, request, Actionable.SIMULATE, source);
            if (accepted <= 0L) {
                continue;
            }
            IAEFluidStack extracted = sourceStorage.extractItems(
                request.copy()
                    .setStackSize(accepted),
                Actionable.MODULATE,
                source);
            if (extracted == null || extracted.getStackSize() <= 0L) {
                continue;
            }
            long inserted = insertFluids(handlers, extracted, Actionable.MODULATE, source);
            if (inserted < extracted.getStackSize()) {
                long remainder = extracted.getStackSize() - inserted;
                sourceStorage.injectItems(
                    extracted.copy()
                        .setStackSize(remainder),
                    Actionable.MODULATE,
                    source);
            }
            moved = saturatedAdd(moved, inserted);
        }
        return moved;
    }

    private static long insertItems(List<IMEInventoryHandler> rawHandlers, IAEItemStack input, Actionable mode,
        BaseActionSource source) {
        long remaining = input == null ? 0L : input.getStackSize();
        long inserted = 0L;
        if (remaining <= 0L) {
            return 0L;
        }
        for (IMEInventoryHandler rawHandler : rawHandlers) {
            if (remaining <= 0L) {
                break;
            }
            @SuppressWarnings("unchecked")
            IMEInventoryHandler<IAEItemStack> handler = (IMEInventoryHandler<IAEItemStack>) rawHandler;
            IAEItemStack remainder = handler.injectItems(
                input.copy()
                    .setStackSize(remaining),
                mode,
                source);
            long accepted = acceptedAmount(remaining, remainder);
            inserted = saturatedAdd(inserted, accepted);
            remaining -= Math.min(remaining, accepted);
        }
        return inserted;
    }

    private static long insertFluids(List<IMEInventoryHandler> rawHandlers, IAEFluidStack input, Actionable mode,
        BaseActionSource source) {
        long remaining = input == null ? 0L : input.getStackSize();
        long inserted = 0L;
        if (remaining <= 0L) {
            return 0L;
        }
        for (IMEInventoryHandler rawHandler : rawHandlers) {
            if (remaining <= 0L) {
                break;
            }
            @SuppressWarnings("unchecked")
            IMEInventoryHandler<IAEFluidStack> handler = (IMEInventoryHandler<IAEFluidStack>) rawHandler;
            IAEFluidStack remainder = handler.injectItems(
                input.copy()
                    .setStackSize(remaining),
                mode,
                source);
            long accepted = acceptedAmount(remaining, remainder);
            inserted = saturatedAdd(inserted, accepted);
            remaining -= Math.min(remaining, accepted);
        }
        return inserted;
    }

    private static long acceptedAmount(long requested, appeng.api.storage.data.IAEStack remainder) {
        if (requested <= 0L) {
            return 0L;
        }
        long remaining = remainder == null ? 0L : positiveAmount(remainder.getStackSize());
        return Math.max(0L, requested - Math.min(requested, remaining));
    }

    private static long positiveAmount(long amount) {
        return Math.max(0L, amount);
    }

    private static long saturatedAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }
}
