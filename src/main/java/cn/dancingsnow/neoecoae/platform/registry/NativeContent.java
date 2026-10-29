package cn.dancingsnow.neoecoae.platform.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.Upgrades;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;
import cn.dancingsnow.neoecoae.platform.ae2.storage.NativeCellHandler;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cpw.mods.fml.common.registry.GameRegistry;

/** FML registration boundary. Identifiers, capacities and type limits come from the locked NEItems/NECellTypes. */
public final class NativeContent {

    private static final Map<String, Item> ITEMS = new LinkedHashMap<>();
    private static final Map<Item, String> IDS = new IdentityHashMap<>();
    private static final List<NativeStorageCellItem> CELLS = new ArrayList<>();
    public static final CreativeTabs TAB = new CreativeTabs("neoecoae.main") {

        @Override
        public Item getTabIconItem() {
            return ITEMS.get("eco_item_storage_cell_16m");
        }

        @Override
        @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
        public void displayAllReleventItems(List<ItemStack> items) {
            List<ItemStack> content = new ArrayList<>();
            super.displayAllReleventItems(content);
            CreativeContentOrder.sort(content);
            items.addAll(content);
        }
    };

    private NativeContent() {}

    public static void register() {
        if (!ITEMS.isEmpty()) throw new IllegalStateException("ECO content registered twice");
        CoreItemCatalog.registerSimpleItems();
        ECOCellType item = new ECOCellType(Component.translatable("gui.appliedenergistics2.Items"), 315);
        ECOCellType fluid = new ECOCellType(Component.translatable("gui.appliedenergistics2.Fluids"), 25);
        StorageRegistries.CELL_TYPE.register(
            cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation.fromNamespaceAndPath("neoecoae", "items"),
            item);
        StorageRegistries.CELL_TYPE.register(
            cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation.fromNamespaceAndPath("neoecoae", "fluids"),
            fluid);
        String[] sizes = { "16m", "64m", "256m" };
        int index = 0;
        for (StorageTier tier : StorageTier.values()) {
            registerCell(
                "eco_item_storage_cell_" + sizes[index],
                new NativeStorageCellItem(tier, AEKeyType.items(), item));
            registerCell(
                "eco_fluid_storage_cell_" + sizes[index],
                new NativeStorageCellItem(tier, AEKeyType.fluids(), fluid));
            register(
                "eco_computation_cell_" + tier.name()
                    .toLowerCase(java.util.Locale.ROOT),
                new cn.dancingsnow.neoecoae.items.ECOComputationCellItem(tier));
            index++;
        }
        register(
            "eco_infinite_item_cell",
            new cn.dancingsnow.neoecoae.items.ECOInfiniteResourceCellItem(StorageTier.L9, () -> item));
        CoreItemCatalog.checkParity();
        // Recipe-viewer fluid stand-in; outside the source item catalog and every creative tab.
        GameRegistry
            .registerItem(cn.dancingsnow.neoecoae.platform.minecraft.FluidDisplayItem.INSTANCE, "fluid_display");
    }

    public static void connectAe2() {
        NativeCellHandler handler = new NativeCellHandler();
        ECOStorageCells.register(handler);
        AEApi.instance()
            .registries()
            .cell()
            .addCellHandler(handler);
        for (NativeStorageCellItem cell : CELLS) {
            Upgrades.FUZZY.registerItem(new ItemStack(cell), 1);
            Upgrades.INVERTER.registerItem(new ItemStack(cell), 1);
            Upgrades.VOID_OVERFLOW.registerItem(new ItemStack(cell), 1);
        }
    }

    private static void registerCell(String id, NativeStorageCellItem item) {
        register(id, item);
        CELLS.add(item);
    }

    static void register(String id, Item item) {
        if (ITEMS.containsKey(id)) throw new IllegalArgumentException("Duplicate core item: " + id);
        item.setUnlocalizedName(translationName(id))
            .setCreativeTab(TAB);
        GameRegistry.registerItem(item, id);
        ITEMS.put(id, item);
        IDS.put(item, id);
    }

    /** Item adds the {@code item.} prefix; the shipped lang files carry {@code item.neoecoae.<id>.name}. */
    static String translationName(String id) {
        return "neoecoae." + id;
    }

    public static String id(Item item) {
        return IDS.get(item);
    }

    public static Item item(String id) {
        return ITEMS.get(id);
    }

    public static List<NativeStorageCellItem> cells() {
        return Collections.unmodifiableList(CELLS);
    }

    public static Map<String, Item> items() {
        return Collections.unmodifiableMap(ITEMS);
    }
}
