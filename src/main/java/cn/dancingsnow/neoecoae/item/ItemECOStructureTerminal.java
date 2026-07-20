package cn.dancingsnow.neoecoae.item;

import java.util.List;

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
import cn.dancingsnow.neoecoae.gui.mui.NeoEcoGuiData;
import cn.dancingsnow.neoecoae.gui.mui.NeoEcoUiFactory;
import cn.dancingsnow.neoecoae.multiblock.ECOStructureBuilder;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalHostType;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMode;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 1.7.10 port of the non-preview part of the modern structure terminal.
 *
 * <p>
 * Normal right-click opens the configuration terminal. Right-clicking a controller remembers the
 * target; Shift+right-clicking it then performs normal build, mirrored build, or dismantle according
 * to the selected mode.
 * </p>
 */
public class ItemECOStructureTerminal extends Item {

    public static final String TAG_BUILD_LENGTH = "BuildLength";
    public static final String TAG_HOST_TYPE = "HostType";
    public static final String TAG_HOST_TIER = "HostTier";
    public static final String TAG_OPERATION_MODE = "OperationMode";
    public static final String TAG_TARGET_DIMENSION = "TargetDimension";
    public static final String TAG_TARGET_X = "TargetX";
    public static final String TAG_TARGET_Y = "TargetY";
    public static final String TAG_TARGET_Z = "TargetZ";

    public ItemECOStructureTerminal() {
        this.setUnlocalizedName("eco_structure_terminal");
        this.setTextureName(NeoECOAE.MODID + ":structure_terminal");
        this.setCreativeTab(NECreativeTabs.NEO_ECO_AE);
        this.setMaxStackSize(1);
    }

    public static int getGlobalMaxBuildLength() {
        return ECOStructureBuilder.MAX_LENGTH;
    }

    public static int getBuildLength(ItemStack stack) {
        return getBuildLength(stack, getMaxBuildLength(stack));
    }

