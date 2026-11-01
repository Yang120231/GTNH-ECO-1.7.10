package cn.dancingsnow.neoecoae.platform.registry;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEStackType;
import appeng.items.contents.CellConfig;
import appeng.items.contents.CellConfigLegacy;
import appeng.items.contents.CellUpgrades;
import appeng.util.item.AEFluidStackType;
import appeng.util.item.AEItemStackType;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;
import cn.dancingsnow.neoecoae.platform.ae2.storage.ConfigInventory;
import cn.dancingsnow.neoecoae.platform.ae2.storage.IUpgradeInventory;

/** FML item and native cell-workbench boundary; all inventory policy stays in ECOStorageCell. */
public class NativeStorageCellItem extends Item implements IBasicECOCellItem {

    private final StorageTierDescriptor tier;
    private final AEKeyType type;
    private final ECOCellType cellType;

    public NativeStorageCellItem(StorageTierDescriptor tier, AEKeyType type, ECOCellType cellType) {
        this.tier = tier;
        this.type = type;
        this.cellType = cellType;
        setMaxStackSize(1);
    }

    /** Avoid translating the base key before vanilla appends its legacy .name suffix. */
    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return net.minecraft.util.StatCollector.translateToLocal(getUnlocalizedName(stack) + ".name");
    }

    @Override
    public net.minecraft.item.EnumRarity getRarity(ItemStack stack) {
        return CoreItemCatalog.rarity(this);
    }

    @Override
    public StorageTierDescriptor getTier() {
        return tier;
    }

    @Override
    public AEKeyType getKeyType() {
        return type;
    }

    @Override
    public long getBytes() {
        return tier.getStorageTotalBytes();
    }

    @Override
    public double getIdleDrain() {
        return tier.getStorageIdleDrain();
    }

    @Override
    public long getBytesPerType() {
        return 1L << (12 + tier.getTier());
    }

    @Override
    public int getTotalTypes() {
        return cellType.typeCount();
    }

    @Override
    public ECOCellType getCellType() {
        return cellType;
    }

    public cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell createCellInventory(ItemStack stack,
        cn.dancingsnow.neoecoae.platform.ae2.storage.ISaveProvider host) {
        return new cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell(stack, host);
    }

    protected void appendTooltip(ItemStack stack,
        java.util.List<cn.dancingsnow.neoecoae.platform.minecraft.Component> lines) {
        cn.dancingsnow.neoecoae.platform.registry.CellTooltips.append(this, stack, lines);
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public void addInformation(ItemStack stack, net.minecraft.entity.player.EntityPlayer player, java.util.List lines,
        boolean advanced) {
        java.util.List<cn.dancingsnow.neoecoae.platform.minecraft.Component> translated = new java.util.ArrayList<>();
        appendTooltip(stack, translated);
        for (cn.dancingsnow.neoecoae.platform.minecraft.Component text : translated) lines.add(
            text.nativeText()
                .getFormattedText());
    }

    @Override
    public boolean isEditable(ItemStack stack) {
        return true;
    }

    @Override
    public IInventory getUpgradesInventory(ItemStack stack) {
        return new CellUpgrades(stack, 2);
    }

    @Override
    public appeng.tile.inventory.IAEStackInventory getConfigAEInventory(ItemStack stack) {
        return new CellConfig(stack);
    }

    @Override
    public IInventory getConfigInventory(ItemStack stack) {
        return new CellConfigLegacy(getConfigAEInventory(stack), getStackType());
    }

    @Override
    public IAEStackType<?> getStackType() {
        return type == AEKeyType.items() ? AEItemStackType.ITEM_STACK_TYPE : AEFluidStackType.FLUID_STACK_TYPE;
    }

    public IUpgradeInventory getBusinessUpgrades(ItemStack stack) {
        return new IUpgradeInventory((CellUpgrades) getUpgradesInventory(stack));
    }

    public ConfigInventory getBusinessConfigInventory(ItemStack stack) {
        return new ConfigInventory(getConfigAEInventory(stack));
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack stack) {
        String mode = stack.hasTagCompound() ? stack.getTagCompound()
            .getString("neoecoae_fuzzy") : "";
        try {
            return FuzzyMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            return FuzzyMode.IGNORE_ALL;
        }
    }

    @Override
    public void setFuzzyMode(ItemStack stack, FuzzyMode mode) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        stack.getTagCompound()
            .setString("neoecoae_fuzzy", mode.name());
    }

    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    private net.minecraft.util.IIcon[] icons;

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public void registerIcons(net.minecraft.client.renderer.texture.IIconRegister register) {
        icons = cn.dancingsnow.neoecoae.platform.render.ItemModelLayers.icons(NativeContent.id(this), register);
        itemIcon = icons[0];
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public boolean requiresMultipleRenderPasses() {
        return true;
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public int getRenderPasses(int metadata) {
        return icons == null ? 3 : icons.length;
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public net.minecraft.util.IIcon getIconFromDamageForRenderPass(int metadata, int pass) {
        return icons[Math.min(pass, icons.length - 1)];
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public int getColorFromItemStack(ItemStack stack, int pass) {
        // The infinite resource cell has no tier-light layer, so its status light is layer 1.
        int statusPass = this instanceof cn.dancingsnow.neoecoae.items.ECOInfiniteResourceCellItem ? 1 : 2;
        if (pass != statusPass) return 0xffffff;
        return createCellInventory(stack, null).getStatus()
            .getStateColor();
    }

    /**
     * Sneak-use disassembly. Clicking a block also ends here: an item with no {@code onItemUse} falls through to the
     * air-use packet. {@code onItemUseFirst} is deliberately not used, because on 1.7.10 a {@code true} from the
     * client suppresses the block-placement packet, so the server never runs it.
     * <p>
     * The returned stack must stay identical and unchanged. {@code ItemInWorldManager.tryUseItem} writes any changed
     * result back into the held slot (and {@code null} throws there), and in creative mode it restores the original
     * stack size, which would leave the disk in place after its parts were handed out.
     */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, net.minecraft.world.World world,
        net.minecraft.entity.player.EntityPlayer player) {
        disassemble(stack, world, player);
        return stack;
    }

    /** Native inventory/drop operations implementing the locked item's alternate-use disassembly contract. */
    private boolean disassemble(ItemStack stack, net.minecraft.world.World world,
        net.minecraft.entity.player.EntityPlayer player) {
        if (!player.isSneaking() || player.getCurrentEquippedItem() != stack || stack.stackSize != 1) return false;
        if (cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(stack)) return false;
        java.util.List<ItemStack> outputs = cn.dancingsnow.neoecoae.platform.recipe.StorageRecipes.disassemble(this);
        if (outputs.isEmpty()) return false;
        if (world.isRemote) return true;
        cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell inventory = new cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell(
            stack,
            null);
        if (!inventory.getAvailableStacks()
            .isEmpty()) {
            player
                .addChatMessage(new net.minecraft.util.ChatComponentTranslation("tooltip.neoecoae.storage.empty_only"));
            return false;
        }
        IInventory upgrades = getUpgradesInventory(stack);
        for (int slot = 0; slot < upgrades.getSizeInventory(); slot++) {
            ItemStack upgrade = upgrades.getStackInSlot(slot);
            if (upgrade != null) outputs.add(upgrade.copy());
        }
        // The disk keeps the held slot while outputs are added so none of them lands there; the slot is then
        // cleared directly. The stack object itself is left untouched (see onItemRightClick).
        int held = player.inventory.currentItem;
        for (ItemStack output : outputs)
            if (!player.inventory.addItemStackToInventory(output)) player.dropPlayerItemWithRandomChoice(output, false);
        player.inventory.setInventorySlotContents(held, null);
        player.inventory.markDirty();
        // The packet handler syncs the inventory with isChangingQuantityOnly set, which drops the output slots.
        player.inventoryContainer.detectAndSendChanges();
        return true;
    }
}
