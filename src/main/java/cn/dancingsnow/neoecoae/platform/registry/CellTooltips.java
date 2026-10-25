package cn.dancingsnow.neoecoae.platform.registry;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;

/** Native text presentation of the locked ECOStorageCellItem capacity/member tooltip. */
public final class CellTooltips {

    private CellTooltips() {}

    public static void append(NativeStorageCellItem item, ItemStack stack, List<Component> lines) {
        if (ECOInfiniteStorageMember.isMember(stack)) {
            lines.add(
                Component.translatable("tooltip.neoecoae.storage.infinite_member")
                    .withStyle(EnumChatFormatting.LIGHT_PURPLE));
            ECOInfiniteStorageMember.getDomainId(stack)
                .ifPresent(
                    id -> lines.add(
                        Component.literal("UUID: " + id)
                            .withStyle(EnumChatFormatting.AQUA)));
            return;
        }
        var inventory = item.createCellInventory(stack, null);
        lines.add(capacity(inventory.getUsedBytes(), inventory.getTotalBytes(), GuiText.BytesUsed));
        lines.add(capacity(inventory.getStoredItemTypes(), inventory.getTotalItemTypes(), GuiText.Types));
    }

    private static Component capacity(long used, long total, GuiText suffix) {
        NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.ENGLISH);
        return Component.literal(
            EnumChatFormatting.WHITE + numbers.format(used)
                + EnumChatFormatting.GRAY
                + " "
                + GuiText.Of.getLocal()
                + " "
                + EnumChatFormatting.DARK_GREEN
                + (total == Long.MAX_VALUE ? "∞" : numbers.format(total))
                + EnumChatFormatting.GRAY
                + " "
                + suffix.getLocal());
    }
}
