package cn.dancingsnow.neoecoae.client;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import appeng.api.config.Settings;
import appeng.client.gui.widgets.GuiImgButton;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadTarget;

/** AE2-styled button with Neo ECO tooltip text. */
public final class PatternUploadButton extends GuiImgButton {

    private static volatile String autoTargetName = "";
    private static volatile int autoTargetKind = -1;
    private static volatile ItemStack autoTargetCircuit;

    private final String titleKey;
    private final String descriptionKey;

    public PatternUploadButton(int x, int y, Settings setting, Enum value, String titleKey, String descriptionKey) {
        super(x, y, setting, value);
        this.titleKey = titleKey;
        this.descriptionKey = descriptionKey;
    }

    public static void setAutoTarget(String name, int kind, ItemStack circuit) {
        autoTargetName = name == null ? "" : name;
        autoTargetKind = kind;
        autoTargetCircuit = circuit == null ? null : circuit.copy();
    }

    public static void clearAutoTarget() {
        autoTargetName = "";
        autoTargetKind = -1;
        autoTargetCircuit = null;
    }

    @Override
    public String getMessage() {
        String message = StatCollector.translateToLocal(this.titleKey) + "\n"
            + StatCollector.translateToLocal(this.descriptionKey);
        if ("gui.neoecoae.pattern_upload.button.auto.tooltip.title".equals(this.titleKey)
            && !autoTargetName.isEmpty()) {
            String displayName = autoTargetName;
            if (StatCollector.canTranslate(displayName)) displayName = StatCollector.translateToLocal(displayName);
            message += "\n" + StatCollector.translateToLocalFormatted(
                "gui.neoecoae.pattern_upload.button.auto.tooltip.target",
                displayName,
                targetKindName(autoTargetKind));
            if (autoTargetCircuit != null) {
                message += "\n" + StatCollector.translateToLocalFormatted(
                    "gui.neoecoae.pattern_upload.button.auto.tooltip.circuit",
                    StatCollector.translateToLocal("gui.neoecoae.pattern_upload.circuit_short") + " "
                        + (autoTargetCircuit.getItemDamage() & 0xFF));
            }
        }
        return message;
    }

    private static String targetKindName(int kind) {
        PatternUploadTarget.Kind[] kinds = PatternUploadTarget.Kind.values();
        if (kind < 0 || kind >= kinds.length) return "";
        switch (kinds[kind]) {
            case GT_CRAFTING_INPUT:
                return StatCollector.translateToLocal("gui.neoecoae.pattern_upload.target.gt_assembly");
            case GT_CRAFTING_INPUT_BUS:
                return StatCollector.translateToLocal("gui.neoecoae.pattern_upload.target.gt_bus");
            case ECO_PATTERN_BUS:
                return StatCollector.translateToLocal("gui.neoecoae.pattern_upload.target.eco");
            case PROGRAMMABLE_HATCH:
                return StatCollector.translateToLocal("gui.neoecoae.pattern_upload.target.programmable");
            case AE2_DUAL_INTERFACE:
                return StatCollector.translateToLocal("gui.neoecoae.pattern_upload.target.ae2_dual");
            case AE2_INTERFACE:
            default:
                return StatCollector.translateToLocal("gui.neoecoae.pattern_upload.target.ae2");
        }
    }
}
