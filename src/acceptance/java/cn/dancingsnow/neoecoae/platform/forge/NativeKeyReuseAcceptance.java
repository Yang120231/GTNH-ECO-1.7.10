package cn.dancingsnow.neoecoae.platform.forge;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import appeng.api.config.Actionable;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.infinite.*;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.*;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.*;
import cn.dancingsnow.neoecoae.platform.network.RegistryFriendlyByteBuf;
import cn.dancingsnow.neoecoae.platform.registry.NativeContent;

/** Real FML-world acceptance, isolated from the production mod artifact. */
public final class NativeKeyReuseAcceptance {
    private static final List<String> CHECKS = new ArrayList<>();
    private static boolean finished;
    private static int ticks;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        CHECKS.add(message);
    }

    public static void tick() {
        if (!Boolean.getBoolean("neoecoae.keyReuseAcceptance") || finished || ++ticks < 5) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.worldServerForDimension(0) == null) return;
        finished = true;
        String result = "PASS";
        try { run(); }
        catch (Throwable failure) {
            result = "FAIL " + failure;
            NeoECOAE.LOG.error("KEY_REUSE_ACCEPTANCE_FAILED", failure);
        }
        String side = server.isDedicatedServer() ? "server" : "client";
        try {
            Files.write(java.nio.file.Paths.get("key-reuse-acceptance-" + side + ".txt"),
                (result + "\n" + String.join("\n", CHECKS)).getBytes(StandardCharsets.UTF_8));
        } catch (Exception failure) { throw new RuntimeException(failure); }
        NeoECOAE.LOG.info("KEY_REUSE_ACCEPTANCE {} {} checks", result, CHECKS.size());
        if (server.isDedicatedServer()) server.initiateShutdown();
    }

    private static void run() throws Exception {
        check(KeyReuse.epoch() != null, "local config enabled reuse before acceptance overrides");
        var registries = RegistryAccess.EMPTY;
        ItemStack input = new ItemStack(Items.iron_ingot, 64, 0);
        FluidStack fluid = new FluidStack(FluidRegistry.WATER, 1000);
        check(AEItemStack.create(input) != AEItemStack.create(input), "native AE2 item stack factory remains independent");
        KeyReuse.configure(false);
        AEItemKey oldItem = AEItemKey.of(input);
        AEFluidKey oldFluid = AEFluidKey.of(fluid);
        CompoundTag oldItemTag = oldItem.toTagGeneric(registries);
        CompoundTag oldFluidTag = oldFluid.toTagGeneric(registries);
        ItemStack cellStack = new ItemStack(NativeContent.item("eco_item_storage_cell_16m"));
        ECOStorageCell oldCell = new ECOStorageCell(cellStack, null);
        check(oldCell.insert(oldItem, 4096, Actionable.MODULATE, IActionSource.empty()) == 4096, "old finite inventory inserted");
        NBTTagCompound serializedCell = new NBTTagCompound();
        cellStack.writeToNBT(serializedCell);
        ECOInfiniteStorageData data = ECOInfiniteStorageData.createNew();
        SavedDataInfiniteStorageEngine original = new SavedDataInfiniteStorageEngine(data);
        BigInteger huge = BigInteger.TEN.pow(40);
        original.insert(oldItem, huge, Actionable.MODULATE);
        original.insert(oldFluid, BigInteger.ONE.shiftLeft(100), Actionable.MODULATE);
        UUID receipt = UUID.fromString("c5348bb2-50fa-4d72-868c-1fc7cf663eee");
        original.insertOnce(receipt, oldItem, 17);
        Path file = java.nio.file.Paths.get("key-reuse-old-inventory.nbt");
        NbtIo.writeCompressed(data.save(new CompoundTag(), registries), file);

        KeyReuse.configure(true);
        AEItemKey item = AEItemKey.of(input);
        AEFluidKey water = AEFluidKey.of(fluid);
        check(item == AEItemKey.of(input) && item == AEItemKey.of(Items.iron_ingot), "item factories share an immutable identity");
        check(item != oldItem && item.equals(oldItem) && item.hashCode() == oldItem.hashCode(), "old value equality and hash preserved");
        check(water == AEFluidKey.of(fluid) && water == AEFluidKey.of(FluidRegistry.WATER) && water.equals(oldFluid), "fluid factories share the old value");
        check(item.toTagGeneric(registries).equals(oldItemTag) && water.toTagGeneric(registries).equals(oldFluidTag), "encoded identities unchanged");
        check(AEKey.fromTagGeneric(registries, oldItemTag) == item && AEKey.fromTagGeneric(registries, oldFluidTag) == water, "old key decoders reuse");
        check(GenericStack.readTag(registries, GenericStack.writeTag(registries, new GenericStack(oldItem, 99))).what() == item, "GenericStack decoder reuses");
        RegistryFriendlyByteBuf packet = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), registries);
        try {
            AEKey.writeKey(packet, oldItem);
            AEKey.writeKey(packet, oldFluid);
            check(AEKey.readKey(packet) == item && AEKey.readKey(packet) == water, "packet decoders reuse");
        } finally { packet.release(); }
        IAEItemStack incoming = AEItemStack.create(input);
        incoming.setCraftable(true);
        check(AEKey.fromNative(incoming) == item, "native ingress reuses without copying the source stack");
        check(AEKey.fromNative(appeng.util.item.AEFluidStack.create(fluid)) == water, "native fluid ingress reuses before creating a representative stack");
        IAEItemStack view = item.toNative(5);
        check(view.getStackSize() == 5 && !view.isCraftable(), "native source flags do not leak into the cached template");
        view.setStackSize(90).setCraftable(true);
        check(item.toNative(7).getStackSize() == 7 && !item.toNative(7).isCraftable(), "native views remain independent and mutable");
        item.getReadOnlyStack().stackSize = 0;
        check(item.toStack().stackSize == 1 && input.stackSize == 64, "shared identity and source are protected from mutation");
        ItemStack emptyTag = input.copy();
        emptyTag.setTagCompound(new NBTTagCompound());
        check(!item.equals(AEItemKey.of(emptyTag)) && AEItemKey.of(emptyTag) != AEItemKey.of(emptyTag), "null and empty NBT remain distinct");
        ItemStack variant = new ItemStack(Items.dye, 1, 14);
        check(AEItemKey.of(variant) == AEItemKey.of(variant) && !AEItemKey.of(variant).equals(AEItemKey.of(new ItemStack(Items.dye, 1, 15))), "raw metadata variants remain distinct");
        KeyReuse.invalidate();
        var executor = Executors.newFixedThreadPool(8);
        try {
            var tasks = new ArrayList<java.util.concurrent.Callable<AEItemKey>>();
            for (int i = 0; i < 64; i++) tasks.add(() -> AEItemKey.of(input));
            var results = executor.invokeAll(tasks);
            AEItemKey shared = results.get(0).get();
            for (var value : results) check(value.get() == shared, "concurrent first publication");
        } finally { executor.shutdownNow(); }
        AEItemKey refreshed = AEItemKey.of(input);
        check(item != refreshed && item.equals(refreshed), "reload replaces the cached object while preserving old equality");
        ECOStorageCell reloadedCell = new ECOStorageCell(ItemStack.loadItemStackFromNBT(serializedCell), null);
        check(reloadedCell.extract(refreshed, 4096, Actionable.MODULATE, IActionSource.empty()) == 4096, "old finite cell reload conserves all resources");
        SavedDataInfiniteStorageEngine engine = new SavedDataInfiniteStorageEngine(
            ECOInfiniteStorageData.load(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()), registries));
        check(engine.getAmount(refreshed).toBigInteger().equals(huge.add(BigInteger.valueOf(17))), "10^40 survives disk and real key decoding");
        check(engine.getAmount(AEFluidKey.of(fluid)).toBigInteger().equals(BigInteger.ONE.shiftLeft(100)), "U126 survives disk and real key decoding");
        engine.insertOnce(receipt, refreshed, 17);
        check(engine.getAmount(refreshed).toBigInteger().equals(huge.add(BigInteger.valueOf(17))), "migration retry does not duplicate inventory");
        check(engine.extract(refreshed, 31, Actionable.MODULATE) == 31 && engine.getAmount(oldItem).toBigInteger().equals(huge.subtract(BigInteger.valueOf(14))), "old and refreshed keys share one exact ledger");
        KeyReuse.configure(false);
        check(AEItemKey.of(input) != AEItemKey.of(input) && AEItemKey.of(input).equals(refreshed), "disable restores independent keys with old value access");
        KeyReuse.configure(true);
        check(AEItemKey.of(input) != refreshed && AEItemKey.of(input).equals(refreshed), "reenable publishes a new compatible generation");
    }
}
