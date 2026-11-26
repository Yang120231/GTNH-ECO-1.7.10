package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.inventory.InventoryCrafting;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;

/** Resolves the native push implementation without loading optional mods or submitting a probe. */
public final class NativeProcessingAccess {

    private enum Contract {
        SCALED,
        DIRECT,
        ORDERED,
        ASSEMBLER,
        ORDERED_ASSEMBLER
    }

    @com.github.bsideup.jabel.Desugar
    record BatchSupport(boolean scaled, boolean direct, boolean assemblerSize, boolean orderedSlots) {}

    private static final BatchSupport SINGLE = new BatchSupport(false, false, false, false);
    private static final Map<String, Contract> DECLARATIONS = new HashMap<>();
    private static volatile ClassValue<BatchSupport> profiles = profiles(Collections.emptyMap());

    private NativeProcessingAccess() {}

    /** Common startup only (IMC or server config). Names refer to classes, not interfaces, globs or packages. */
    public static synchronized boolean declareBatchMedium(String className, boolean direct) {
        if (className == null) return false;
        String name = className.trim();
        if (!name.matches(
            "(?:[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*\\.)+"
                + "[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*"))
            return false;
        Contract mode = direct ? Contract.DIRECT : Contract.SCALED;
        Contract previous = DECLARATIONS.get(name);
        // Conflicting declarations get the conservative first offer, regardless of mod-loading order.
        if (previous == null || previous == Contract.DIRECT && mode == Contract.SCALED) {
            DECLARATIONS.put(name, mode);
            profiles = profiles(new HashMap<>(DECLARATIONS));
        }
        return true;
    }

    static BatchSupport batchSupport(Class<?> type) {
        return profiles.get(type);
    }

    private static ClassValue<BatchSupport> profiles(Map<String, Contract> extra) {
        return new ClassValue<>() {

            @Override
            protected BatchSupport computeValue(Class<?> type) {
                if (type.isInterface() || !ICraftingMedium.class.isAssignableFrom(type)) return SINGLE;
                Class<?> parent = type.getSuperclass();
                BatchSupport base = parent == null ? SINGLE : get(parent);
                Contract contract = builtIn(type.getName());
                boolean declared = contract == null && extra.containsKey(type.getName()) && hasClassPush(type);
                if (declared) contract = extra.get(type.getName());
                if (contract != null) return new BatchSupport(
                    true,
                    contract == Contract.DIRECT,
                    base.assemblerSize() || contract == Contract.ASSEMBLER || contract == Contract.ORDERED_ASSEMBLER,
                    base.orderedSlots() || declared
                        || contract == Contract.ORDERED
                        || contract == Contract.ORDERED_ASSEMBLER);
                if (!base.scaled() || inheritsPush(type)) return base;
                // An override is a new intake contract. Its ordinary push still gets the parent's table layout.
                return new BatchSupport(false, false, base.assemblerSize(), base.orderedSlots());
            }
        };
    }

    private static boolean hasClassPush(Class<?> type) {
        try {
            var push = type.getMethod("pushPattern", ICraftingPatternDetails.class, InventoryCrafting.class);
            return !push.getDeclaringClass()
                .isInterface() && !Modifier.isAbstract(push.getModifiers());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    private static boolean inheritsPush(Class<?> type) {
        try {
            type.getDeclaredMethod("pushPattern", ICraftingPatternDetails.class, InventoryCrafting.class);
            return false;
        } catch (NoSuchMethodException inherited) {
            return true;
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    private static Contract builtIn(String name) {
        return switch (name) {
            case "gregtech.common.tileentities.machines.MTEHatchCraftingInputME" -> Contract.DIRECT;
            case "com.science.gtnl.common.machine.hatch.SuperCraftingInputHatchME" -> Contract.DIRECT;
            case "com.tenkeitech.machine.MTEHatchAutoTemplateInputME" -> Contract.DIRECT;
            case "com.tenkeitech.machine.MTESuperCraftingInputME" -> Contract.DIRECT;
            case "appeng.helpers.DualityInterface" -> Contract.SCALED;
            case "com.science.gtnl.common.machine.multiblock.AssemblerMatrix" -> Contract.ORDERED_ASSEMBLER;
            case "com.science.gtnl.common.block.blocks.tile.TileEntityMEChisel" -> Contract.ASSEMBLER;
            case "com.miaokatze.gtswn.common.assembly.MatrixItemInputBus" -> Contract.ORDERED;
            case "com.miaokatze.gtswn.common.assembly.MatrixFluidInputHatch" -> Contract.ORDERED;
            default -> null;
        };
    }

    // The inherited AE2 push routes through its private iHost, not a subclass's GUI-facing getHost().
    private static final class PushHost {

        static final Field FIELD = field();

        private static Field field() {
            try {
                Field f = DualityInterface.class.getDeclaredField("iHost");
                if (f.getType() != IInterfaceHost.class) return null;
                f.setAccessible(true);
                return f;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                return null;
            }
        }
    }

    static IInterfaceHost pushHost(DualityInterface provider) {
        try {
            return PushHost.FIELD == null ? null : (IInterfaceHost) PushHost.FIELD.get(provider);
        } catch (IllegalAccessException unavailable) {
            return null;
        }
    }

    public static boolean isSendBufferEmpty(DualityInterface provider) {
        try {
            var pending = provider.getWaitingToSend();
            return pending == null || pending.isEmpty();
        } catch (RuntimeException | LinkageError unavailable) {
            // Capacity hint after an accepted push; failure must not invalidate that ownership receipt.
            return false;
        }
    }
}