    public static int getBuildLength(ItemStack stack, int maxLength) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        if (tag == null || !tag.hasKey(TAG_BUILD_LENGTH)) {
            return ECOStructureBuilder.MIN_LENGTH;
        }
        return clampLength(tag.getInteger(TAG_BUILD_LENGTH), maxLength);
    }

    public static void setBuildLength(ItemStack stack, int length) {
        if (stack == null) {
            return;
        }
        NBTTagCompound tag = getOrCreateTag(stack);
        tag.setInteger(TAG_BUILD_LENGTH, clampLength(length, getMaxBuildLength(stack)));
    }

    public static int getMaxBuildLength(ItemStack stack) {
        return ECOStructureBuilder.MAX_LENGTH;
    }

    public static StructureTerminalHostType getHostType(ItemStack stack) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        return tag == null ? StructureTerminalHostType.CRAFTING
            : StructureTerminalHostType.fromName(tag.getString(TAG_HOST_TYPE));
    }

    public static String getHostTier(ItemStack stack) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        if (tag == null || !tag.hasKey(TAG_HOST_TIER)) {
            return "l4";
        }
        String tier = tag.getString(TAG_HOST_TIER);
        return "l6".equals(tier) || "l9".equals(tier) ? tier : "l4";
    }

    public static void setHostTarget(ItemStack stack, StructureTerminalHostType type, String tier) {
        if (stack == null || type == null) {
            return;
        }
        NBTTagCompound tag = getOrCreateTag(stack);
        tag.setString(TAG_HOST_TYPE, type.name());
        tag.setString(TAG_HOST_TIER, normalizeTier(tier));
        tag.removeTag(TAG_TARGET_DIMENSION);
        tag.removeTag(TAG_TARGET_X);
        tag.removeTag(TAG_TARGET_Y);
        tag.removeTag(TAG_TARGET_Z);
        setBuildLength(stack, getBuildLength(stack, getMaxBuildLength(stack)));
    }

    public static void setHostType(ItemStack stack, StructureTerminalHostType type) {
        setHostTarget(stack, type, getHostTier(stack));
    }

    public static void setHostTier(ItemStack stack, String tier) {
        setHostTarget(stack, getHostType(stack), tier);
    }

    public static void setHostTarget(ItemStack stack, TileECOController controller) {
        if (stack == null || controller == null) {
            return;
        }
        setHostTarget(
            stack,
            StructureTerminalHostType.fromSubsystem(controller.getSubsystem()),
            controller.getTier()
                .getId());
    }

    public static StructureTerminalMode getOperationMode(ItemStack stack) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        return tag == null ? StructureTerminalMode.BUILD
            : StructureTerminalMode.fromName(tag.getString(TAG_OPERATION_MODE));
    }

    public static void setOperationMode(ItemStack stack, StructureTerminalMode mode) {
        if (stack == null || mode == null) {
            return;
        }
        getOrCreateTag(stack).setString(TAG_OPERATION_MODE, mode.name());
    }

    public static StructureTerminalMode consumeOperationMode(ItemStack stack) {
        StructureTerminalMode mode = getOperationMode(stack);
        if (stack != null && stack.hasTagCompound()) {
            stack.getTagCompound()
                .removeTag(TAG_OPERATION_MODE);
        }
        return mode;
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

    public static TileECOController findLinkedController(ItemStack stack, EntityPlayer player) {
        if (stack == null || player == null || player.worldObj == null || !stack.hasTagCompound()) {
            return null;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(TAG_TARGET_DIMENSION)
            || tag.getInteger(TAG_TARGET_DIMENSION) != player.worldObj.provider.dimensionId) {
            return null;
        }
        int x = tag.getInteger(TAG_TARGET_X);
        int y = tag.getInteger(TAG_TARGET_Y);
        int z = tag.getInteger(TAG_TARGET_Z);
        if (player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D) > 4096.0D) {
            return null;
        }
        net.minecraft.tileentity.TileEntity tile = player.worldObj.getTileEntity(x, y, z);
        return tile instanceof TileECOController ? (TileECOController) tile : null;
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

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world == null || stack == null || player == null) {
            return stack;
        }
        if (world.isRemote || player.isSneaking()) {
            return stack;
        }
        NeoEcoUiFactory.openHeldItem(player, NeoEcoGuiData.Kind.STRUCTURE_TERMINAL);
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
            player.addChatMessage(new ChatComponentTranslation("chat.neoecoae.structure_terminal.too_far"));
            return true;
        }

        setHostTarget(stack, controller);
        rememberTarget(stack, world, controller);
        if (!player.isSneaking()) {
            NeoEcoUiFactory.openHeldItem(player, NeoEcoGuiData.Kind.STRUCTURE_TERMINAL);
            return true;
        }

        StructureTerminalMode mode = consumeOperationMode(stack);
        if (mode == StructureTerminalMode.DISMANTLE) {
            sendDismantleResult(player, ECOStructureBuilder.dismantle(controller, player));
            return true;
        }
        boolean mirrored = mode == StructureTerminalMode.MIRRORED_BUILD;
        sendBuildResult(player, ECOStructureBuilder.build(controller, player, getBuildLength(stack), mirrored));
        return true;
    }

    private static void sendBuildResult(EntityPlayer player, ECOStructureBuilder.BuildResult result) {
        String error = result.getError();
        if (error != null) {
            player.addChatMessage(new ChatComponentTranslation(buildErrorKey(error)));
            return;
        }
        if (result.getConflicts() > 0 || result.getMissingMaterials() > 0) {
            player.addChatMessage(
                new ChatComponentTranslation(
                    "chat.neoecoae.structure_terminal.plan_blocked",
                    result.getConflicts(),
                    result.getMissingMaterials()));
            return;
        }
        String scan = result.getFormationResult() == null ? ""
            : result.getFormationResult()
                .getMessage();
        player.addChatMessage(
            new ChatComponentTranslation(
                result.isFormed() ? "chat.neoecoae.structure_terminal.built"
                    : "chat.neoecoae.structure_terminal.not_formed",
                result.getPlacedBlocks(),
                scan));
    }

    private static void sendDismantleResult(EntityPlayer player, ECOStructureBuilder.DismantleResult result) {
        if (result.getError() != null) {
            player.addChatMessage(new ChatComponentTranslation(buildErrorKey(result.getError())));
            return;
        }
        String scan = result.isDismantled() && result.getFormationResult() != null ? result.getFormationResult()
            .getMessage() : "";
        player.addChatMessage(
            new ChatComponentTranslation(
                "chat.neoecoae.structure_terminal.dismantled",
                result.getRemovedBlocks(),
                scan));
    }

    private static String buildErrorKey(String error) {
        if ("too far".equals(error) || "too_far".equals(error)) {
            return "chat.neoecoae.structure_terminal.too_far";
        }
        if ("formed".equals(error)) {
            return "chat.neoecoae.structure_terminal.formed";
        }
        if ("not_formed".equals(error)) {
            return "chat.neoecoae.structure_terminal.not_formed_error";
        }
        if ("infinite_locked".equals(error)) {
            return "chat.neoecoae.structure_terminal.dismantle_blocked";
        }
        return "chat.neoecoae.structure_terminal.invalid";
    }

    private static int clampLength(int length, int maxLength) {
        return Math
            .max(ECOStructureBuilder.MIN_LENGTH, Math.min(Math.max(ECOStructureBuilder.MIN_LENGTH, maxLength), length));
    }

    private static String normalizeTier(String tier) {
        return "l6".equals(tier) || "l9".equals(tier) ? tier : "l4";
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
        tooltip.add(EnumChatFormatting.GRAY + "Length: " + getBuildLength(stack));
        tooltip.add(EnumChatFormatting.GRAY + "Mode: " + getOperationMode(stack).name());
        tooltip.add(EnumChatFormatting.DARK_GRAY + "Right-click: open terminal configuration");
        tooltip.add(EnumChatFormatting.DARK_GRAY + "Select host, tier, length, and operation");
        tooltip.add(EnumChatFormatting.DARK_GRAY + "Right-click a controller to select its target");
        tooltip.add(EnumChatFormatting.DARK_GRAY + "Shift+right-click the controller to execute");
        NETooltips.addBlockTooltips(stack, tooltip);
    }
}
