package cn.dancingsnow.neoecoae.crafting.upload;

import net.minecraft.item.ItemStack;

import appeng.api.networking.IGrid;

/**
 * A live upload destination resolved by a platform adapter. Implementations read the real host on every call, so a
 * changed circuit, recipe mode or disassembled structure is seen at submit time instead of from a stale snapshot.
 */
public interface PatternUploadTarget {

    enum Kind {

        ECO_PATTERN_DOMAIN("eco", 0),
        PROGRAMMABLE_HATCH("programmable", 1),
        GT_CRAFTING_INPUT("gt_assembly", 2),
        GT_CRAFTING_INPUT_BUS("gt_bus", 2),
        GTNL_SUPER_INPUT("gtnl", 2),
        AE2_DUAL_INTERFACE("ae2_dual", 3),
        AE2_INTERFACE("ae2", 3),
        ECO_WORKSTATION("eco_workstation", 0),
        GTNL_ASSEMBLER_MATRIX("assembler_matrix", 1),
        CRAFTING_ASSEMBLER("crafting_assembler", 2);

        public final String key;
        public final int priority;

        Kind(String key, int priority) {
            this.key = key;
            this.priority = priority;
        }

        public String translationKey() {
            return "gui.neoecoae.pattern_upload.target." + key;
        }
    }

    Kind kind();

    /** Translation key or literal name of the host. */
    String name();

    /** Literal text shown after the translated {@link #name()}, such as GT's ghost circuit " [1]"; may be empty. */
    default String nameSuffix() {
        return "";
    }

    ItemStack icon();

    int dimension();

    int x();

    int y();

    int z();

    /** Stable position key including the part side, used for sorting and duplicate host detection. */
    String positionKey();

    /** Whether a saved binding position still names this target after its canonical position moved. */
    default boolean ownsPosition(String positionKey) {
        return positionKey().equals(positionKey);
    }

    /** Actual shared inventory ownership; aliases of the same inventory are one upload object. */
    default Object storageIdentity() {
        return null;
    }

    /** Still on {@code grid}, powered and in the same world slot. */
    boolean isPresent(IGrid grid);

    PatternSlotAccess slots();

    MatchResult match(PatternContext context);
}
