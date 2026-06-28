package cn.dancingsnow.neoecoae.client.tooltip;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class NETooltips {

    private static final int MAX_LINES = 16;

    private NETooltips() {}

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void addBlockTooltips(ItemStack stack, java.util.List tooltip) {
        String keyBase = stack.getUnlocalizedName() + ".tooltip";
        if (!hasKey(keyBase + ".summary") && !hasKey(keyBase + ".line.0")) {
            return;
        }

        if (hasKey(keyBase + ".summary")) {
            tooltip.add(translate(keyBase + ".summary"));
        }

        if (!isShiftDown()) {
            tooltip.add(translate("tooltip.neoecoae.hold_shift"));
            return;
        }

        for (int i = 0; i < MAX_LINES; i++) {
            String key = keyBase + ".line." + i;
            if (!hasKey(key)) {
                break;
            }
            tooltip.add(translate(key));
        }
    }

    private static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private static boolean hasKey(String key) {
        return StatCollector.canTranslate(key);
    }

    private static String translate(String key) {
        return StatCollector.translateToLocal(key)
            .replace('&', '\u00a7');
    }
}
