package cn.dancingsnow.neoecoae.item;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cn.dancingsnow.neoecoae.client.tooltip.NETooltips;
import cn.dancingsnow.neoecoae.gui.NEGuiIds;
import cn.dancingsnow.neoecoae.storage.domain.ECOStorageDomainData;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Lightweight 1.7.10 recovery terminal. Right-clicking air opens the selector UI; the UI rotates
 * through the domains in the current save data. Shift+right-clicking a formed infinite storage
 * controller adopts the selected domain after the controller performs all server-side safety
 * checks.
 */
public class ItemECOStorageRecoveryTerminal extends Item {

    private static final String TAG_SELECTED_DOMAIN = "SelectedStorageDomain";
    public static final String TAG_TARGET_DIMENSION = "TargetDimension";
    public static final String TAG_TARGET_X = "TargetX";
    public static final String TAG_TARGET_Y = "TargetY";
    public static final String TAG_TARGET_Z = "TargetZ";

    public ItemECOStorageRecoveryTerminal() {
        this.setUnlocalizedName("eco_storage_recovery_terminal");
        this.setTextureName(NeoECOAE.MODID + ":structure_terminal");
        this.setCreativeTab(NECreativeTabs.NEO_ECO_AE);
        this.setMaxStackSize(1);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world == null || world.isRemote || stack == null || player == null) {
            return stack;
        }
        if (player.isSneaking()) {
            return stack;
        }
        player.openGui(NeoECOAE.instance, NEGuiIds.ECO_STORAGE_RECOVERY_TERMINAL, world, 0, 0, 0);
        return stack;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world == null || stack == null || player == null) {
            return false;
        }
        net.minecraft.tileentity.TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileECOController)) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }
        TileECOController controller = (TileECOController) tile;
        if (!controller.isUseableByPlayer(player)) {
            player.addChatMessage(new ChatComponentTranslation("chat.neoecoae.storage.recovery.too_far"));
            return true;
        }
        rememberTarget(stack, world, controller);
        if (!player.isSneaking()) {
            player.openGui(NeoECOAE.instance, NEGuiIds.ECO_STORAGE_RECOVERY_TERMINAL, world, 0, 0, 0);
            return true;
        }
        UUID selected = getSelectedDomain(stack);
        if (selected == null) {
            player.addChatMessage(new ChatComponentTranslation("chat.neoecoae.storage.recovery.select_first"));
            return true;
        }
        String result = controller.adoptRecoveredDomain(selected);
        player.addChatMessage(
            new ChatComponentTranslation("chat.neoecoae.storage.recovery." + result, selected.toString()));
        return true;
    }

    /** Cycles the UUID list on the server and returns the newly selected domain. */
    public static UUID cycleSelectedDomain(ItemStack stack, World world, int delta) {
        if (stack == null || world == null) {
            return null;
        }
        List<UUID> ids = ECOStorageDomainData.get(world)
            .getDomainIds();
        if (ids.isEmpty()) {
            return null;
        }
        UUID selected = getSelectedDomain(stack);
        int current = selected == null ? -1 : ids.indexOf(selected);
        int next;
        if (current < 0) {
            next = delta < 0 ? ids.size() - 1 : 0;
        } else {
            next = (current + delta) % ids.size();
            if (next < 0) {
                next += ids.size();
            }
        }
        UUID result = ids.get(next);
        setSelectedDomain(stack, result);
        return result;
    }

    public static UUID getSelectedDomain(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return null;
        }
        try {
            return UUID.fromString(
                stack.getTagCompound()
                    .getString(TAG_SELECTED_DOMAIN));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void setSelectedDomain(ItemStack stack, UUID domainId) {
        if (stack == null || domainId == null) {
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setString(TAG_SELECTED_DOMAIN, domainId.toString());
    }

    public static void rememberTarget(ItemStack stack, World world, TileECOController controller) {
        if (stack == null || world == null || controller == null) {
            return;
        }
        NBTTagCompound tag = getOrCreateTag(stack);
        tag.setInteger(TAG_TARGET_DIMENSION, world.provider.dimensionId);
        tag.setInteger(TAG_TARGET_X, controller.xCoord);
        tag.setInteger(TAG_TARGET_Y, controller.yCoord);
        tag.setInteger(TAG_TARGET_Z, controller.zCoord);
    }

    public static String getTargetDescription(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return "none";
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(TAG_TARGET_DIMENSION) || !tag.hasKey(TAG_TARGET_X)
            || !tag.hasKey(TAG_TARGET_Y)
            || !tag.hasKey(TAG_TARGET_Z)) {
            return "none";
        }
        return tag.getInteger(TAG_TARGET_DIMENSION) + ":"
            + tag.getInteger(TAG_TARGET_X)
            + ","
            + tag.getInteger(TAG_TARGET_Y)
            + ","
            + tag.getInteger(TAG_TARGET_Z);
    }

    private static NBTTagCompound getOrCreateTag(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        return tag;
    }

    @SideOnly(Side.CLIENT)
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        UUID selected = getSelectedDomain(stack);
        tooltip
            .add(EnumChatFormatting.GRAY + (selected == null ? "No domain selected" : "Selected domain: " + selected));
        tooltip.add(EnumChatFormatting.DARK_GRAY + "Right-click to open the recovery selector");
        tooltip.add(EnumChatFormatting.DARK_GRAY + "Use previous/next to cycle saved UUIDs");
        tooltip.add(EnumChatFormatting.DARK_GRAY + "Shift+right-click a new infinite host to recover");
        NETooltips.addBlockTooltips(stack, tooltip);
    }
}
