package cn.dancingsnow.neoecoae.platform.forge;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidRegistry;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;
import cn.dancingsnow.neoecoae.platform.ae2.storage.NativeStorageInventory;
import cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.IOUtilities;
import cn.dancingsnow.neoecoae.platform.nbt.NbtAccounter;
import cn.dancingsnow.neoecoae.platform.nbt.NbtIo;
import cn.dancingsnow.neoecoae.platform.registry.NativeContent;
import cn.dancingsnow.neoecoae.platform.registry.NativeStorageCellItem;

/** Only registered with -Dneoecoae.portingAcceptance=true, for isolated development-world acceptance. */
public final class PortingAcceptanceCommand extends CommandBase {

    private static final UUID DOMAIN = UUID
        .nameUUIDFromBytes("neoecoae1710:porting-acceptance-domain".getBytes(StandardCharsets.UTF_8));
    private static final UUID RECEIPT = UUID
        .nameUUIDFromBytes("neoecoae1710:porting-acceptance-receipt".getBytes(StandardCharsets.UTF_8));
    private static final BigInteger EXACT = BigInteger.TEN.pow(40)
        .add(BigInteger.valueOf(48));

    @Override
    public String getCommandName() {
        return "neoecoae_port_check";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "neoecoae_port_check storage|reload|foundation|storage-host";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] arguments) {
        if (arguments.length != 1 || !("storage".equals(arguments[0]) || "reload".equals(arguments[0])
            || "foundation".equals(arguments[0])
            || "storage-host".equals(arguments[0]))) throw new IllegalArgumentException(getCommandUsage(sender));
        WorldServer world = MinecraftServer.getServer()
            .worldServerForDimension(0);
        if ("foundation".equals(arguments[0])) {
            PortingFoundationFixture.start(world, sender);
            return;
        }
        if ("storage-host".equals(arguments[0])) {
            PortingStorageHostFixture.start(world, sender);
            return;
        }
        if ("storage".equals(arguments[0])) {
            checkCells();
            checkInfiniteResourceCell();
            checkRecipes(world);
            checkMui2Bindings();
        }
        checkDomain(world, "storage".equals(arguments[0]));
        sender.addChatMessage(
            new ChatComponentText(
                "NEOECOAE_PORT_ACCEPTANCE_OK mode=" + arguments[0]
                    + " cells="
                    + NativeContent.cells()
                        .size()
                    + " channels=items,fluids exact="
                    + EXACT
                    + " receipt="
                    + RECEIPT));
    }

    private static void checkRecipes(WorldServer world) {
        for (NativeStorageCellItem cell : NativeContent.cells()) {
            java.util.List<ItemStack> ingredients = cn.dancingsnow.neoecoae.platform.recipe.StorageRecipes
                .disassemble(cell);
            require(ingredients.size() == 2, "Imported disassembly has housing and component");
            net.minecraft.inventory.InventoryCrafting table = new net.minecraft.inventory.InventoryCrafting(
                new net.minecraft.inventory.Container() {

                    @Override
                    public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
                        return true;
                    }
                },
                3,
                3);
            for (int slot = 0; slot < ingredients.size(); slot++)
                table.setInventorySlotContents(slot, ingredients.get(slot));
            ItemStack output = net.minecraft.item.crafting.CraftingManager.getInstance()
                .findMatchingRecipe(table, world);
            require(
                output != null && output.getItem() == cell && output.stackSize == 1,
                "Native assembly recipe for " + NativeContent.id(cell));
        }
        cn.dancingsnow.neoecoae.NeoECOAE.LOG.info("NEOECOAE_PORT_RECIPES_OK assembly=6 disassembly=6");
    }

    private static void checkMui2Bindings() {
        cn.dancingsnow.neoecoae.platform.ui.Mui2Bindings bindings = new cn.dancingsnow.neoecoae.platform.ui.Mui2Bindings();
        require(
            bindings.longValue("energy", () -> 123L)
                .getLongValue() == 123L,
            "MUI2 long binding on dedicated server");
        require(
            EXACT.equals(
                bindings.exactValue("exact_bytes", () -> EXACT)
                    .getValue()),
            "MUI2 BigInteger binding on dedicated server");
        require(
            bindings.booleanValue("formed", () -> true)
                .getValue(),
            "MUI2 boolean binding on dedicated server");
        require(
            bindings.schema()
                .size() == 3,
            "MUI2 deterministic binding schema");
        cn.dancingsnow.neoecoae.NeoECOAE.LOG.info("NEOECOAE_PORT_MUI2_BINDINGS_OK long=true exact=true boolean=true");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void checkCells() {
        require(
            NativeContent.cells()
                .size() == 9,
            "Native cell registration count");
        for (NativeStorageCellItem item : NativeContent.cells()) {
            ItemStack stack = new ItemStack(item);
            AEKey key = item.getKeyType() == AEKeyType.items() ? AEItemKey.of(net.minecraft.init.Items.iron_ingot)
                : AEFluidKey.of(FluidRegistry.WATER);
            NativeStorageInventory inventory = (NativeStorageInventory) AEApi.instance()
                .registries()
                .cell()
                .getCellInventory(
                    stack,
                    null,
                    key.getType()
                        .nativeChannel());
            require(inventory != null, "Native AE2 cell handler for " + NativeContent.id(item));
            IAEStack inserted = inventory.injectItems(key.toNative(4096), Actionable.MODULATE, new BaseActionSource());
            require(inserted == null, "Complete native insertion");
            IAEStack simulated = inventory.extractItems(key.toNative(128), Actionable.SIMULATE, new BaseActionSource());
            require(simulated != null && simulated.getStackSize() == 128, "Native extraction simulation");
            require(
                inventory.business()
                    .getAvailableStacks()
                    .get(key) == 4096,
                "Simulation leaves inventory unchanged");
            IAEStack extracted = inventory.extractItems(key.toNative(128), Actionable.MODULATE, new BaseActionSource());
            require(extracted != null && extracted.getStackSize() == 128, "Native extraction");
            NBTTagCompound serialized = stack.writeToNBT(new NBTTagCompound());
            ItemStack restored = ItemStack.loadItemStackFromNBT(serialized);
            NativeStorageInventory reloaded = (NativeStorageInventory) AEApi.instance()
                .registries()
                .cell()
                .getCellInventory(
                    restored,
                    null,
                    key.getType()
                        .nativeChannel());
            require(
                reloaded != null && reloaded.business()
                    .getAvailableStacks()
                    .get(key) == 3968,
                "Cell item-NBT reload");
            ECOInfiniteStorageMember.beginMigration(restored, DOMAIN);
            require(
                reloaded.business()
                    .getAvailableStacks()
                    .isEmpty(),
                "Sealed source not exposed");
            require(
                reloaded.business()
                    .getMigrationAmount(key) == 3968,
                "Sealed source retains quantity");
            require(
                reloaded.extractItems(key.toNative(1), Actionable.MODULATE, new BaseActionSource()) == null,
                "Sealed source cannot extract");
            require(
                AEApi.instance()
                    .registries()
                    .cell()
                    .getCellInventory(
                        new ItemStack(item),
                        null,
                        key.getType() == AEKeyType.items() ? appeng.api.storage.StorageChannel.FLUIDS
                            : appeng.api.storage.StorageChannel.ITEMS)
                    == null,
                "Wrong native channel rejected");
        }
    }

    private static void checkDomain(WorldServer world, boolean initialize) {
        ECOInfiniteStorageEngine engine = ECOInfiniteStorageDomains.acquire(world, DOMAIN);
        try {
            AEKey key = AEItemKey.of(net.minecraft.init.Items.iron_ingot);
            if (initialize && engine.isEmpty()) {
                require(
                    engine.insert(key, EXACT.subtract(BigInteger.valueOf(31)), Actionable.MODULATE)
                        .equals(EXACT.subtract(BigInteger.valueOf(31))),
                    "Exact domain insertion");
                require(engine.insertOnce(RECEIPT, key, 31) == 31, "Migration receipt insertion");
            }
            require(engine.hasMigrationReceipt(RECEIPT), "Domain receipt persisted");
            require(engine.insertOnce(RECEIPT, key, 31) == 31, "Migration retry recognized");
            require(
                engine.getAmount(key)
                    .toBigInteger()
                    .equals(EXACT),
                "Exact domain quantity and receipt idempotence");
            require(engine.extract(key, 7, Actionable.SIMULATE) == 7, "Domain extraction simulation");
            require(
                engine.getAmount(key)
                    .toBigInteger()
                    .equals(EXACT),
                "Domain simulation leaves quantity unchanged");
            require(
                engine.commit()
                    .successful(),
                "Domain commit");
            NativeWorldStorage.save(MinecraftServer.getServer());
            IOUtilities.waitUntilIOWorkerComplete();
            Path snapshot = NativeWorldStorage.root(MinecraftServer.getServer())
                .resolve("data")
                .resolve("neoecoae_infinite_" + DOMAIN + "_v3.dat");
            try {
                ECOInfiniteStorageData recovered = ECOInfiniteStorageData.load(
                    NbtIo.readCompressed(snapshot, NbtAccounter.create(16L * 1024 * 1024))
                        .getCompound("data"),
                    RegistryAccess.EMPTY);
                require(
                    recovered.getAmount(key)
                        .toBigInteger()
                        .equals(EXACT),
                    "Real world snapshot contents");
                require(recovered.hasMigrationReceipt(RECEIPT), "Real world snapshot receipt");
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Cannot reload real world snapshot", e);
            }
        } finally {
            ECOInfiniteStorageDomains.release(MinecraftServer.getServer(), DOMAIN);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void checkInfiniteResourceCell() {
        ItemStack stack = new ItemStack(NativeContent.item("eco_infinite_item_cell"));
        BaseActionSource source = new BaseActionSource();
        java.util.Map<AEKey, cn.dancingsnow.neoecoae.crafting.amount.ExactAmount> exact = new java.util.HashMap<>();
        for (AEKey key : cn.dancingsnow.neoecoae.items.ECOInfiniteResourceCellItem.lockedKeys()) {
            var channel = key.getType()
                .nativeChannel();
            NativeStorageInventory inventory = (NativeStorageInventory) AEApi.instance()
                .registries()
                .cell()
                .getCellInventory(stack, null, channel);
            require(inventory != null, "Infinite resource native channel " + channel);
            require(
                inventory.injectItems(key.toNative(Long.MAX_VALUE), Actionable.MODULATE, source) == null,
                "Locked base resource sink");
            require(
                inventory.extractItems(key.toNative(Long.MAX_VALUE), Actionable.MODULATE, source)
                    .getStackSize() == Long.MAX_VALUE,
                "Locked base resource unbounded source");
            require(
                !inventory.business()
                    .isInfiniteStorageEligible(),
                "Unbounded supply excluded from domain migration");
            ((cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountSource) inventory.business())
                .neoecoae$visitExactAmounts(exact::put);

            NativeStorageCellItem finiteItem = NativeContent.cells()
                .stream()
                .filter(item -> item.getKeyType() == key.getType())
                .findFirst()
                .get();
            NativeStorageInventory finite = new NativeStorageInventory(new ItemStack(finiteItem), null, channel);
            finite.injectItems(key.toNative(64), Actionable.MODULATE, source);
            for (boolean reverse : new boolean[] { false, true }) {
                var network = new appeng.me.storage.NetworkInventoryHandler(finiteItem.getStackType(), null);
                network.addNewStorage(reverse ? inventory : finite);
                network.addNewStorage(reverse ? finite : inventory);
                // A freshly constructed native handler uses zero as its unvisited sentinel.
                int iteration = appeng.util.IterationCounter.fetchNewId();
                if (iteration == 0) iteration = appeng.util.IterationCounter.fetchNewId();
                appeng.api.storage.data.IItemList listing = network.getAvailableItems(channel.createList(), iteration);
                require(
                    ((IAEStack) listing.findPrecise(key.toNative(1))).getStackSize() == Long.MAX_VALUE,
                    "Native network aggregate saturates in both orders");
            }
            var left = new appeng.me.storage.NetworkInventoryHandler(finiteItem.getStackType(), null);
            var right = new appeng.me.storage.NetworkInventoryHandler(finiteItem.getStackType(), null);
            var nested = new appeng.util.item.NetworkItemList(left, channel::createList);
            var ordinary = channel.createList();
            ordinary.add(key.toNative(64));
            var unbounded = channel.createList();
            unbounded.add(key.toNative(Long.MAX_VALUE));
            nested.addNetworkItems(left, ordinary);
            nested.addNetworkItems(right, unbounded);
            require(
                ((IAEStack) nested.buildFinalItemList()
                    .findPrecise(key.toNative(1))).getStackSize() == Long.MAX_VALUE,
                "Native cross-network merge saturates after filtering");
        }
        require(
            exact.size() == 3 && exact.values()
                .stream()
                .allMatch(value -> value.infinite()),
            "Only the three locked base resources are explicitly unbounded");
        NativeStorageInventory items = new NativeStorageInventory(stack, null, appeng.api.storage.StorageChannel.ITEMS);
        AEKey iron = AEItemKey.of(net.minecraft.init.Items.iron_ingot);
        require(
            items.injectItems(iron.toNative(64), Actionable.MODULATE, source)
                .getStackSize() == 64,
            "Unlisted resource rejected by hard lock");
        ECOInfiniteStorageMember.beginMigration(stack, DOMAIN);
        for (AEKey key : cn.dancingsnow.neoecoae.items.ECOInfiniteResourceCellItem.lockedKeys()) {
            var inventory = new NativeStorageInventory(
                stack,
                null,
                key.getType()
                    .nativeChannel());
            require(
                inventory.extractItems(key.toNative(1), Actionable.MODULATE, source) == null,
                "Sealed unbounded supply unavailable");
        }
        cn.dancingsnow.neoecoae.NeoECOAE.LOG
            .info("NEOECOAE_PORT_INFINITE_RESOURCE_OK keys=3 channels=items,fluids native_network_saturation=true");
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new IllegalStateException("Porting acceptance failed: " + description);
    }
}
