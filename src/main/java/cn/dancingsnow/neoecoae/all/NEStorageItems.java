package cn.dancingsnow.neoecoae.all;

import java.text.NumberFormat;
import java.util.Locale;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import appeng.api.storage.StorageChannel;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.client.tooltip.NETooltips;
import cn.dancingsnow.neoecoae.storage.core.ECOAmount;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageBackend;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageSnapshot;
import cn.dancingsnow.neoecoae.storage.item.ECOStorageCellAccess;
import cn.dancingsnow.neoecoae.storage.item.IECOStorageMatrixItem;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class NEStorageItems {

    public static final Item ecoCellComponent16M = simpleItem("eco_cell_component_16m");
    public static final Item ecoCellComponent64M = simpleItem("eco_cell_component_64m");
    public static final Item ecoCellComponent256M = simpleItem("eco_cell_component_256m");
    public static final Item ecoInfiniteCellComponent = simpleItem("eco_infinite_cell_component");
    public static final Item ecoItemCellHousing = simpleItem("eco_item_cell_housing");
    public static final Item ecoItemStorageCell16M = storageCell("eco_item_storage_cell_16m", "16M");
    public static final Item ecoItemStorageCell64M = storageCell("eco_item_storage_cell_64m", "64M");
    public static final Item ecoItemStorageCell256M = storageCell("eco_item_storage_cell_256m", "256M");

    private NEStorageItems() {}

    public static void register() {
        register(ecoCellComponent16M, "eco_cell_component_16m");
        register(ecoCellComponent64M, "eco_cell_component_64m");
        register(ecoCellComponent256M, "eco_cell_component_256m");
        register(ecoInfiniteCellComponent, "eco_infinite_cell_component");
        register(ecoItemCellHousing, "eco_item_cell_housing");
        register(ecoItemStorageCell16M, "eco_item_storage_cell_16m");
        register(ecoItemStorageCell64M, "eco_item_storage_cell_64m");
        register(ecoItemStorageCell256M, "eco_item_storage_cell_256m");
    }

    private static Item simpleItem(String id) {
        return new TooltipItem(id);
    }

    private static Item storageCell(String id, String tier) {
        return new ECOStorageCellItem(id, tier);
    }

    private static void register(Item item, String id) {
        GameRegistry.registerItem(item, id);
    }

    private static class TooltipItem extends Item {

        TooltipItem(String id) {
            setUnlocalizedName(id);
            setTextureName(NeoECOAE.MODID + ":" + id);
            setCreativeTab(NECreativeTabs.NEO_ECO_AE);
        }

        @SideOnly(Side.CLIENT)
        @Override
        @SuppressWarnings("rawtypes")
        public void addInformation(ItemStack stack, EntityPlayer player, java.util.List tooltip, boolean advanced) {
            NETooltips.addBlockTooltips(stack, tooltip);
        }
    }

    public static class ECOStorageCellItem extends TooltipItem implements IECOStorageMatrixItem {

        private final String tier;
        private final long bytes;
        private final NumberFormat numberFormat;

        ECOStorageCellItem(String id, String tier) {
            super(id);
            this.tier = tier;
            this.bytes = bytesForTier(tier);
            this.numberFormat = NumberFormat.getIntegerInstance(Locale.US);
            setMaxStackSize(1);
        }

        public String getChannel() {
            return "item";
        }

        public String getTier() {
            return tier;
        }

        @Override
        public StorageChannel getStorageChannel(ItemStack stack) {
            return StorageChannel.ITEMS;
        }

        @Override
        public long getDisplayBytes(ItemStack stack) {
            return this.bytes;
        }

        @Override
        public void onCreated(ItemStack stack, net.minecraft.world.World world, EntityPlayer player) {
            ECOStorageCellAccess.writeCellIdentity(stack, "item", this.tier);
        }

        @SideOnly(Side.CLIENT)
        @Override
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public void addInformation(ItemStack stack, EntityPlayer player, java.util.List tooltip, boolean advanced) {
            ECOStorageBackend backend = ECOStorageCellAccess.load(stack);
            ECOStorageSnapshot snapshot = backend.snapshot();
            tooltip.add(
                EnumChatFormatting.GRAY + formatAmount(snapshot.getUsed()) + " / "
                    + EnumChatFormatting.GREEN
                    + formatLong(this.getDisplayBytes(stack))
                    + EnumChatFormatting.GRAY
                    + " "
                    + translate("tooltip.neoecoae.storage.bytes_used"));
            tooltip.add(
                EnumChatFormatting.GREEN + formatLong(snapshot.getTypeCount()) + EnumChatFormatting.GRAY + " "
                    + translate("tooltip.neoecoae.storage.types"));
            NETooltips.addBlockTooltips(stack, tooltip);
        }

        private static long bytesForTier(String tier) {
            if ("64M".equals(tier)) {
                return 64L * 1024L * 1024L;
            }
            if ("256M".equals(tier)) {
                return 256L * 1024L * 1024L;
            }
            return 16L * 1024L * 1024L;
        }

        private String formatAmount(ECOAmount amount) {
            return this.numberFormat.format(amount.toBigInteger());
        }

        private String formatLong(long value) {
            return this.numberFormat.format(value);
        }

        private static String translate(String key) {
            return StatCollector.translateToLocal(key)
                .replace('&', '\u00a7');
        }
    }
}
