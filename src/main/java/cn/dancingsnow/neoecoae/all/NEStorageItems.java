package cn.dancingsnow.neoecoae.all;

import java.text.NumberFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.client.render.model.ModernIconRegistrar;
import cn.dancingsnow.neoecoae.client.tooltip.NETooltips;
import cn.dancingsnow.neoecoae.energy.ECOEnergyProfile;
import cn.dancingsnow.neoecoae.storage.core.ECOAmount;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageBackend;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageSnapshot;
import cn.dancingsnow.neoecoae.storage.item.ECOStorageCellAccess;
import cn.dancingsnow.neoecoae.storage.item.ECOStorageCellMetadata;
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
    public static final Item ecoComputationCellL4 = computationCell(
        "eco_computation_cell_l4",
        "CE4",
        ECOEnergyProfile.computationBytes(cn.dancingsnow.neoecoae.tile.ECOControllerTier.L4));
    public static final Item ecoComputationCellL6 = computationCell(
        "eco_computation_cell_l6",
        "CE6",
        ECOEnergyProfile.computationBytes(cn.dancingsnow.neoecoae.tile.ECOControllerTier.L6));
    public static final Item ecoComputationCellL9 = computationCell(
        "eco_computation_cell_l9",
        "CE9",
        ECOEnergyProfile.computationBytes(cn.dancingsnow.neoecoae.tile.ECOControllerTier.L9));

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
        register(ecoComputationCellL4, "eco_computation_cell_l4");
        register(ecoComputationCellL6, "eco_computation_cell_l6");
        register(ecoComputationCellL9, "eco_computation_cell_l9");
    }

    private static Item simpleItem(String id) {
        return new TooltipItem(id);
    }

    private static Item storageCell(String id, String tier) {
        return new ECOStorageCellItem(id, tier);
    }

    private static Item computationCell(String id, String tier, long bytes) {
        return new ECOComputationCellItem(id, tier, bytes);
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

        private static final int COLOR_LOW = 0x45F05A;
        private static final int COLOR_MEDIUM = 0xFFEA4A;
        private static final int COLOR_HIGH = 0xFF9D32;
        private static final int COLOR_FULL = 0xFF5151;
        private static final int COLOR_INFINITE = 0xD8A8FF;
        private static final EnumChatFormatting TOTAL_COLOR = EnumChatFormatting.BLUE;

        private final String tier;
        private final long bytes;
        private final NumberFormat numberFormat;
        @SideOnly(Side.CLIENT)
        private IIcon baseIcon;
        @SideOnly(Side.CLIENT)
        private IIcon lightMaskIcon;

        ECOStorageCellItem(String id, String tier) {
            super(id);
            this.tier = tier;
            this.bytes = bytesForTier(tier);
            this.numberFormat = NumberFormat.getIntegerInstance(Locale.US);
            setMaxStackSize(1);
        }

        public String getTier() {
            return tier;
        }

        @Override
        public long getDisplayBytes(ItemStack stack) {
            return this.bytes;
        }

        @SideOnly(Side.CLIENT)
        @Override
        public void registerIcons(IIconRegister register) {
            this.baseIcon = register.registerIcon(this.getIconString());
            this.lightMaskIcon = register.registerIcon(NeoECOAE.MODID + ":eco_cell_status_light");
            this.itemIcon = this.baseIcon;
        }

        @SideOnly(Side.CLIENT)
        @Override
        public boolean requiresMultipleRenderPasses() {
            return true;
        }

        @SideOnly(Side.CLIENT)
        @Override
        public int getRenderPasses(int metadata) {
            return 2;
        }

        @SideOnly(Side.CLIENT)
        @Override
        public IIcon getIconFromDamageForRenderPass(int metadata, int pass) {
            return pass == 1 && this.lightMaskIcon != null ? this.lightMaskIcon : this.baseIcon;
        }

        @SideOnly(Side.CLIENT)
        @Override
        public int getColorFromItemStack(ItemStack stack, int pass) {
            return pass == 1 ? getStorageLightColor(stack) : 0xFFFFFF;
        }

        @Override
        public void onCreated(ItemStack stack, net.minecraft.world.World world, EntityPlayer player) {
            ECOStorageCellAccess.writeCellIdentity(stack, "universal", this.tier);
        }

        @SideOnly(Side.CLIENT)
        @Override
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public void addInformation(ItemStack stack, EntityPlayer player, java.util.List tooltip, boolean advanced) {
            ECOStorageBackend backend = ECOStorageCellAccess.load(stack);
            ECOStorageSnapshot snapshot = backend.snapshot();
            long used = snapshot.getUsed()
                .toLongSaturated();
            tooltip.add(
                colorForUsage(used, this.getDisplayBytes(stack)) + formatAmount(snapshot.getUsed())
                    + EnumChatFormatting.GRAY
                    + " / "
                    + TOTAL_COLOR
                    + formatLong(this.getDisplayBytes(stack))
                    + EnumChatFormatting.GRAY
                    + " "
                    + translate("tooltip.neoecoae.storage.bytes_used"));
            tooltip.add(
                EnumChatFormatting.GREEN + formatLong(snapshot.getTypeCount())
                    + EnumChatFormatting.GRAY
                    + " "
                    + translate("tooltip.neoecoae.storage.types"));
            if (ECOStorageCellMetadata.hasNonPortableState(stack)) {
                tooltip.add(EnumChatFormatting.LIGHT_PURPLE + translate("tooltip.neoecoae.storage.infinite_locked"));
            }
            NETooltips.addBlockTooltips(stack, tooltip);
        }

        private static long bytesForTier(String tier) {
            if ("64M".equals(tier)) {
                return ECOEnergyProfile.storageBytes(cn.dancingsnow.neoecoae.tile.ECOControllerTier.L6);
            }
            if ("256M".equals(tier)) {
                return ECOEnergyProfile.storageBytes(cn.dancingsnow.neoecoae.tile.ECOControllerTier.L9);
            }
            return ECOEnergyProfile.storageBytes(cn.dancingsnow.neoecoae.tile.ECOControllerTier.L4);
        }

        private String formatAmount(ECOAmount amount) {
            return this.numberFormat.format(amount.toBigInteger());
        }

        private String formatLong(long value) {
            return this.numberFormat.format(value);
        }

        private int getStorageLightColor(ItemStack stack) {
            if (ECOStorageCellMetadata.hasNonPortableState(stack)) {
                return COLOR_INFINITE;
            }
            long total = this.getDisplayBytes(stack);
            long used;
            try {
                used = ECOStorageCellAccess.getUsed(stack)
                    .toLongSaturated();
            } catch (RuntimeException ignored) {
                return COLOR_LOW;
            }
            return usageColor(used, total);
        }

        private static int usageColor(long used, long total) {
            if (total <= 0L) {
                return COLOR_LOW;
            }
            if (used >= total) {
                return COLOR_FULL;
            }
            double ratio = (double) used / (double) total;
            if (ratio >= 0.90D) {
                return COLOR_FULL;
            }
            if (ratio >= 0.75D) {
                return COLOR_HIGH;
            }
            if (ratio >= 0.50D) {
                return COLOR_MEDIUM;
            }
            return COLOR_LOW;
        }

        private static EnumChatFormatting colorForUsage(long used, long total) {
            int color = usageColor(used, total);
            if (color == COLOR_FULL) {
                return EnumChatFormatting.RED;
            }
            if (color == COLOR_HIGH) {
                return EnumChatFormatting.GOLD;
            }
            if (color == COLOR_MEDIUM) {
                return EnumChatFormatting.YELLOW;
            }
            return EnumChatFormatting.GREEN;
        }

        private static String translate(String key) {
            return StatCollector.translateToLocal(key)
                .replace('&', '\u00a7');
        }
    }

    public static class ECOComputationCellItem extends TooltipItem {

        private static final EnumChatFormatting TIER_L4 = EnumChatFormatting.YELLOW;
        private static final EnumChatFormatting TIER_L6 = EnumChatFormatting.AQUA;
        private static final EnumChatFormatting TIER_L9 = EnumChatFormatting.LIGHT_PURPLE;
        private static final EnumChatFormatting VALUE = EnumChatFormatting.BLUE;
        private static final String[] MODEL_TEXTURES = { NeoECOAE.MODID + ":block/compute/drive/cell_inside_back",
            NeoECOAE.MODID + ":block/compute/drive/cell_north_a", NeoECOAE.MODID + ":block/compute/drive/cell_north_b",
            NeoECOAE.MODID + ":block/compute/drive/cell_north_c", NeoECOAE.MODID + ":block/compute/drive/cell_side_a",
            NeoECOAE.MODID + ":block/compute/drive/cell_side_b", NeoECOAE.MODID + ":block/compute/drive/cell_side_c",
            NeoECOAE.MODID + ":block/compute/drive/cell_south", NeoECOAE.MODID + ":block/compute/drive/cell_top" };

        private final String tier;
        private final long bytes;
        private final NumberFormat numberFormat;
        @SideOnly(Side.CLIENT)
        private final Map<String, IIcon> modelIcons = new HashMap<String, IIcon>();

        ECOComputationCellItem(String id, String tier, long bytes) {
            super(id);
            this.tier = tier;
            this.bytes = bytes;
            this.numberFormat = NumberFormat.getIntegerInstance(Locale.US);
            setMaxStackSize(1);
        }

        public String getTier() {
            return this.tier;
        }

        public long getBytes() {
            return this.bytes;
        }

        @SideOnly(Side.CLIENT)
        @Override
        public void registerIcons(IIconRegister register) {
            super.registerIcons(register);
        }

        @SideOnly(Side.CLIENT)
        private void registerModelIcons(IIconRegister register) {
            this.modelIcons.clear();
            for (String texture : MODEL_TEXTURES) {
                this.modelIcons.put(texture, register.registerIcon(ModernIconRegistrar.toLegacyIconName(texture)));
            }
        }

        @SideOnly(Side.CLIENT)
        public Map<String, IIcon> getModelIcons() {
            return Collections.unmodifiableMap(this.modelIcons);
        }

        @SideOnly(Side.CLIENT)
        @Override
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public void addInformation(ItemStack stack, EntityPlayer player, java.util.List tooltip, boolean advanced) {
            tooltip.add(
                EnumChatFormatting.GRAY + translate("tooltip.neoecoae.computation_cell.prefix")
                    + VALUE
                    + this.numberFormat.format(this.bytes)
                    + EnumChatFormatting.GRAY
                    + " "
                    + translate("tooltip.neoecoae.computation_cell.bytes"));
            tooltip.add(
                tierColor() + this.tier
                    + EnumChatFormatting.GRAY
                    + " "
                    + translate("tooltip.neoecoae.computation_cell.tier"));
            NETooltips.addBlockTooltips(stack, tooltip);
        }

        private EnumChatFormatting tierColor() {
            if ("CE9".equals(this.tier)) {
                return TIER_L9;
            }
            if ("CE6".equals(this.tier)) {
                return TIER_L6;
            }
            return TIER_L4;
        }

        private static String translate(String key) {
            return StatCollector.translateToLocal(key)
                .replace('&', '\u00a7');
        }
    }

    @SideOnly(Side.CLIENT)
    public static void registerComputationCellModelIcons(IIconRegister register) {
        ((ECOComputationCellItem) ecoComputationCellL4).registerModelIcons(register);
        ((ECOComputationCellItem) ecoComputationCellL6).registerModelIcons(register);
        ((ECOComputationCellItem) ecoComputationCellL9).registerModelIcons(register);
    }
}
