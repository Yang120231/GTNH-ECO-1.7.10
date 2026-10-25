import com.github.bsideup.jabel.Desugar;
import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.item.IItemHandler;
import com.cleanroommc.modularui.value.sync.BigIntSyncValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import net.minecraftforge.fluids.IFluidTank;

import java.math.BigInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Compile-only probe of the pinned MUI2 API; not a playable screen. */
public final class MUI2BridgeProbe implements IGuiHolder<PosGuiData> {
    @Desugar
    public record Config(
        IntSupplier progress,
        Supplier<BigInteger> exactBytes,
        BooleanSupplier overclocked,
        Runnable toggleOverclock,
        IItemHandler inventory,
        IFluidTank tank
    ) {}

    private final Config config;

    public MUI2BridgeProbe(Config config) {
        this.config = config;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        // The same deterministic keys must be registered by both peers.
        sync.syncValue("progress", new IntSyncValue(config.progress()));
        sync.syncValue("exact_bytes", new BigIntSyncValue(config.exactBytes(), null));
        sync.syncValue("overclock", new BooleanSyncValue(config.overclocked()));
        sync.registerSyncedAction("toggle_overclock", false, true, buffer -> config.toggleOverclock().run());

        return ModularPanel.defaultPanel("neoecoae_probe", 253, 214)
            .child(new TextWidget<>("MUI2 adapter API probe").pos(7, 7))
            .child(new ItemSlot().slot(new ModularSlot(config.inventory(), 0)).pos(7, 28))
            .child(new FluidSlot().syncHandler(new FluidSlotSyncHandler(config.tank())).pos(29, 28))
            .child(new ButtonWidget<>().size(18).pos(51, 28).onMousePressed(mouseButton -> {
                sync.callSyncedAction("toggle_overclock");
                return true;
            }))
            .child(SlotGroupWidget.playerInventory(7, true));
    }
}
