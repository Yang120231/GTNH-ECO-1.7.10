package cn.dancingsnow.neoecoae.items;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.impl.storage.ECOInfiniteResourceCell;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;
import cn.dancingsnow.neoecoae.platform.ae2.storage.ConfigInventory;
import cn.dancingsnow.neoecoae.platform.ae2.storage.ISaveProvider;
import cn.dancingsnow.neoecoae.platform.ae2.storage.IUpgradeInventory;
import cn.dancingsnow.neoecoae.platform.ae2.storage.UpgradeInventories;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.registry.NativeStorageCellItem;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;

/**
 * ECO infinite base resource storage matrix: an unbounded supply of a small fixed set of base
 * resources (water, cobblestone and lava).
 *
 * <p>
 * The cell is hard-locked to those keys: {@link #isBlackListed} rejects every other key, so no
 * partition configuration can widen it. It is also permanently excluded from the ECO infinite
 * storage domain via {@link #isInfiniteStorageEligible()}, because migrating an unbounded supply
 * into the domain would erase the distinction between "stored" and "always available".
 */
public class ECOInfiniteResourceCellItem extends NativeStorageCellItem {

    /** The only keys this cell accepts or supplies. */
    private static final List<AEKey> LOCKED_KEYS = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
        AEFluidKey.of(net.minecraftforge.fluids.FluidRegistry.WATER),
        AEItemKey.of(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.cobblestone)),
        AEFluidKey.of(net.minecraftforge.fluids.FluidRegistry.LAVA));

    /** One slot per locked key, so the cell reads as partition-configured out of the box. */
    private static final int CONFIG_SLOTS = LOCKED_KEYS.size();

    /**
     * Installed upgrade slots. Cards are inert here (nothing is stored, so fuzzy/void/inverter have
     * nothing to act on) but the slots are accepted for parity with the other ECO matrices.
     */
    private static final int UPGRADE_SLOTS = 4;

    public ECOInfiniteResourceCellItem(StorageTierDescriptor tier, Supplier<ECOCellType> cellType) {
        super(tier, AEKeyType.items(), cellType.get(), false);
    }

    @Override
    public long getBytes() {
        return Long.MAX_VALUE;
    }

    @Override
    public long getBytesPerType() {
        return bytesPerType(getTier());
    }

    @Override
    public double getIdleDrain() {
        return getTier().getStorageIdleDrain();
    }

    /** Matches the base cell's per-type cost (1 GiB per type at L9) without overflow. */
    private static int bytesPerType(StorageTierDescriptor tier) {
        return 1 << (12 + tier.getTier());
    }

    public static List<AEKey> lockedKeys() {
        return LOCKED_KEYS;
    }

    public static boolean isLockedKey(@Nullable AEKey what) {
        return what != null && LOCKED_KEYS.contains(what);
    }

    @Override
    public Set<AEKeyType> getKeyTypes() {
        return cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(AEKeyType.items(), AEKeyType.fluids());
    }

    @Override
    public boolean isBlackListed(ItemStack cellStack, AEKey what) {
        return !isLockedKey(what);
    }

    @Override
    public ConfigInventory getBusinessConfigInventory(ItemStack stack) {
        return new ConfigInventory(getConfigAEInventory(stack));
    }

    @Override
    public appeng.tile.inventory.IAEStackInventory getConfigAEInventory(ItemStack stack) {
        return new cn.dancingsnow.neoecoae.platform.ae2.storage.FilteredCellConfig(
            stack,
            CONFIG_SLOTS,
            ECOInfiniteResourceCellItem::isLockedKey);
    }

    @Override
    public IUpgradeInventory getBusinessUpgrades(ItemStack stack) {
        return UpgradeInventories.forItem(stack, UPGRADE_SLOTS);
    }

    @Override
    public net.minecraft.inventory.IInventory getUpgradesInventory(ItemStack stack) {
        return new appeng.items.contents.CellUpgrades(stack, UPGRADE_SLOTS);
    }

    @Override
    public ECOInfiniteResourceCell createCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        return new ECOInfiniteResourceCell(stack, host);
    }

    @Override
    protected void appendTooltip(ItemStack stack, List<Component> lines) {
        if (ECOInfiniteStorageMember.isMember(stack)) {
            lines.add(
                Component.translatable("tooltip.neoecoae.storage.infinite_member")
                    .withStyle(EnumChatFormatting.LIGHT_PURPLE));
            return;
        }
        lines.add(
            Component.translatable("tooltip.neoecoae.infinite_resource.contents")
                .withStyle(EnumChatFormatting.AQUA));
        lines.add(
            Component.translatable("tooltip.neoecoae.infinite_resource.unbounded")
                .withStyle(EnumChatFormatting.GRAY));
        lines.add(
            Component.translatable("tooltip.neoecoae.infinite_resource.sink")
                .withStyle(EnumChatFormatting.DARK_GRAY, EnumChatFormatting.ITALIC));
    }
}
