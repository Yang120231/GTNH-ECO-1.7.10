package cn.dancingsnow.neoecoae.gui.mui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.utils.item.PlayerMainInvWrapper;
import com.cleanroommc.modularui.value.ObjectValue;
import com.cleanroommc.modularui.value.sync.ByteArraySyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Unit;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import appeng.core.localization.ButtonToolTips;
import appeng.util.ReadableNumberConverter;
import cn.dancingsnow.neoecoae.Config;
import cn.dancingsnow.neoecoae.crafting.cooling.ECOCoolingRecipe;
import cn.dancingsnow.neoecoae.crafting.cooling.ECOCoolingRecipes;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadSession;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadSessions;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadTarget;
import cn.dancingsnow.neoecoae.gui.computation.ComputationHostSnapshot;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingHostSnapshot;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostSnapshot;
import cn.dancingsnow.neoecoae.gui.storage.StorageInterfaceSnapshot;
import cn.dancingsnow.neoecoae.item.ItemECOStorageRecoveryTerminal;
import cn.dancingsnow.neoecoae.item.ItemECOStructureTerminal;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalHostType;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMode;
import cn.dancingsnow.neoecoae.network.NEPatternUploadNetwork;
import cn.dancingsnow.neoecoae.storage.domain.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.tile.TileCraftingHatch;
import cn.dancingsnow.neoecoae.tile.TileCraftingPatternBus;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cn.dancingsnow.neoecoae.tile.TileECOInterface;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class NeoEcoPanels {

    private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.US);
    private static final int HOST_TITLE = 0xFF3F3D52;
    private static final int TEXT = 0xFFD6D0E0;
    private static final int MUTED = 0xFFAAA4B2;
    private static final int VALUE = 0xFF8377FF;
    private static final int USED = 0xFF00FC00;
    private static final int STORAGE_PROGRESS = 0xFF26A6BD;
    private static final int STORAGE_ITEM = 0xFFF89737;
    private static final int INFINITE_TEXT = 0xFFCA6CFF;
    private static final int INFINITE_STATUS = 0xFFE0B8FF;
    private static final int GOOD = 0xFF6CFFA0;
    private static final int WARN = 0xFFFFD65A;
    private static final int BAD = 0xFFFF6A75;
    private static final int BLUE = 0xFF3FD6FF;
    private static final int PANEL = 0xFF665F6D;
    private static final int PANEL_ALT = 0xFF201E27;
    private static final int PANEL_OUTER = 0xFF17141E;
    private static final int PANEL_MIDDLE = 0xFF2B2834;
    private static final int PANEL_EDGE = 0xFFC9C3D6;
    private static final int BYTES_IN_K = 1024;
    private static final long BYTES_IN_M = BYTES_IN_K * 1024L;
    private static final long BYTES_IN_G = BYTES_IN_M * 1024L;
    private static final long BYTES_IN_T = BYTES_IN_G * 1024L;
    private static final BigInteger BIG_BYTES_IN_K = BigInteger.valueOf(BYTES_IN_K);
    private static final String[] EXPANDED_BYTE_UNITS = { "", "K", "M", "G", "T", "P", "E", "Z", "Y" };
    private static final ThreadLocal<DecimalFormat> COMPACT_DECIMAL = ThreadLocal
        .withInitial(() -> new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US)));

    private NeoEcoPanels() {}

    static ModularPanel build(NeoEcoGuiData data, PanelSyncManager syncManager, UISettings settings) {
        settings.canInteractWith(player -> NeoEcoUiFactory.INSTANCE.canInteractWith(player, data));
        switch (data.getKind()) {
            case STORAGE_CONTROLLER:
                return storageController(data, syncManager);
            case STORAGE_PRIORITY:
                return storagePriorityController(data, syncManager);
            case COMPUTATION_CONTROLLER:
                return computationController(data, syncManager);
            case CRAFTING_CONTROLLER:
                return craftingController(data, syncManager);
            case STORAGE_INTERFACE:
                return storageInterface(data, syncManager);
            case CRAFTING_PATTERN_BUS:
                return craftingPatternBus(data, syncManager);
            case CRAFTING_HATCH:
                return craftingHatch(data, syncManager);
            case STRUCTURE_TERMINAL:
                return structureTerminal(data, syncManager);
            case STORAGE_RECOVERY_TERMINAL:
                return storageRecoveryTerminal(data, syncManager);
            case PATTERN_UPLOAD:
                return patternUpload(data, syncManager);
            default:
                throw new IllegalArgumentException("Unsupported Neo ECO UI: " + data.getKind());
        }
    }

    private static ModularPanel storageController(NeoEcoGuiData data, PanelSyncManager syncManager) {
        TileECOController controller = tile(data, TileECOController.class);
        SnapshotState<StorageHostSnapshot> state = snapshot(
            syncManager,
            "storage_state",
            StorageHostSnapshot.EMPTY,
            () -> StorageHostSnapshot.create(controller),
            StorageHostSnapshot::write,
            StorageHostSnapshot::read,
            10);
        bindPlayerInventory(syncManager, data.getPlayer());

        String energyLabel = StatCollector.translateToLocal("gui.neoecoae.storage_ui.energy_storage") + ": ";

        ModularSlot componentSlot = new ModularSlot(new InvWrapper(controller), 0) {

            @Override
            public boolean canTakeStack(net.minecraft.entity.player.EntityPlayer player) {
                return controller.canTakeInfiniteStorageComponent() && super.canTakeStack(player);
            }
        }.filter(stack -> controller.isItemValidForSlot(0, stack))
            .singletonSlotGroup(0);

        ModularPanel panel = panel("storage_controller", 344, 232);
        panel.child(hostTitle(() -> hostDisplayTitle("storage", state.get().tier), 8, 8, 242));
        panel.child(
            dynamic(() -> formedLabel(state.get().formed), 220, 8, 96)
                .color(() -> state.get().formed ? 0xFF1A6A3A : 0xFF8A1A2A)
                .textAlign(Alignment.CenterRight));

        panel.child(section(6, 24, 176, 108));
        panel.child(lang("gui.neoecoae.storage_ui.energy_monitor", 14, 32).color(TEXT));
        ParentWidget<?> energyRow = new ParentWidget<>().pos(14, 45)
            .size(166, 12);
        energyRow.child(dynamic(() -> energyLabel, 0, 0, 90).color(MUTED));
        energyRow.child(
            dynamic(() -> energyUsage(state.get()).usedText, 0, 0, 24)
                .left(() -> estimatedTextWidth(energyLabel) + 2, Unit.Measure.PIXEL)
                .color(
                    () -> storageValueColor(
                        energyLong(state.get().energyStored),
                        energyLong(state.get().energyCapacity))));
        energyRow
            .child(
                dynamic(() -> "/", 0, 0, 8)
                    .left(
                        () -> estimatedTextWidth(energyLabel) + estimatedTextWidth(energyUsage(state.get()).usedText)
                            + 4,
                        Unit.Measure.PIXEL)
                    .color(MUTED));
        energyRow.child(
            dynamic(() -> energyUsage(state.get()).maxText, 0, 0, 28)
                .left(
                    () -> estimatedTextWidth(energyLabel) + estimatedTextWidth(energyUsage(state.get()).usedText)
                        + estimatedTextWidth("/")
                        + 6,
                    Unit.Measure.PIXEL)
                .color(VALUE));
        energyRow.child(
            dynamic(() -> "AE", 0, 0, 16)
                .left(
                    () -> estimatedTextWidth(energyLabel) + estimatedTextWidth(energyUsage(state.get()).usedText)
                        + estimatedTextWidth("/")
                        + estimatedTextWidth(energyUsage(state.get()).maxText)
                        + 8,
                    Unit.Measure.PIXEL)
                .color(MUTED));
        panel.child(energyRow);
        panel.child(lang("gui.neoecoae.storage_ui.item_storage", 14, 62).color(STORAGE_ITEM));
        panel.child(
            lang("gui.neoecoae.storage_ui.types", 14, 76).color(MUTED)
                .setEnabledIf(widget -> !isInfiniteStorage(state.get())));
        panel.child(
            new HostProgressWidget(() -> ratio(state.get().usedTypes, state.get().totalTypes), () -> STORAGE_PROGRESS)
                .pos(44, 77)
                .size(36, 9)
                .setEnabledIf(widget -> !isInfiniteStorage(state.get())));
        panel.child(
            dynamic(() -> ae2Amount(state.get().usedTypes), 84, 76, 26)
                .color(() -> storageValueColor(state.get().usedTypes, state.get().totalTypes))
                .textAlign(Alignment.CenterRight)
                .setEnabledIf(widget -> !isInfiniteStorage(state.get())));
        panel.child(
            dynamic(() -> "/", 113, 76, 6).color(MUTED)
                .textAlign(Alignment.Center)
                .setEnabledIf(widget -> !isInfiniteStorage(state.get())));
        panel.child(
            dynamic(() -> ae2Amount(state.get().totalTypes), 122, 76, 52).color(VALUE)
                .setEnabledIf(widget -> !isInfiniteStorage(state.get())));
        panel.child(
            infiniteStorageMetric(
                () -> ae2Amount(state.get().usedTypes),
                "gui.neoecoae.storage_ui.types",
                76,
                () -> storageValueColor(state.get().usedTypes, Long.MAX_VALUE))
                    .setEnabledIf(widget -> isInfiniteStorage(state.get())));
        panel.child(
            lang("gui.neoecoae.storage_ui.bytes", 14, 89).color(MUTED)
                .setEnabledIf(widget -> !isInfiniteStorage(state.get())));
        panel.child(
            new HostProgressWidget(() -> ratio(state.get().usedBytes, state.get().totalBytes), () -> STORAGE_PROGRESS)
                .pos(44, 90)
                .size(36, 9)
                .setEnabledIf(widget -> !isInfiniteStorage(state.get())));
        panel.child(
            dynamic(() -> ae2Amount(state.get().preciseUsedBytes), 84, 89, 26)
                .color(() -> storageValueColor(state.get().usedBytes, state.get().totalBytes))
                .textAlign(Alignment.CenterRight)
                .setEnabledIf(widget -> !isInfiniteStorage(state.get())));
        panel.child(
            dynamic(() -> "/", 113, 89, 6).color(MUTED)
                .textAlign(Alignment.Center)
                .setEnabledIf(widget -> !isInfiniteStorage(state.get())));
        panel.child(
            dynamic(() -> ae2Amount(state.get().totalBytes), 122, 89, 52).color(VALUE)
                .setEnabledIf(widget -> !isInfiniteStorage(state.get())));
        panel.child(
            infiniteStorageMetric(
                () -> fitStorageAmount(state.get().preciseUsedBytes, 62),
                "gui.neoecoae.storage_ui.bytes",
                89,
                () -> storageValueColor(state.get().usedBytes, Long.MAX_VALUE))
                    .setEnabledIf(widget -> isInfiniteStorage(state.get())));

        panel.child(section(186, 24, 156, 200));
        panel.child(darkInset(194, 40, 141, 169));
        panel.child(
            lang("gui.neoecoae.storage_ui.system_load", 192, 28).color(TEXT)
                .width(144)
                .textAlign(Alignment.Center));
        panel.child(
            new StorageGaugeWidget(() -> storageProgress(state.get()), () -> storageGaugeColor(state.get()))
                .pos(202, 52)
                .size(32, 143));
        panel.child(
            dynamic(
                () -> isInfiniteStorage(state.get()) ? "\u221e"
                    : percent(state.get().usedBytes, state.get().totalBytes),
                202,
                197,
                32).color(() -> isInfiniteStorage(state.get()) ? INFINITE_TEXT : storageTextColor(state.get()))
                    .textAlign(Alignment.Center));
        panel.child(
            dynamic(
                () -> StatCollector.translateToLocal("gui.neoecoae.storage_ui.current_load") + ": "
                    + percent(state.get().usedBytes, state.get().totalBytes),
                242,
                57,
                88).color(TEXT));
        panel.child(
            dynamic(
                () -> StatCollector.translateToLocal("gui.neoecoae.storage_ui.max_load") + ": "
                    + (isInfiniteStorage(state.get()) ? "MAX" : percent(maxMatrixLoad(state.get()))),
                242,
                72,
                88).color(() -> isInfiniteStorage(state.get()) ? INFINITE_STATUS : WARN));
        panel.child(
            dynamic(
                () -> StatCollector.translateToLocal("gui.neoecoae.storage_ui.status") + ": "
                    + storageStatus(state.get()),
                242,
                87,
                88).color(() -> isInfiniteStorage(state.get()) ? INFINITE_STATUS : storageStatusColor(state.get())));
        panel.child(
            dynamic(
                () -> StatCollector.translateToLocal("gui.neoecoae.storage_ui.idle_matrices") + ": "
                    + idleMatrices(state.get()),
                242,
                102,
                88).color(MUTED));
        panel.child(lang("gui.neoecoae.common.inventory", 13, 136).color(HOST_TITLE));
        panel.child(playerInventory(13, 147));
        ParentWidget<?> infiniteComponentSlot = new ParentWidget<>().pos(306, 184)
            .size(18, 18)
            .background(NeoEcoTextures.SLOT);
        infiniteComponentSlot.child(
            new ItemSlot().slot(componentSlot)
                .pos(0, 0)
                .size(18, 18)
                .background(new Rectangle().color(0x00000000))
                .overlay(InfiniteSlotBorderDrawable.INSTANCE)
                .addTooltipLine(IKey.lang("gui.neoecoae.storage_ui.infinite_component")));
        panel.child(infiniteComponentSlot);
        panel.child(
            iconButton(
                GuiTextures.GEAR,
                () -> NeoEcoUiFactory.openTile(data.getPlayer(), NeoEcoGuiData.Kind.STORAGE_PRIORITY, controller),
                () -> false).pos(319, 5)
                    .size(18, 18)
                    .addTooltipLine(IKey.dynamic(() -> "Priority: " + state.get().priority)));
        return panel;
    }

    private static ModularPanel storagePriorityController(NeoEcoGuiData data, PanelSyncManager syncManager) {
        TileECOController controller = tile(data, TileECOController.class);
        SnapshotState<StorageHostSnapshot> state = snapshot(
            syncManager,
            "storage_priority_state",
            StorageHostSnapshot.EMPTY,
            () -> StorageHostSnapshot.create(controller),
            StorageHostSnapshot::write,
            StorageHostSnapshot::read,
            5);
        ModularPanel panel = panel("storage_priority", 344, 160);
        panel.child(hostTitle(() -> hostDisplayTitle("storage", state.get().tier), 8, 8, 260));
        panel.child(section(40, 30, 264, 102));
        panel.child(label("AE2 storage priority", 54, 42));
        panel.child(
            dynamic(() -> Integer.toString(state.get().priority), 54, 66, 236).scale(1.5F)
                .textAlign(Alignment.Center));
        panel.child(
            serverButton("-10", () -> controller.setPriority(controller.getPriority() - 10)).pos(54, 98)
                .size(52, 20));
        panel.child(
            serverButton("-1", () -> controller.setPriority(controller.getPriority() - 1)).pos(112, 98)
                .size(52, 20));
        panel.child(
            serverButton("+1", () -> controller.setPriority(controller.getPriority() + 1)).pos(180, 98)
                .size(52, 20));
        panel.child(
            serverButton("+10", () -> controller.setPriority(controller.getPriority() + 10)).pos(238, 98)
                .size(52, 20));
        panel.child(
            serverButton(
                "<",
                () -> NeoEcoUiFactory.openTile(data.getPlayer(), NeoEcoGuiData.Kind.STORAGE_CONTROLLER, controller))
                    .pos(319, 7)
                    .size(18, 18));
        return panel;
    }

    private static IWidget storageOverview(SnapshotState<StorageHostSnapshot> state, ModularSlot componentSlot) {
        ParentWidget<?> page = page();
        page.child(section(0, 0, 182, 142));
        page.child(section(188, 0, 186, 142));
        page.child(label("Host", 8, 8));
        page.child(dynamic(() -> "Mode: " + state.get().hostMode, 8, 27, 164));
        page.child(
            dynamic(
                () -> "Drives: " + state.get().formedDriveCount + " / " + state.get().requiredDriveCount,
                8,
                43,
                164));
        page.child(dynamic(() -> "All L9: " + yesNo(state.get().allDrivesL9), 8, 59, 164));
        page.child(dynamic(() -> "Priority: " + state.get().priority, 8, 75, 164));
        page.child(
            new ItemSlot().slot(componentSlot)
                .pos(8, 100));
        page.child(dynamic(() -> "x" + state.get().infiniteComponentCount, 30, 105, 136));

        page.child(label("Capacity", 196, 8));
        page.child(
            dynamic(
                () -> "Bytes: " + big(state.get().preciseUsedBytes) + " / " + number(state.get().totalBytes),
                196,
                27,
                170));
        page.child(
            dynamic(
                () -> "Types: " + number(state.get().usedTypes) + " / " + number(state.get().totalTypes),
                196,
                43,
                170));
        page.child(dynamic(() -> "Matrices: " + state.get().matrixCells.size(), 196, 59, 170));
        page.child(dynamic(() -> "Large stacks: " + state.get().hugeStacks.size(), 196, 75, 170));
        page.child(
            dynamic(
                () -> state.get().canTakeInfiniteComponent ? "Component unlocked"
                    : "Component locked while storage is in use",
                196,
                100,
                170).color(() -> state.get().canTakeInfiniteComponent ? GOOD : BAD));
        return page;
    }

    private static IWidget storageMatrices(SnapshotState<StorageHostSnapshot> state) {
        ListWidget<IWidget, ?> list = new ListWidget<>().pos(0, 0)
            .size(374, 142)
            .padding(3)
            .background(new Rectangle().color(PANEL));
        list.child(label("Row / Column   Tier   Mode   Bytes   Types", 2, 0));
        for (int i = 0; i < 256; i++) {
            final int index = i;
            TextWidget<?> row = dynamic(() -> {
                if (index >= state.get().matrixCells.size()) return "";
                StorageHostSnapshot.MatrixCell cell = state.get().matrixCells.get(index);
                if (!cell.hasCell) return String.format("%02d / %02d   <empty>", cell.row + 1, cell.column + 1);
                return String.format(
                    "%02d / %02d   %-3s   %-12s   %s/%s B   %s/%s types",
                    cell.row + 1,
                    cell.column + 1,
                    cell.tier,
                    cell.mode,
                    number(cell.usedBytes),
                    number(cell.totalBytes),
                    number(cell.usedTypes),
                    number(cell.totalTypes));
            }, 0, 0, 356).height(14)
                .setEnabledIf(widget -> index < state.get().matrixCells.size());
            list.child(row);
        }
        return list;
    }

    private static IWidget storageContents(SnapshotState<StorageHostSnapshot> state) {
        ParentWidget<?> page = page();
        page.child(label("Storage channels", 3, 2));
        ListWidget<IWidget, ?> types = new ListWidget<>().pos(0, 18)
            .size(182, 124)
            .padding(3)
            .background(new Rectangle().color(PANEL));
        for (int i = 0; i < 32; i++) {
            final int index = i;
            types.child(dynamic(() -> {
                if (index >= state.get().typeStats.size()) return "";
                StorageHostSnapshot.TypeStat stat = state.get().typeStats.get(index);
                return stat.displayName + ": " + number(stat.usedBytes) + " B, " + number(stat.usedTypes) + " types";
            }, 0, 0, 170).height(15)
                .setEnabledIf(widget -> index < state.get().typeStats.size()));
        }
        page.child(types);
        page.child(label("Amounts beyond long range", 195, 2));
        ListWidget<IWidget, ?> huge = new ListWidget<>().pos(190, 18)
            .size(184, 124)
            .padding(3)
            .background(new Rectangle().color(PANEL_ALT));
        for (int i = 0; i < 128; i++) {
            final int index = i;
            huge.child(dynamic(() -> {
                if (index >= state.get().hugeStacks.size()) return "";
                StorageHostSnapshot.HugeStack stack = state.get().hugeStacks.get(index);
                return stack.identity + "  x" + big(stack.amount);
            }, 0, 0, 172).height(15)
                .setEnabledIf(widget -> index < state.get().hugeStacks.size()));
        }
        page.child(huge);
        return page;
    }

    private static IWidget storagePriority(EntityPlayer player, TileECOController controller,
        SnapshotState<StorageHostSnapshot> state) {
        ParentWidget<?> page = page();
        page.child(section(55, 18, 264, 102));
        page.child(label("AE2 storage priority", 68, 30));
        page.child(
            dynamic(() -> Integer.toString(state.get().priority), 68, 56, 238).scale(1.5F)
                .textAlign(Alignment.Center));
        page.child(
            serverButton("-10", () -> controller.setPriority(controller.getPriority() - 10)).pos(68, 88)
                .size(52, 20));
        page.child(
            serverButton("-1", () -> controller.setPriority(controller.getPriority() - 1)).pos(126, 88)
                .size(52, 20));
        page.child(
            serverButton("+1", () -> controller.setPriority(controller.getPriority() + 1)).pos(196, 88)
                .size(52, 20));
        page.child(
            serverButton("+10", () -> controller.setPriority(controller.getPriority() + 10)).pos(254, 88)
                .size(52, 20));
        return page;
    }

    private static ModularPanel computationController(NeoEcoGuiData data, PanelSyncManager syncManager) {
        TileECOController controller = tile(data, TileECOController.class);
        SnapshotState<ComputationHostSnapshot> state = snapshot(
            syncManager,
            "computation_state",
            ComputationHostSnapshot.EMPTY,
            () -> ComputationHostSnapshot.create(controller),
            ComputationHostSnapshot::write,
            ComputationHostSnapshot::read,
            5);
        bindPlayerInventory(syncManager, data.getPlayer());
        ModularPanel panel = panel("computation_controller", 344, 232);
        panel.child(hostTitle(() -> hostDisplayTitle("computation", state.get().tier), 5, 9, 225));
        panel.child(
            dynamic(() -> formedLabel(state.get().formed), 234, 8, 80)
                .color(() -> state.get().formed ? 0xFF1A6A3A : 0xFF8A1A2A)
                .textAlign(Alignment.CenterRight));
        InteractionSyncHandler cpuModeHandler = new InteractionSyncHandler().setOnMousePressed(
            mouse -> {
                if (mouse.side.isServer() && mouse.mouseButton == 0) controller.cycleComputationCpuSelectionMode();
            });
        panel.child(
            new ButtonWidget<>().syncHandler(cpuModeHandler)
                .background(NeoEcoTextures.RECT_RD)
                .hoverBackground(NeoEcoTextures.RECT_RD_LIGHT)
                .overlay(new DynamicDrawable(() -> computationCpuIcon(state.get().cpuSelectionMode)))
                .pos(321, 5)
                .size(18, 18)
                .addTooltipLine(IKey.lang("gui.neoecoae.computation.cpu_selection_mode"))
                .addTooltipLine(
                    IKey.dynamic(
                        () -> tr("gui.neoecoae.computation.cpu_selection_mode.short") + ": "
                            + tr(
                                "gui.neoecoae.computation.cpu_selection_mode." + state.get().cpuSelectionMode.getId())))
                .addTooltipLine(IKey.lang("gui.neoecoae.computation.cpu_selection_mode.click")));

        panel.child(section(6, 24, 162, 108));
        panel.child(lang("gui.neoecoae.computation.capacity", 12, 30).color(TEXT));
        panel.child(lang("gui.neoecoae.computation.cpu_storage", 12, 44).color(MUTED));
        panel.child(
            new HostProgressWidget(() -> ratio(state.get().usedComputationBytes, state.get().totalBytes), () -> VALUE)
                .pos(12, 57)
                .size(70, 9)
                .addTooltipLine(
                    IKey.dynamic(
                        () -> tr("gui.neoecoae.computation.cpu_storage") + ": "
                            + number(state.get().usedComputationBytes)
                            + " / "
                            + number(state.get().totalBytes)
                            + " "
                            + tr("gui.neoecoae.storage_ui.bytes"))));
        panel.child(
            dynamic(
                () -> ae2Amount(state.get().usedComputationBytes) + " / " + ae2Amount(state.get().totalBytes),
                86,
                56,
                76).color(VALUE));
        panel.child(lang("gui.neoecoae.computation.threads", 12, 70).color(MUTED));
        panel.child(
            new HostProgressWidget(() -> ratio(state.get().usedThreads, state.get().totalThreads), () -> USED)
                .pos(12, 83)
                .size(70, 9)
                .addTooltipLine(
                    IKey.dynamic(
                        () -> tr("gui.neoecoae.computation.threads") + ": "
                            + number(state.get().usedThreads)
                            + " / "
                            + number(state.get().totalThreads))));
        panel.child(dynamic(() -> state.get().usedThreads + " / " + state.get().totalThreads, 86, 82, 76).color(USED));
        panel.child(lang("gui.neoecoae.computation.parallel_count", 12, 96).color(MUTED));
        panel.child(dynamic(() -> number(state.get().parallelCount), 12, 108, 70).color(VALUE));
        panel.child(lang("gui.neoecoae.computation.available_storage", 86, 96).color(MUTED));
        panel.child(
            dynamic(
                () -> ae2Amount(Math.max(0L, state.get().totalBytes - state.get().usedComputationBytes)),
                86,
                108,
                76).color(MUTED));

        panel.child(section(180, 24, 156, 200));
        panel.child(lang("gui.neoecoae.computation_ui.tasks", 192, 31).color(TEXT));
        panel.child(dynamic(() -> Integer.toString(state.get().tasks.size()), 308, 31, 20).color(VALUE));
        ListWidget<IWidget, ?> tasks = new ListWidget<>().pos(192, 43)
            .size(132, 174)
            .padding(2)
            .background(new Rectangle().color(0x00000000));
        for (int i = 0; i < 32; i++) tasks.child(computationTaskRow(state, i));
        panel.child(tasks);
        panel.child(lang("gui.neoecoae.common.inventory", 6, 136).color(HOST_TITLE));
        panel.child(playerInventory(6, 147));
        return panel;
    }

    private static IWidget computationTaskRow(SnapshotState<ComputationHostSnapshot> state, int index) {
        ParentWidget<?> row = new ParentWidget<>().widthRel(1.0F)
            .height(28)
            .background(new Rectangle().color(PANEL_EDGE))
            .setEnabledIf(widget -> index < state.get().tasks.size());
        row.child(
            new ParentWidget<>().pos(1, 1)
                .size(126, 26)
                .background(new Rectangle().color(PANEL_OUTER)));
        row.child(
            new ParentWidget<>().pos(3, 3)
                .size(122, 22)
                .background(new Rectangle().color(PANEL_ALT)));
        row.child(
            new ItemDisplayWidget()
                .item(
                    new ObjectValue.Dynamic<ItemStack>(
                        ItemStack.class,
                        () -> index < state.get().tasks.size() ? state.get().tasks.get(index).outputStack : null,
                        null))
                .pos(4, 4)
                .size(18));
        row.child(dynamic(() -> {
            if (index >= state.get().tasks.size()) return "";
            ComputationHostSnapshot.TaskEntry task = state.get().tasks.get(index);
            return fitText(task.outputName, 70);
        }, 24, 4, 70));
        row.child(
            dynamic(
                () -> index >= state.get().tasks.size() ? ""
                    : "x" + compactTaskAmount(state.get().tasks.get(index).outputAmount),
                94,
                11,
                29).color(VALUE)
                    .textAlign(Alignment.CenterRight));
        row.child(
            new LineProgressWidget(
                () -> 1D,
                () -> index < state.get().tasks.size() && state.get().tasks.get(index).status
                    == cn.dancingsnow.neoecoae.computation.ComputationTaskInfo.Status.WAITING ? WARN : BLUE).pos(3, 24)
                        .size(122, 2));
        return row;
    }

    private static ModularPanel craftingController(NeoEcoGuiData data, PanelSyncManager syncManager) {
        TileECOController controller = tile(data, TileECOController.class);
        SnapshotState<CraftingHostSnapshot> state = snapshot(
            syncManager,
            "crafting_state",
            CraftingHostSnapshot.EMPTY,
            () -> CraftingHostSnapshot.create(controller),
            CraftingHostSnapshot::write,
            CraftingHostSnapshot::read,
            5);
        bindPlayerInventory(syncManager, data.getPlayer());
        ModularPanel panel = panel("crafting_controller", 304, 196);
        panel.child(hostTitle(() -> hostDisplayTitle("crafting", state.get().tier), 6, 9, 184));
        panel.child(
            dynamic(() -> formedLabel(state.get().formed), 190, 9, 66)
                .color(() -> state.get().formed ? 0xFF1A6A3A : 0xFF8A1A2A)
                .textAlign(Alignment.CenterRight));
        panel.child(
            iconButton(NeoEcoTextures.POWER, controller::toggleCraftingOverclocked, () -> state.get().overclocked)
                .pos(261, 7)
                .size(16, 16)
                .addTooltipLine(IKey.lang("gui.neoecoae.crafting.overclocked.tooltip")));
        panel.child(
            iconButton(GuiTextures.FILTER, controller::toggleCraftingActiveCooling, () -> state.get().activeCooling)
                .pos(281, 7)
                .size(16, 16)
                .addTooltipLine(IKey.lang("gui.neoecoae.crafting.active_cooling.tooltip")));

        panel.child(section(6, 27, 76, 70));
        panel.child(lang("gui.neoecoae.crafting.ui.status", 12, 33).color(TEXT));
        panel.child(statusLight(12, 46, () -> state.get().overclocked));
        panel.child(lang("gui.neoecoae.crafting.ui.overclock_short", 29, 48).color(MUTED));
        panel.child(
            dynamic(() -> onOff(state.get().overclocked), 0, 48, 22)
                .left(
                    () -> 29 + estimatedTextWidth(tr("gui.neoecoae.crafting.ui.overclock_short")) + 3,
                    Unit.Measure.PIXEL)
                .color(() -> state.get().overclocked ? GOOD : BAD));
        panel.child(statusLight(12, 62, () -> state.get().activeCooling));
        panel.child(lang("gui.neoecoae.crafting.ui.cooling_short", 29, 64).color(MUTED));
        panel.child(
            dynamic(() -> onOff(state.get().activeCooling), 0, 64, 22)
                .left(
                    () -> 29 + estimatedTextWidth(tr("gui.neoecoae.crafting.ui.cooling_short")) + 3,
                    Unit.Measure.PIXEL)
                .color(() -> state.get().activeCooling ? GOOD : BAD));

        panel.child(section(88, 27, 114, 70));
        panel.child(lang("gui.neoecoae.crafting.ui.stats", 94, 33).color(TEXT));
        panel.child(
            dynamic(() -> formatMicros(state.get().performanceAverageNanos), 136, 33, 60).color(VALUE)
                .textAlign(Alignment.CenterRight));
        panel.child(lang("gui.neoecoae.crafting.ui.recipe_slots", 94, 45).color(MUTED));
        panel.child(
            dynamic(() -> state.get().occupiedRecipeSlots + " / " + state.get().maxRecipeSlots, 134, 46, 62).color(TEXT)
                .textAlign(Alignment.CenterRight));
        panel.child(
            new HostProgressWidget(
                () -> ratio(state.get().occupiedRecipeSlots, state.get().maxRecipeSlots),
                () -> VALUE).pos(94, 57)
                    .size(102, 9));
        panel.child(lang("gui.neoecoae.crafting.ui.batch_parallel", 94, 69).color(MUTED));
        panel.child(
            dynamic(() -> number(state.get().batchParallel), 0, 69, 66)
                .left(
                    () -> 94 + estimatedTextWidth(tr("gui.neoecoae.crafting.ui.batch_parallel")) + 4,
                    Unit.Measure.PIXEL)
                .color(BLUE));
        panel.child(dynamic(() -> tr("gui.neoecoae.host.crafting.overflow"), 94, 80, 28).color(MUTED));
        panel.child(
            dynamic(() -> number(state.get().overflowThreads), 0, 80, 28)
                .left(() -> 94 + estimatedTextWidth(tr("gui.neoecoae.host.crafting.overflow")) + 3, Unit.Measure.PIXEL)
                .color(0xFF000000));
        panel.child(lang("gui.neoecoae.crafting.ui.recipe_time_ratio", 152, 80).color(MUTED));
        panel.child(
            dynamic(() -> recipeTimeMultiplier(state.get().effectiveOverclockTimes), 0, 80, 24)
                .left(
                    () -> 152 + estimatedTextWidth(tr("gui.neoecoae.crafting.ui.recipe_time_ratio")) + 2,
                    Unit.Measure.PIXEL)
                .color(0xFF55A7FF));

        panel.child(section(208, 27, 90, 70));
        panel.child(lang("gui.neoecoae.crafting.ui.energy_cooling", 214, 33).color(TEXT));
        panel.child(
            new CraftingEnergyGaugeWidget(
                () -> ratio(state.get().maxEnergyUsage, state.get().energyGaugeReference),
                () -> craftingEnergyColor(state.get())).pos(225, 53)
                    .size(20, 32)
                    .addTooltipLine(IKey.lang("gui.neoecoae.crafting.ui.energy_usage"))
                    .addTooltipLine(IKey.dynamic(() -> number(state.get().maxEnergyUsage) + " AE/t")));
        panel.child(
            new CraftingCoolantGaugeWidget(
                () -> ratio(state.get().coolant, state.get().maxCoolant),
                () -> state.get().coolantMaxOverclock).pos(259, 53)
                    .size(23, 32)
                    .addTooltipLine(IKey.lang("gui.neoecoae.crafting.coolant"))
                    .addTooltipLine(
                        IKey.dynamic(() -> number(state.get().coolant) + " / " + number(state.get().maxCoolant)))
                    .addTooltipLine(IKey.dynamic(() -> {
                        int maxOverclock = state.get().coolantMaxOverclock;
                        Fluid fluid = CraftingCoolantGaugeWidget.coolantFluid(maxOverclock);
                        String fluidName = fluid == null ? tr("gui.neoecoae.crafting.coolant_max_overclock.none")
                            : fluid.getLocalizedName();
                        return fluidName + ": " + (maxOverclock <= 0 ? "-" : number(maxOverclock));
                    })));

        panel.child(lang("gui.neoecoae.common.inventory", 6, 102).color(HOST_TITLE));
        panel.child(playerInventory(6, 114));
        panel.child(section(176, 102, 122, 88));
        panel.child(lang("gui.neoecoae.crafting.tasks", 182, 108).color(TEXT));
        panel.child(
            dynamic(() -> Integer.toString(activeWorkerCount(state.get())), 268, 108, 16).color(VALUE)
                .textAlign(Alignment.CenterRight));
        panel.child(
            dynamic(() -> tr("gui.neoecoae.crafting.no_tasks"), 184, 148, 106).color(MUTED)
                .textAlign(Alignment.Center)
                .setEnabledIf(widget -> activeWorkerCount(state.get()) == 0));
        ListWidget<IWidget, ?> workers = new ListWidget<>().pos(184, 121)
            .size(106, 61)
            .padding(1)
            .background(new Rectangle().color(0x00000000));
        for (int i = 0; i < 16; i++) workers.child(craftingWorkerRow(state, i));
        panel.child(workers);
        return panel;
    }

    private static IWidget craftingWorkerRow(SnapshotState<CraftingHostSnapshot> state, int index) {
        ParentWidget<?> row = new ParentWidget<>().widthRel(1.0F)
            .height(16)
            .background(new Rectangle().color(0xFFD8D3E4))
            .setEnabledIf(widget -> activeWorker(state.get(), index) != null);
        row.child(
            new ParentWidget<>().pos(1, 1)
                .size(102, 14)
                .background(new Rectangle().color(PANEL_OUTER)));
        row.child(
            new ParentWidget<>().pos(2, 2)
                .size(100, 12)
                .background(new Rectangle().color(0xFF2C2735)));
        row.child(
            new ParentWidget<>().pos(2, 14)
                .size(100, 1)
                .background(new Rectangle().color(GOOD)));
        row.child(
            new ItemDisplayWidget()
                .item(
                    new ObjectValue.Dynamic<ItemStack>(
                        ItemStack.class,
                        () -> activeWorker(state.get(), index) == null ? null
                            : activeWorker(state.get(), index).outputStack,
                        null))
                .pos(3, 0)
                .size(16)
                .disableHoverBackground());
        row.child(dynamic(() -> {
            CraftingHostSnapshot.WorkerEntry worker = activeWorker(state.get(), index);
            String name = worker == null || worker.outputName.isEmpty() ? tr("gui.neoecoae.crafting.task.status.queued")
                : worker.outputName;
            return fitText(name, 52);
        }, 20, 3, 52));
        row.child(dynamic(() -> {
            CraftingHostSnapshot.WorkerEntry worker = activeWorker(state.get(), index);
            return worker == null ? "" : "x" + compactTaskAmount(worker.queueSize);
        }, 73, 3, 29).color(VALUE)
            .textAlign(Alignment.CenterRight));
        return row;
    }

    private static ModularPanel storageInterface(NeoEcoGuiData data, PanelSyncManager syncManager) {
        TileECOInterface storageInterface = tile(data, TileECOInterface.class);
        SnapshotState<StorageInterfaceSnapshot> state = snapshot(
            syncManager,
            "interface_state",
            StorageInterfaceSnapshot.EMPTY,
            () -> StorageInterfaceSnapshot.create(storageInterface),
            StorageInterfaceSnapshot::write,
            StorageInterfaceSnapshot::read,
            5);
        ModularPanel panel = panel("storage_interface", 250, 142);
        panel.child(title("gui.neoecoae.storage_interface.title", 8, 7));
        panel.child(section(8, 28, 234, 50));
        panel.child(
            dynamic(() -> "Network: " + online(state.get().targetOnline), 16, 37, 106)
                .color(() -> state.get().targetOnline ? GOOD : BAD));
        panel.child(
            dynamic(() -> "Structure: " + (state.get().formed ? "Formed" : "Unformed"), 126, 37, 106)
                .color(() -> state.get().formed ? GOOD : BAD));
        panel.child(
            dynamic(
                () -> "Last tick: " + number(state.get().transferredLastTick)
                    + " | Total: "
                    + number(state.get().transferredTotal),
                16,
                57,
                218));
        int x = 8;
        for (ECOStorageInterfaceMode mode : ECOStorageInterfaceMode.values()) {
            final ECOStorageInterfaceMode target = mode;
            panel.child(
                serverButton(mode.name(), () -> storageInterface.setStorageInterfaceMode(target)).pos(x, 88)
                    .size(74, 22)
                    .addTooltipLine(IKey.dynamic(() -> state.get().mode == target ? "Selected" : "Select mode")));
            x += 80;
        }
        panel.child(dynamic(() -> "Mode: " + state.get().mode.name(), 8, 118, 234).textAlign(Alignment.Center));
        return panel;
    }

    private static ModularPanel craftingPatternBus(NeoEcoGuiData data, PanelSyncManager syncManager) {
        TileCraftingPatternBus bus = tile(data, TileCraftingPatternBus.class);
        EntityPlayer player = data.getPlayer();
        PageState pageState = new PageState();
        AtomicInteger pageCount = new AtomicInteger(Math.max(1, bus.getPageCount()));
        syncManager.syncValue("page", new IntSyncValue(pageState::get, pageState::set));
        syncManager.syncValue("page_count", new IntSyncValue(() -> Math.max(1, bus.getPageCount()), pageCount::set));
        bindPlayerInventory(syncManager, player);
        PagedInventoryHandler patterns = new PagedInventoryHandler(
            new InvWrapper(bus),
            pageState,
            TileCraftingPatternBus.SLOTS_PER_PAGE);
        syncManager.registerSlotGroup("patterns", TileCraftingPatternBus.COLUMNS, 0);

        ModularPanel panel = panel("crafting_pattern_bus", 170, 259);
        panel.child(title("container.neoecoae.crafting_pattern_bus", 4, 6));
        panel.child(
            serverButton("<", () -> pageState.set(Math.max(0, pageState.get() - 1))).pos(105, 23)
                .size(16, 16));
        panel.child(
            dynamic(() -> (pageState.get() + 1) + "/" + Math.max(1, pageCount.get()), 123, 25, 24).color(HOST_TITLE)
                .scale(0.85F)
                .textAlign(Alignment.Center));
        panel.child(
            serverButton(">", () -> pageState.set(Math.min(Math.max(0, bus.getPageCount() - 1), pageState.get() + 1)))
                .pos(149, 23)
                .size(16, 16));
        SlotGroupWidget grid = SlotGroupWidget.builder()
            .matrix("IIIIIIIII", "IIIIIIIII", "IIIIIIIII", "IIIIIIIII", "IIIIIIIII", "IIIIIIIII", "IIIIIIIII")
            .key('I', index -> patternBusSlot(bus, patterns, pageState, index))
            .build()
            .pos(4, 42);
        panel.child(grid);
        panel.child(playerInventory(4, 175));
        return panel;
    }

    private static ItemSlot patternBusSlot(TileCraftingPatternBus bus, PagedInventoryHandler patterns, PageState page,
        int index) {
        final int slot = index;
        ItemSlot widget = new ItemSlot() {

            @Override
            protected ItemStack getItemStackForRendering(ItemStack itemstack, boolean dragging) {
                ItemStack displayStack = bus.getPatternDisplayStack(itemstack);
                return displayStack == null ? itemstack : displayStack;
            }
        };
        ModularSlot modularSlot = new ModularSlot(patterns, index).slotGroup("patterns");
        widget.slot(modularSlot)
            .background(
                new DynamicDrawable(
                    () -> modularSlot.getStack() == null ? NeoEcoTextures.EMPTY_PATTERN_SLOT : NeoEcoTextures.SLOT));
        return widget;
    }

    private static ModularPanel patternUpload(NeoEcoGuiData data, PanelSyncManager syncManager) {
        PatternUploadSession session = PatternUploadSessions.get(data.getUploadSession());
        SnapshotState<UploadSnapshot> state = snapshot(
            syncManager,
            "upload_targets",
            UploadSnapshot.EMPTY,
            () -> UploadSnapshot.create(session),
            UploadSnapshot::write,
            UploadSnapshot::read,
            20);
        AtomicInteger selected = new AtomicInteger();
        syncManager.syncValue("upload_selected", new IntSyncValue(selected::get, selected::set));
        UploadPanelHeightState heightState = new UploadPanelHeightState();
        syncManager.syncValue("upload_height", new IntSyncValue(heightState::get, heightState::set));

        ModularPanel panel = ModularPanel.defaultPanel("pattern_upload")
            .width(306)
            .height(heightState::panelHeight, Unit.Measure.PIXEL)
            .invisible();
        heightState.track(panel);
        ParentWidget<?> content = new ParentWidget<>().left(20)
            .top(0)
            .width(286)
            .height(heightState::panelHeight, Unit.Measure.PIXEL)
            .background(NeoEcoTextures.BACKGROUND);
        heightState.track(content);
        panel.child(content);
        content.child(title("gui.neoecoae.pattern_upload.title", 8, 8));
        content.child(
            new ItemDisplayWidget()
                .item(
                    new ObjectValue.Dynamic<ItemStack>(
                        ItemStack.class,
                        () -> state.get().output == null ? state.get().pattern : state.get().output,
                        null))
                .pos(9, 27)
                .size(20)
                .disableHoverBackground()
                .tooltip(new RichTooltip().tooltipBuilder(tooltip -> {
                    ItemStack pattern = state.get().pattern;
                    if (pattern != null) tooltip.addFromItem(pattern);
                })
                    .setAutoUpdate(true)));
        content.child(
            dynamic(() -> patternOutputName(state.get()), 36, 29, 232).color(HOST_TITLE)
                .scale(0.92F));

        ListWidget<IWidget, ?> targets = new ListWidget<>().left(8)
            .top(54)
            .width(270)
            .height(heightState::listHeight, Unit.Measure.PIXEL)
            .padding(2)
            .background(new Rectangle().color(PANEL));
        heightState.track(targets);
        // Keep one row for every target the snapshot protocol can carry. ListWidget clips this
        // to the selected panel height and handles the mouse wheel/scrollbar itself.
        for (int i = 0; i < UploadSnapshot.MAX_TARGETS; i++) {
            targets.child(uploadTargetRow(data, state, selected, i));
        }
        content.child(targets);
        ButtonWidget<?> uploadButton = dynamicServerButton(
            () -> state.get().uploaded ? tr("gui.neoecoae.pattern_upload.undo")
                : tr("gui.neoecoae.pattern_upload.upload_selected"),
            () -> {
                PatternUploadSession live = PatternUploadSessions.get(data.getUploadSession());
                if (live != null && live.isUploaded()) {
                    if (live.undoUpload()) {
                        data.getPlayer()
                            .addChatMessage(new ChatComponentTranslation("gui.neoecoae.pattern_upload.undo_success"));
                    } else {
                        data.getPlayer()
                            .addChatMessage(new ChatComponentTranslation("gui.neoecoae.pattern_upload.failed"));
                    }
                    return;
                }
                int index = selected.get();
                if (live == null || index < 0
                    || index >= live.getTargets()
                        .size())
                    return;
                if (live.upload(
                    live.getTargets()
                        .get(index)
                        .getId())) {
                    data.getPlayer()
                        .addChatMessage(new ChatComponentTranslation("gui.neoecoae.pattern_upload.success"));
                } else {
                    data.getPlayer()
                        .addChatMessage(new ChatComponentTranslation("gui.neoecoae.pattern_upload.failed"));
                }
            }).left(92)
                .top(heightState::buttonY, Unit.Measure.PIXEL)
                .size(90, 19)
                .setEnabledIf(widget -> {
                    int index = selected.get();
                    return state.get().uploaded || (index >= 0 && index < state.get().targets.size()
                        && state.get().targets.get(index).exact
                        && !state.get().targets.get(index).maxed
                        && !state.get().targets.get(index).hasPattern);
                });
        heightState.track(uploadButton);
        content.child(uploadButton);
        ButtonWidget<?> cancelButton = dynamicServerButton(() -> tr("gui.neoecoae.pattern_upload.cancel"), () -> {
            PatternUploadSessions.remove(data.getUploadSession());
            data.getPlayer()
                .closeScreen();
        }).left(208)
            .top(heightState::buttonY, Unit.Measure.PIXEL)
            .size(70, 19);
        heightState.track(cancelButton);
        content.child(cancelButton);
        InteractionSyncHandler heightHandler = new InteractionSyncHandler()
            .setOnMousePressed(mouse -> { if (mouse.side.isServer() && mouse.mouseButton == 0) heightState.cycle(); });
        panel.child(
            new ButtonWidget<>().syncHandler(heightHandler)
                .background(NeoEcoTextures.AE2_TERMINAL_STYLE_BUTTON)
                .hoverBackground(NeoEcoTextures.AE2_TERMINAL_STYLE_BUTTON)
                .overlay(new DynamicDrawable(heightState::icon))
                .pos(0, 27)
                .size(18, 18)
                .addTooltipLine(IKey.dynamic(heightState::tooltip)));
        return panel;
    }

    private static String patternOutputName(UploadSnapshot snapshot) {
        if (snapshot.pattern == null) return tr("gui.neoecoae.pattern_upload.no_pattern");
        if (snapshot.output != null) {
            String name = snapshot.output.getDisplayName();
            if (name != null && !name.isEmpty()) return name;
        }
        return snapshot.processing ? tr("gui.neoecoae.pattern_upload.processing")
            : tr("gui.neoecoae.pattern_upload.crafting");
    }

    private static IWidget uploadTargetRow(NeoEcoGuiData data, SnapshotState<UploadSnapshot> state,
        AtomicInteger selected, int index) {
        ParentWidget<?> row = new ParentWidget<>().widthRel(1.0F)
            .height(28)
            .setEnabledIf(widget -> index < state.get().targets.size());
        ButtonWidget<?> button = dynamicServerButton(() -> "", () -> selected.set(index));
        row.child(
            button.pos(24, 1)
                .size(200, 26)
                .background(
                    new DynamicDrawable(
                        () -> selected.get() == index ? NeoEcoTextures.BUTTON_HOVER : NeoEcoTextures.BUTTON)));
        row.child(
            new ItemDisplayWidget().item(
                new ObjectValue.Dynamic<ItemStack>(
                    ItemStack.class,
                    () -> { return index < state.get().targets.size() ? state.get().targets.get(index).icon : null; },
                    null))
                .pos(3, 5)
                .size(18)
                .disableHoverBackground());
        row.child(
            dynamic(
                () -> index < state.get().targets.size() ? targetDisplayName(state.get().targets.get(index)) : "",
                32,
                4,
                194).height(9)
                    .scale(0.78F)
                    .color(0xFF25212E));
        row.child(
            dynamic(
                () -> index < state.get().targets.size() ? targetKindDisplay(state.get().targets.get(index)) : "",
                32,
                13,
                120).height(8)
                    .scale(0.62F)
                    .color(0xFF514A5D));
        row.child(
            dynamic(
                () -> index < state.get().targets.size() ? targetCapacity(state.get().targets.get(index)) : "",
                151,
                13,
                70).height(8)
                    .scale(0.72F)
                    .color(
                        () -> index < state.get().targets.size()
                            && (state.get().targets.get(index).maxed || state.get().targets.get(index).hasPattern)
                                ? 0xFF9B2435
                                : 0xFF176B3A)
                    .textAlign(Alignment.CenterRight));
        row.child(dynamicServerButton(() -> tr("gui.neoecoae.pattern_upload.position"), () -> {
            PatternUploadSession session = PatternUploadSessions.get(data.getUploadSession());
            if (session == null || index < 0
                || index >= session.getTargets()
                    .size())
                return;
            PatternUploadTarget target = session.getTargets()
                .get(index);
            if (data.getPlayer() instanceof EntityPlayerMP && target.isPresent()) {
                NEPatternUploadNetwork.showHighlight(
                    (EntityPlayerMP) data.getPlayer(),
                    target.getDimension(),
                    target.getX(),
                    target.getY(),
                    target.getZ());
            }
        }).pos(228, 5)
            .size(36, 18)
            .addTooltipLine(IKey.dynamic(() -> {
                UploadTargetSnapshot target = index < state.get().targets.size() ? state.get().targets.get(index)
                    : null;
                return target == null ? ""
                    : tr("gui.neoecoae.pattern_upload.position") + ": " + target.x + ", " + target.y + ", " + target.z;
            })));
        return row;
    }

    private static String targetDisplayName(UploadTargetSnapshot target) {
        if (StatCollector.canTranslate(target.name)) return tr(target.name);
        if (StatCollector.canTranslate(target.name + ".name")) return tr(target.name + ".name");
        if (!target.name.contains(".")) return target.name;
        if (target.icon != null) {
            String displayName = target.icon.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) return displayName;
        }
        return target.name;
    }

    private static String targetCapacity(UploadTargetSnapshot target) {
        return target.hasPattern ? tr("gui.neoecoae.pattern_upload.existing_pattern")
            : target.maxed ? "MAX"
                : StatCollector.translateToLocalFormatted(
                    "gui.neoecoae.pattern_upload.empty_slots",
                    Integer.toString(target.emptySlots),
                    Integer.toString(target.capacity));
    }

    private static String targetKindName(int kind) {
        PatternUploadTarget.Kind[] kinds = PatternUploadTarget.Kind.values();
        if (kind < 0 || kind >= kinds.length) return "";
        switch (kinds[kind]) {
            case GT_CRAFTING_INPUT:
                return tr("gui.neoecoae.pattern_upload.target.gt_assembly");
            case GT_CRAFTING_INPUT_BUS:
                return tr("gui.neoecoae.pattern_upload.target.gt_bus");
            case ECO_PATTERN_BUS:
                return tr("gui.neoecoae.pattern_upload.target.eco");
            case PROGRAMMABLE_HATCH:
                return tr("gui.neoecoae.pattern_upload.target.programmable");
            case AE2_INTERFACE:
                return tr("gui.neoecoae.pattern_upload.target.ae2");
            case AE2_DUAL_INTERFACE:
                return tr("gui.neoecoae.pattern_upload.target.ae2_dual");
            default:
                return tr("gui.neoecoae.pattern_upload.target.ae2");
        }
    }

    private static String targetKindDisplay(UploadTargetSnapshot target) {
        String kind = targetKindName(target.kind);
        String circuit = target.circuit == null || target.programmingCover ? ""
            : " / " + circuitDisplayName(target.circuit);
        String programmingCover = target.programmingCover ? " / " + tr("gui.neoecoae.pattern_upload.programming_cover")
            : "";
        return kind + circuit + programmingCover;
    }

    private static String circuitDisplayName(ItemStack circuit) {
        if (circuit == null) return "";
        return tr("gui.neoecoae.pattern_upload.circuit_short") + " " + (circuit.getItemDamage() & 0xFF);
    }

    private static ModularPanel craftingHatch(NeoEcoGuiData data, PanelSyncManager syncManager) {
        TileCraftingHatch hatch = tile(data, TileCraftingHatch.class);
        bindPlayerInventory(syncManager, data.getPlayer());
        ModularPanel panel = panel("crafting_hatch", 172, 142);
        panel.child(
            title(
                hatch.isInput() ? "container.neoecoae.crafting_input_hatch"
                    : "container.neoecoae.crafting_output_hatch",
                7,
                7));
        panel.child(
            new FluidSlot()
                .syncHandler(
                    new com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler(hatch.getFluidTank())
                        .canFillSlot(hatch.isInput())
                        .canDrainSlot(!hatch.isInput()))
                .background(NeoEcoTextures.SLOT)
                .pos(77, 28)
                .size(18, 18));
        panel.child(playerInventory(7, 60));
        return panel;
    }

    private static ModularPanel structureTerminal(NeoEcoGuiData data, PanelSyncManager syncManager) {
        EntityPlayer player = data.getPlayer();
        ModularSlot held = bindHeldItem(syncManager, data);
        ModularPanel panel = panel("structure_terminal", 304, 214);
        panel.child(title("gui.neoecoae.structure_terminal.title", 8, 7));
        panel.child(
            new ItemSlot().syncHandler("held_item")
                .background(NeoEcoTextures.SLOT)
                .pos(278, 5));
        panel.child(label("Host type", 8, 30));
        StructureTerminalHostType[] hosts = StructureTerminalHostType.values();
        for (int i = 0; i < hosts.length; i++) {
            final StructureTerminalHostType host = hosts[i];
            panel.child(
                serverButton(
                    host.name(),
                    () -> mutateHeld(data, stack -> ItemECOStructureTerminal.setHostType(stack, host)))
                        .pos(8 + i * 98, 45)
                        .size(92, 21));
        }
        panel.child(label("Tier", 8, 76));
        String[] tiers = { "l4", "l6", "l9" };
        for (int i = 0; i < tiers.length; i++) {
            final String tier = tiers[i];
            panel.child(
                serverButton(
                    tier.toUpperCase(Locale.ROOT),
                    () -> mutateHeld(data, stack -> ItemECOStructureTerminal.setHostTier(stack, tier)))
                        .pos(8 + i * 66, 91)
                        .size(60, 20));
        }
        panel.child(label("Build length", 208, 76));
        panel.child(
            serverButton(
                "-",
                () -> mutateHeld(
                    data,
                    stack -> ItemECOStructureTerminal
                        .setBuildLength(stack, ItemECOStructureTerminal.getBuildLength(stack) - 1))).pos(208, 91)
                            .size(28, 20));
        panel.child(
            dynamic(() -> Integer.toString(ItemECOStructureTerminal.getBuildLength(data.getItemStack())), 238, 94, 28)
                .textAlign(Alignment.Center));
        panel.child(
            serverButton(
                "+",
                () -> mutateHeld(
                    data,
                    stack -> ItemECOStructureTerminal
                        .setBuildLength(stack, ItemECOStructureTerminal.getBuildLength(stack) + 1))).pos(268, 91)
                            .size(28, 20));
        panel.child(label("Operation", 8, 122));
        StructureTerminalMode[] modes = StructureTerminalMode.values();
        for (int i = 0; i < modes.length; i++) {
            final StructureTerminalMode mode = modes[i];
            panel.child(
                serverButton(
                    mode.name(),
                    () -> mutateHeld(data, stack -> ItemECOStructureTerminal.setOperationMode(stack, mode)))
                        .pos(8 + i * 98, 137)
                        .size(92, 21));
        }
        panel.child(section(8, 169, 288, 36));
        panel.child(dynamic(() -> structureSummary(data.getItemStack()), 14, 176, 276).scale(0.8F));
        return panel;
    }

    private static ModularPanel storageRecoveryTerminal(NeoEcoGuiData data, PanelSyncManager syncManager) {
        bindHeldItem(syncManager, data);
        ModularPanel panel = panel("storage_recovery_terminal", 304, 162);
        panel.child(title("gui.neoecoae.storage_recovery_terminal.title", 8, 7));
        panel.child(
            new ItemSlot().syncHandler("held_item")
                .background(NeoEcoTextures.SLOT)
                .pos(278, 5));
        panel.child(section(8, 30, 288, 64));
        panel.child(label("Storage domain UUID", 16, 38));
        panel.child(dynamic(() -> {
            UUID selected = ItemECOStorageRecoveryTerminal.getSelectedDomain(data.getItemStack());
            return selected == null ? "No domain selected" : selected.toString();
        }, 16, 58, 272).textAlign(Alignment.Center));
        panel.child(
            serverButton("Previous", () -> cycleRecovery(data, -1)).pos(8, 104)
                .size(92, 22));
        panel.child(
            serverButton("Next", () -> cycleRecovery(data, 1)).pos(204, 104)
                .size(92, 22));
        panel.child(
            dynamic(
                () -> "Target: " + ItemECOStorageRecoveryTerminal.getTargetDescription(data.getItemStack()),
                8,
                138,
                288).textAlign(Alignment.Center));
        return panel;
    }

    private static ModularPanel panel(String name, int width, int height) {
        return ModularPanel.defaultPanel(name, width, height)
            .background(NeoEcoTextures.BACKGROUND);
    }

    private static ParentWidget<?> page() {
        return new ParentWidget<>().sizeRel(1.0F);
    }

    private static IWidget section(int x, int y, int width, int height) {
        return new ParentWidget<>().pos(x, y)
            .size(width, height)
            .background(ExactPanelBorderDrawable.INSTANCE);
    }

    private static IWidget darkInset(int x, int y, int width, int height) {
        ParentWidget<?> inset = new ParentWidget<>().pos(x, y)
            .size(width, height)
            .background(new Rectangle().color(PANEL_EDGE));
        inset.child(
            new ParentWidget<>().pos(1, 1)
                .size(width - 2, height - 2)
                .background(new Rectangle().color(PANEL_OUTER)));
        inset.child(
            new ParentWidget<>().pos(2, 2)
                .size(width - 4, height - 4)
                .background(new Rectangle().color(PANEL_ALT)));
        return inset;
    }

    private static TextWidget<?> title(String translationKey, int x, int y) {
        return IKey.lang(translationKey)
            .asWidget()
            .pos(x, y)
            .height(14)
            .color(HOST_TITLE);
    }

    private static TextWidget<?> hostTitle(Supplier<String> localizedName, int x, int y, int width) {
        return dynamic(localizedName, x, y, width).color(HOST_TITLE);
    }

    private static TextWidget<?> lang(String translationKey, int x, int y) {
        return IKey.lang(translationKey)
            .asWidget()
            .pos(x, y)
            .height(12)
            .color(MUTED);
    }

    private static TextWidget<?> label(String value, int x, int y) {
        return IKey.str(value)
            .asWidget()
            .pos(x, y)
            .height(12)
            .color(MUTED);
    }

    private static TextWidget<?> dynamic(Supplier<String> value, int x, int y, int width) {
        return IKey.dynamic(value)
            .asWidget()
            .pos(x, y)
            .size(width, 12)
            .color(TEXT);
    }

    private static ParentWidget<?> infiniteStorageMetric(Supplier<String> amount, String labelKey, int y,
        IntSupplier amountColor) {
        ParentWidget<?> row = new ParentWidget<>().pos(14, y)
            .size(150, 12);
        row.child(dynamic(amount, 0, 0, 62).color(amountColor));
        row.child(
            dynamic(() -> " " + tr(labelKey), 0, 0, 60)
                .left(() -> estimatedTextWidth(amount.get()) + 2, Unit.Measure.PIXEL)
                .color(MUTED));
        return row;
    }

    private static PageButton tab(int index, PagedWidget.Controller controller, String text, int x) {
        return (PageButton) new PageButton(index, controller).overlay(IKey.str(text))
            .pos(x, 20)
            .size(64, 18);
    }

    private static ButtonWidget<?> serverButton(String text, Runnable action) {
        InteractionSyncHandler handler = new InteractionSyncHandler()
            .setOnMousePressed(mouse -> { if (mouse.side.isServer() && mouse.mouseButton == 0) action.run(); });
        return new ButtonWidget<>().syncHandler(handler)
            .background(NeoEcoTextures.BUTTON)
            .hoverBackground(NeoEcoTextures.BUTTON_HOVER)
            .overlay(IKey.str(text));
    }

    private static ButtonWidget<?> dynamicServerButton(Supplier<String> text, Runnable action) {
        InteractionSyncHandler handler = new InteractionSyncHandler()
            .setOnMousePressed(mouse -> { if (mouse.side.isServer() && mouse.mouseButton == 0) action.run(); });
        return new ButtonWidget<>().syncHandler(handler)
            .background(NeoEcoTextures.BUTTON)
            .hoverBackground(NeoEcoTextures.BUTTON_HOVER)
            .overlay(IKey.dynamic(text));
    }

    private static ButtonWidget<?> iconButton(com.cleanroommc.modularui.api.drawable.IDrawable icon, Runnable action,
        BooleanSupplier selected) {
        InteractionSyncHandler handler = new InteractionSyncHandler()
            .setOnMousePressed(mouse -> { if (mouse.side.isServer() && mouse.mouseButton == 0) action.run(); });
        return new ButtonWidget<>().syncHandler(handler)
            .background(
                new DynamicDrawable(
                    () -> selected.getAsBoolean() ? NeoEcoTextures.RECT_RD_DARK : NeoEcoTextures.RECT_RD))
            .hoverBackground(NeoEcoTextures.RECT_RD_LIGHT)
            .overlay(icon);
    }

    private static IDrawable computationCpuIcon(
        cn.dancingsnow.neoecoae.gui.computation.ComputationCpuSelectionMode mode) {
        switch (mode) {
            case PLAYER_ONLY:
                return GuiTextures.FILTER;
            case MACHINE_ONLY:
                return GuiTextures.GEAR;
            case ANY:
            default:
                return NeoEcoTextures.HAMMER;
        }
    }

    private static IWidget statusLight(int x, int y, BooleanSupplier enabled) {
        ParentWidget<?> light = new ParentWidget<>().pos(x, y)
            .size(13, 13)
            .background(new Rectangle().color(PANEL_EDGE));
        light.child(
            new ParentWidget<>().pos(1, 1)
                .size(11, 11)
                .background(new Rectangle().color(PANEL_OUTER)));
        light.child(
            new ParentWidget<>().pos(2, 2)
                .size(9, 9)
                .background(new DynamicDrawable(() -> new Rectangle().color(enabled.getAsBoolean() ? GOOD : BAD))));
        return light;
    }

    private static void bindPlayerInventory(PanelSyncManager syncManager, EntityPlayer player) {
        syncManager.bindPlayerInventory(player);
    }

    private static SlotGroupWidget playerInventory(int x, int y) {
        return SlotGroupWidget.playerInventory((index, slot) -> slot.background(NeoEcoTextures.SLOT))
            .pos(x, y);
    }

    private static ModularSlot bindHeldItem(PanelSyncManager syncManager, NeoEcoGuiData data) {
        ModularSlot slot = new ModularSlot(new PlayerMainInvWrapper(data.getPlayer().inventory), data.getItemSlot())
            .accessibility(false, false);
        syncManager.itemSlot("held_item", slot);
        return slot;
    }

    private static void mutateHeld(NeoEcoGuiData data, ItemMutation mutation) {
        ItemStack stack = data.getItemStack();
        if (stack == null || data.getPlayer().worldObj.isRemote) return;
        mutation.apply(stack);
        data.getPlayer().inventory.markDirty();
    }

    private static void cycleRecovery(NeoEcoGuiData data, int delta) {
        if (data.getPlayer().worldObj.isRemote) return;
        ItemECOStorageRecoveryTerminal.cycleSelectedDomain(data.getItemStack(), data.getWorld(), delta);
        data.getPlayer().inventory.markDirty();
    }

    private static String structureSummary(ItemStack stack) {
        if (stack == null) return "No terminal";
        return ItemECOStructureTerminal.getHostType(stack)
            .name() + " / "
            + ItemECOStructureTerminal.getHostTier(stack)
                .toUpperCase(Locale.ROOT)
            + " / length "
            + ItemECOStructureTerminal.getBuildLength(stack)
            + " / "
            + ItemECOStructureTerminal.getOperationMode(stack)
                .name()
            + "\nTarget: "
            + ItemECOStructureTerminal.getTargetDescription(stack);
    }

    private static String fluidName(int id) {
        Fluid fluid = FluidRegistry.getFluid(id);
        return fluid == null ? "Empty" : fluid.getLocalizedName();
    }

    private static String hostStatus(boolean formed, String tier) {
        return (formed ? "Formed" : "Unformed") + " | " + (tier == null ? "" : tier.toUpperCase(Locale.ROOT));
    }

    private static String formedLabel(boolean formed) {
        return tr("gui.neoecoae.machine.formed") + ": "
            + tr(formed ? "gui.neoecoae.common.yes" : "gui.neoecoae.common.no");
    }

    private static String hostDisplayTitle(String subsystem, String tier) {
        String level = tier == null || tier.isEmpty() ? "4" : tier.substring(tier.length() - 1);
        String prefix = "crafting".equals(subsystem) ? "F" : "computation".equals(subsystem) ? "C" : "L";
        return StatCollector.translateToLocalFormatted("gui.neoecoae.host.title." + subsystem, prefix + level);
    }

    private static String onOff(boolean value) {
        return tr(value ? "gui.neoecoae.common.on" : "gui.neoecoae.common.off");
    }

    private static String localMode(String mode) {
        String key = "gui.neoecoae.storage_ui.mode." + (mode == null || mode.isEmpty() ? "unformed" : mode);
        String translated = tr(key);
        return translated.equals(key) ? String.valueOf(mode) : translated;
    }

    private static String computationStatus(cn.dancingsnow.neoecoae.computation.ComputationTaskInfo.Status status) {
        String key = status == cn.dancingsnow.neoecoae.computation.ComputationTaskInfo.Status.WAITING
            ? "gui.neoecoae.crafting.task.status.queued"
            : "gui.neoecoae.crafting.task.status.running";
        return tr(key);
    }

    private static String formatMicros(long nanos) {
        return NUMBERS.format(Math.max(0L, nanos) / 1_000L) + " us";
    }

    private static String recipeTimeMultiplier(int effectiveOverclockTimes) {
        int level = Math.max(0, Math.min(9, effectiveOverclockTimes));
        int ticks = (int) Math.ceil(10D / (level + 1));
        return String.format(Locale.US, "%.1fx", ticks / 10D);
    }

    private static int activeWorkerCount(CraftingHostSnapshot state) {
        int count = 0;
        for (CraftingHostSnapshot.WorkerEntry worker : state.workerEntries) {
            if (worker.queueSize > 0) count++;
        }
        return count;
    }

    private static CraftingHostSnapshot.WorkerEntry activeWorker(CraftingHostSnapshot state, int index) {
        int activeIndex = 0;
        for (CraftingHostSnapshot.WorkerEntry worker : state.workerEntries) {
            if (worker.queueSize <= 0) continue;
            if (activeIndex++ == index) return worker;
        }
        return null;
    }

    private static String storageBytes(BigInteger used, long total) {
        return big(used) + " / " + number(total) + " B";
    }

    private static long energyLong(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0D) return 0L;
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(value);
    }

    private static String ae2Amount(long value) {
        return ReadableNumberConverter.INSTANCE.toWideReadableForm(Math.max(0L, value));
    }

    private static String ae2Amount(BigInteger value) {
        return ae2Amount(saturatedLong(value));
    }

    private static String fitStorageAmount(BigInteger value, int maxWidth) {
        BigInteger safe = value == null || value.signum() < 0 ? BigInteger.ZERO : value;
        String exact = expandedStorageBytes(safe);
        if (estimatedTextWidth(exact) <= maxWidth) return exact;

        int naturalUnitIndex = 0;
        BigInteger naturalUnit = BigInteger.ONE;
        while (naturalUnitIndex < EXPANDED_BYTE_UNITS.length - 1
            && safe.compareTo(naturalUnit.multiply(BIG_BYTES_IN_K)) >= 0) {
            naturalUnit = naturalUnit.multiply(BIG_BYTES_IN_K);
            naturalUnitIndex++;
        }
        for (int decimals = 2; decimals >= 0; decimals--) {
            BigInteger unit = BIG_BYTES_IN_K;
            for (int unitIndex = 1; unitIndex <= naturalUnitIndex; unitIndex++) {
                String candidate = new BigDecimal(safe).divide(new BigDecimal(unit), decimals, RoundingMode.HALF_UP)
                    .toPlainString() + EXPANDED_BYTE_UNITS[unitIndex];
                if (estimatedTextWidth(candidate) <= maxWidth) return candidate;
                unit = unit.multiply(BIG_BYTES_IN_K);
            }
        }

        int exponent = Math.max(
            0,
            safe.toString()
                .length() - 1);
        BigDecimal divisor = BigDecimal.TEN.pow(exponent);
        for (int decimals = 3; decimals >= 0; decimals--) {
            String candidate = new BigDecimal(safe).divide(divisor, decimals, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "e" + exponent;
            if (estimatedTextWidth(candidate) <= maxWidth) return candidate;
        }
        return "e" + exponent;
    }

    private static String fitText(String value, int maxWidth) {
        String safe = value == null ? "" : value;
        if (estimatedTextWidth(safe) <= maxWidth) return safe;
        String suffix = "...";
        int end = safe.length();
        while (end > 0 && estimatedTextWidth(safe.substring(0, end) + suffix) > maxWidth) {
            end--;
        }
        return end <= 0 ? suffix : safe.substring(0, end) + suffix;
    }

    private static String expandedStorageBytes(BigInteger value) {
        return value == null ? "0" : NUMBERS.format(value.max(BigInteger.ZERO));
    }

    private static EnergyUsage energyUsage(StorageHostSnapshot state) {
        long used = energyLong(state.energyStored);
        long maximum = energyLong(state.energyCapacity);
        String usedText = number(used);
        String maxText = number(maximum);
        String prefix = StatCollector.translateToLocal("gui.neoecoae.storage_ui.energy_storage") + ": ";
        if (estimatedTextWidth(prefix + usedText + " / " + maxText + " AE") > 160) {
            usedText = compactTaskAmount(used);
            maxText = compactTaskAmount(maximum);
        }
        return new EnergyUsage(usedText, maxText);
    }

    private static String compactTaskAmount(long value) {
        long safe = Math.max(0L, value);
        if (safe < 1_000L) return Long.toString(safe);
        if (safe < 1_000_000L) return compactDecimal(safe, 1_000L, "K");
        if (safe < 1_000_000_000L) return compactDecimal(safe, 1_000_000L, "M");
        if (safe < 1_000_000_000_000L) return compactDecimal(safe, 1_000_000_000L, "G");
        return compactDecimal(safe, 1_000_000_000_000L, "T");
    }

    private static String compactDecimal(long value, long unit, String suffix) {
        double scaled = (double) Math.max(0L, value) / (double) unit;
        DecimalFormat format = COMPACT_DECIMAL.get();
        format.applyPattern(scaled >= 100.0D || Math.abs(scaled - Math.rint(scaled)) < 0.05D ? "0" : "0.0");
        return format.format(scaled) + suffix;
    }

    private static final class EnergyUsage {

        private final String usedText;
        private final String maxText;

        private EnergyUsage(String usedText, String maxText) {
            this.usedText = usedText;
            this.maxText = maxText;
        }
    }

    private static int estimatedTextWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            width += character == ' ' ? 4 : character < 128 ? 6 : 8;
        }
        return width;
    }

    private static long saturatedLong(BigInteger value) {
        if (value == null || value.signum() <= 0) return 0L;
        BigInteger maximum = BigInteger.valueOf(Long.MAX_VALUE);
        return value.compareTo(maximum) >= 0 ? Long.MAX_VALUE : value.longValue();
    }

    private static String percent(long used, long total) {
        if (total <= 0L) return "N/A";
        return percent(ratio(used, total));
    }

    private static String percent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "N/A";
        return String.format(Locale.US, "%.1f%%", Math.max(0D, Math.min(1D, value)) * 100D);
    }

    private static double ratio(long value, long maximum) {
        return maximum <= 0L ? 0D : Math.max(0D, Math.min(1D, (double) value / (double) maximum));
    }

    private static double maxMatrixLoad(StorageHostSnapshot state) {
        double maximum = 0D;
        for (StorageHostSnapshot.MatrixCell cell : state.matrixCells) {
            if (cell.hasCell) maximum = Math.max(maximum, ratio(cell.usedBytes, cell.totalBytes));
        }
        return maximum;
    }

    private static int idleMatrices(StorageHostSnapshot state) {
        int count = 0;
        for (StorageHostSnapshot.MatrixCell cell : state.matrixCells) {
            if (cell.hasCell && cell.usedBytes <= 0L && cell.usedTypes <= 0L) count++;
        }
        return count;
    }

    private static int storageValueColor(long used, long total) {
        double value = ratio(used, total);
        if (used <= 0L || total <= 0L) return USED;
        if (value >= 1.0D) return 0xFFFF6A75;
        if (value >= 0.9D) return 0xFFFF9A3D;
        if (value >= 0.75D) return 0xFFFFD65A;
        return USED;
    }

    private static int craftingEnergyColor(CraftingHostSnapshot state) {
        if (state.maxEnergyUsage >= state.energyGaugeReference * 9L / 10L) return 0xFFFFD65A;
        if (state.maxEnergyUsage >= state.energyGaugeReference / 2L) return 0xFFE7A943;
        return USED;
    }

    private static double storageProgress(StorageHostSnapshot state) {
        return isInfiniteStorage(state) ? 1D : ratio(state.usedBytes, state.totalBytes);
    }

    private static int storageTextColor(StorageHostSnapshot state) {
        return storageValueColor(state.usedBytes, state.totalBytes);
    }

    private static int storageGaugeColor(StorageHostSnapshot state) {
        if (isInfiniteStorage(state)) return 0xD8CA6CFF;
        return 0xBF000000 | storageTextColor(state);
    }

    private static String storageStatus(StorageHostSnapshot state) {
        if (isInfiniteStorage(state)) return tr("gui.neoecoae.storage_ui.infinite_value");
        StorageHostSnapshot.TypeStat line = highestPressureLine(state);
        if (line == null) return tr("gui.neoecoae.storage_ui.status.stable");
        double value = ratio(line.usedBytes, line.totalBytes);
        if (line.totalBytes > 0L && value >= 1.0D) {
            return StatCollector.translateToLocalFormatted("gui.neoecoae.storage_ui.status.full", line.displayName);
        }
        if (value >= 0.9D) {
            return StatCollector.translateToLocalFormatted("gui.neoecoae.storage_ui.status.high", line.displayName);
        }
        if (value >= 0.75D) {
            return StatCollector.translateToLocalFormatted("gui.neoecoae.storage_ui.status.warning", line.displayName);
        }
        return tr("gui.neoecoae.storage_ui.status.stable");
    }

    private static int storageStatusColor(StorageHostSnapshot state) {
        StorageHostSnapshot.TypeStat line = highestPressureLine(state);
        return line == null ? MUTED : storageValueColor(line.usedBytes, line.totalBytes);
    }

    private static StorageHostSnapshot.TypeStat highestPressureLine(StorageHostSnapshot state) {
        StorageHostSnapshot.TypeStat best = null;
        double bestRatio = -1.0D;
        for (StorageHostSnapshot.TypeStat line : state.typeStats) {
            if (line.totalBytes <= 0L) continue;
            double value = ratio(line.usedBytes, line.totalBytes);
            if (value > bestRatio) {
                best = line;
                bestRatio = value;
            }
        }
        return best;
    }

    private static boolean isInfiniteStorage(StorageHostSnapshot state) {
        return "infinite".equals(state.hostMode) || "formed_infinite".equals(state.hostMode)
            || "domain_member".equals(state.hostMode)
            || "migrating".equals(state.hostMode)
            || "migrating_to_infinite".equals(state.hostMode);
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }

    private static String online(boolean value) {
        return value ? "Online" : "Offline";
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private static String number(long value) {
        return NUMBERS.format(Math.max(0L, value));
    }

    private static String big(BigInteger value) {
        return value == null ? "0" : NUMBERS.format(value);
    }

    private static <T extends TileEntity> T tile(NeoEcoGuiData data, Class<T> type) {
        TileEntity tile = data.getTileEntity();
        if (!type.isInstance(tile))
            throw new IllegalStateException("Expected " + type.getSimpleName() + " for " + data.getKind());
        return type.cast(tile);
    }

    private static <T> SnapshotState<T> snapshot(PanelSyncManager syncManager, String key, T empty,
        Supplier<T> supplier, SnapshotWriter<T> writer, SnapshotReader<T> reader, int interval) {
        SnapshotState<T> state = new SnapshotState<>(empty, reader);
        CachedSnapshotBytes<T> cache = new CachedSnapshotBytes<>(supplier, writer, interval);
        syncManager.syncValue(key, new ByteArraySyncValue(cache::get, state::accept));
        return state;
    }

    private interface SnapshotWriter<T> {

        void write(T value, ByteBuf buffer);
    }

    private interface SnapshotReader<T> {

        T read(ByteBuf buffer);
    }

    private interface ItemMutation {

        void apply(ItemStack stack);
    }

    /** Exact LDLib2 BORDER_THICK_RT1 sprite used by the pre-MUI2 host screens. */
    private static final class InfiniteSlotBorderDrawable implements IDrawable {

        private static final InfiniteSlotBorderDrawable INSTANCE = new InfiniteSlotBorderDrawable();

        @Override
        public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme) {
            Rectangle border = new Rectangle().color(0xFFB0B0C8);
            border.draw(context, x, y, width, 1, widgetTheme);
            border.draw(context, x, y + height - 1, width, 1, widgetTheme);
            border.draw(context, x, y, 1, height, widgetTheme);
            border.draw(context, x + width - 1, y, 1, height, widgetTheme);
        }
    }

    /** Exact LDLib2 BORDER_THICK_RT1 sprite used by the pre-MUI2 host screens. */
    private static final class ExactPanelBorderDrawable implements IDrawable {

        private static final ExactPanelBorderDrawable INSTANCE = new ExactPanelBorderDrawable();
        private static final String PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAACxIAAAsSAdLdfvwAAAB4SURBVDhPY2CAAl5ewf+kYJg+uObWxl6i8cVzVxCGgBjuzv7/E6LSwDQxGKQWbgjMAJDJ+lomGE5FxyA1MPUYBsDY+DC6etoYAPIjOh41gEQD8GF09SgGwBTgw1gTEsyZIEFiMEwt2ABYfkD2JzEYrhk5R5KCYfoArHmyRVtuUaoAAAAASUVORK5CYII=";

        private ResourceLocation location;

        @Override
        public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme) {
            ResourceLocation texture = location();
            int border = 6;
            int centerWidth = Math.max(0, width - border * 2);
            int centerHeight = Math.max(0, height - border * 2);
            GL11.glColor4f(1F, 1F, 1F, 1F);
            drawPart(texture, x, y, border, border, 0, 0, border, border);
            drawPart(texture, x + border, y, centerWidth, border, border, 0, 4, border);
            drawPart(texture, x + width - border, y, border, border, 10, 0, border, border);
            drawPart(texture, x, y + border, border, centerHeight, 0, border, border, 4);
            drawPart(texture, x + border, y + border, centerWidth, centerHeight, border, border, 4, 4);
            drawPart(texture, x + width - border, y + border, border, centerHeight, 10, border, border, 4);
            drawPart(texture, x, y + height - border, border, border, 0, 10, border, border);
            drawPart(texture, x + border, y + height - border, centerWidth, border, border, 10, 4, border);
            drawPart(texture, x + width - border, y + height - border, border, border, 10, 10, border, border);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }

        private ResourceLocation location() {
            if (this.location != null) return this.location;
            try {
                byte[] png = Base64.getDecoder()
                    .decode(PNG_BASE64);
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
                if (image == null || image.getWidth() != 16 || image.getHeight() != 16) {
                    throw new IOException("Invalid LDLib2 host panel border image");
                }
                this.location = Minecraft.getMinecraft()
                    .getTextureManager()
                    .getDynamicTextureLocation("neoecoae_host_panel_border", new DynamicTexture(image));
                return this.location;
            } catch (IOException | IllegalArgumentException exception) {
                throw new IllegalStateException("Unable to load LDLib2 host panel border", exception);
            }
        }

        private static void drawPart(ResourceLocation texture, int x, int y, int width, int height, int u, int v,
            int textureWidth, int textureHeight) {
            if (width <= 0 || height <= 0) return;
            GuiDraw.drawTexture(
                texture,
                x,
                y,
                x + width,
                y + height,
                u / 16F,
                v / 16F,
                (u + textureWidth) / 16F,
                (v + textureHeight) / 16F,
                true);
        }
    }

    private static final class HostProgressWidget extends Widget<HostProgressWidget> {

        private final DoubleSupplier progress;
        private final IntSupplier color;

        private HostProgressWidget(DoubleSupplier progress, IntSupplier color) {
            this.progress = progress;
            this.color = color;
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> entry) {
            WidgetTheme theme = getActiveWidgetTheme(entry, isHovering());
            int width = getArea().width;
            int height = getArea().height;
            ExactPanelBorderDrawable.INSTANCE.draw(context, 0, 0, width, height, theme);
            new Rectangle().color(PANEL_ALT)
                .draw(context, 2, 2, width - 4, height - 4, theme);
            int filled = (int) Math.round(Math.max(0D, Math.min(1D, this.progress.getAsDouble())) * (width - 6));
            if (filled > 0) {
                new Rectangle().color(this.color.getAsInt())
                    .draw(context, 3, 3, filled, height - 6, theme);
            }
        }
    }

    private static final class CraftingEnergyGaugeWidget extends Widget<CraftingEnergyGaugeWidget> {

        private final DoubleSupplier progress;
        private final IntSupplier color;

        private CraftingEnergyGaugeWidget(DoubleSupplier progress, IntSupplier color) {
            this.progress = progress;
            this.color = color;
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> entry) {
            WidgetTheme theme = getActiveWidgetTheme(entry, isHovering());
            int width = getArea().width;
            int height = getArea().height;
            ExactPanelBorderDrawable.INSTANCE.draw(context, 0, 0, width, height, theme);
            new Rectangle().color(PANEL_OUTER)
                .draw(context, 3, 3, width - 6, height - 6, theme);
            int filled = (int) Math.round(Math.max(0D, Math.min(1D, this.progress.getAsDouble())) * (height - 8));
            if (filled > 0) {
                int bottom = height - 4;
                new Rectangle().color(this.color.getAsInt())
                    .draw(context, 4, bottom - filled, width - 8, filled, theme);
                new Rectangle().color(0x70FFFFFF)
                    .draw(context, 4, bottom - filled, width - 8, Math.min(2, filled), theme);
            }
        }
    }

    private static final class CraftingCoolantGaugeWidget extends Widget<CraftingCoolantGaugeWidget> {

        private final DoubleSupplier progress;
        private final IntSupplier maxOverclock;

        private CraftingCoolantGaugeWidget(DoubleSupplier progress, IntSupplier maxOverclock) {
            this.progress = progress;
            this.maxOverclock = maxOverclock;
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> entry) {
            WidgetTheme theme = getActiveWidgetTheme(entry, isHovering());
            int width = getArea().width;
            int height = getArea().height;
            ExactPanelBorderDrawable.INSTANCE.draw(context, 0, 0, width, height, theme);
            new Rectangle().color(PANEL_OUTER)
                .draw(context, 3, 3, width - 6, height - 6, theme);
            int filled = (int) Math.round(Math.max(0D, Math.min(1D, this.progress.getAsDouble())) * (height - 6));
            if (filled <= 0) return;
            int fillY = height - 3 - filled;
            Fluid fluid = coolantFluid(this.maxOverclock.getAsInt());
            IIcon icon = fluid == null ? null : fluid.getStillIcon();
            new Rectangle().color(BLUE)
                .draw(context, 3, fillY, width - 6, filled, theme);
            if (icon == null) return;
            GL11.glColor4f(1F, 1F, 1F, 1F);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(TextureMap.locationBlocksTexture);
            for (int offsetY = 0; offsetY < filled; offsetY += 16) {
                int partHeight = Math.min(16, filled - offsetY);
                for (int offsetX = 0; offsetX < width - 6; offsetX += 16) {
                    int partWidth = Math.min(16, width - 6 - offsetX);
                    float minU = icon.getMinU();
                    float maxU = minU + (icon.getMaxU() - minU) * partWidth / 16F;
                    float minV = icon.getMinV();
                    float maxV = minV + (icon.getMaxV() - minV) * partHeight / 16F;
                    GuiDraw.drawTexture(
                        TextureMap.locationBlocksTexture,
                        3 + offsetX,
                        fillY + offsetY,
                        3 + offsetX + partWidth,
                        fillY + offsetY + partHeight,
                        minU,
                        minV,
                        maxU,
                        maxV,
                        true);
                }
            }
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }

        private static Fluid coolantFluid(int maxOverclock) {
            for (ECOCoolingRecipe recipe : ECOCoolingRecipes.all()) {
                if (recipe.getMaxOverclock() == maxOverclock) return recipe.getInputFluid();
            }
            return null;
        }
    }

    private static final class StorageGaugeWidget extends Widget<StorageGaugeWidget> {

        private final DoubleSupplier progress;
        private final IntSupplier color;

        private StorageGaugeWidget(DoubleSupplier progress, IntSupplier color) {
            this.progress = progress;
            this.color = color;
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> entry) {
            WidgetTheme theme = getActiveWidgetTheme(entry, isHovering());
            drawGauge(context, theme, this.progress.getAsDouble(), this.color.getAsInt());
        }

        private void drawGauge(ModularGuiContext context, WidgetTheme theme, double rawProgress, int color) {
            double progress = Math.max(0D, Math.min(1D, rawProgress));
            if (progress <= 0D) return;
            int width = getArea().width;
            int height = getArea().height;
            int capHeight = Math.min(8, height);
            int bodyHeight = Math.max(0, height - capHeight);
            int filled = (int) Math.round(bodyHeight * progress);
            int top = height - filled - capHeight;
            UITexture cap = NeoEcoTextures.STORAGE_GAUGE_CAP.withColorOverride(color);
            UITexture middle = NeoEcoTextures.STORAGE_GAUGE_MIDDLE.withColorOverride(color);
            cap.draw(context, 0, top, width, capHeight, theme);
            int middleEnd = height - capHeight / 2 + 1;
            for (int y = top + capHeight / 2 + 1; y < middleEnd; y++) {
                middle.draw(context, 0, y, width, 4, theme);
            }
            cap.draw(context, 0, height - capHeight, width, capHeight, theme);
        }
    }

    private static final class LineProgressWidget extends Widget<LineProgressWidget> {

        private final DoubleSupplier progress;
        private final IntSupplier color;

        private LineProgressWidget(DoubleSupplier progress, IntSupplier color) {
            this.progress = progress;
            this.color = color;
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> entry) {
            WidgetTheme theme = getActiveWidgetTheme(entry, isHovering());
            int width = getArea().width;
            int height = getArea().height;
            new Rectangle().color(PANEL_OUTER)
                .draw(context, 0, 0, width, height, theme);
            int filled = (int) Math.round(width * Math.max(0D, Math.min(1D, this.progress.getAsDouble())));
            if (filled > 0) new Rectangle().color(this.color.getAsInt())
                .draw(context, 0, 0, filled, height, theme);
        }
    }

    private static final class MeterWidget extends Widget<MeterWidget> {

        private final DoubleSupplier progress;
        private final IntSupplier color;
        private final boolean vertical;

        private MeterWidget(DoubleSupplier progress, IntSupplier color, boolean vertical) {
            this.progress = progress;
            this.color = color;
            this.vertical = vertical;
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> entry) {
            WidgetTheme theme = getActiveWidgetTheme(entry, isHovering());
            int width = getArea().width;
            int height = getArea().height;
            rectangle(context, theme, 0, 0, width, height, PANEL_EDGE);
            rectangle(context, theme, 1, 1, width - 2, height - 2, PANEL_OUTER);
            rectangle(context, theme, 2, 2, width - 4, height - 4, PANEL_ALT);

            double value = Math.max(0D, Math.min(1D, this.progress.getAsDouble()));
            int innerWidth = Math.max(0, width - 6);
            int innerHeight = Math.max(0, height - 6);
            if (this.vertical) {
                int filled = (int) Math.round(innerHeight * value);
                if (filled > 0) {
                    rectangle(context, theme, 3, height - 3 - filled, innerWidth, filled, this.color.getAsInt());
                    rectangle(context, theme, 3, height - 3 - filled, innerWidth, 1, 0x80FFFFFF);
                }
            } else {
                int filled = (int) Math.round(innerWidth * value);
                if (filled > 0) {
                    rectangle(context, theme, 3, 3, filled, innerHeight, this.color.getAsInt());
                    rectangle(context, theme, 3, 3, filled, 1, 0x80FFFFFF);
                }
            }
        }

        private static void rectangle(ModularGuiContext context, WidgetTheme theme, int x, int y, int width, int height,
            int color) {
            if (width > 0 && height > 0) new Rectangle().color(color)
                .draw(context, x, y, width, height, theme);
        }
    }

    private static final class UploadPanelHeightState {

        private static final int[] LIST_HEIGHTS = { 32, 88, 144 };
        private final List<IWidget> trackedWidgets = new ArrayList<>();
        private int mode = Config.getPatternUploadPanelHeight();

        private int get() {
            return this.mode;
        }

        private void set(int mode) {
            int normalized = Math.floorMod(mode, LIST_HEIGHTS.length);
            if (this.mode == normalized) return;
            this.mode = normalized;
            Config.setPatternUploadPanelHeight(normalized);
            for (IWidget widget : this.trackedWidgets) widget.scheduleResize();
        }

        private void cycle() {
            this.set(this.mode + 1);
        }

        private int listHeight() {
            return LIST_HEIGHTS[this.mode];
        }

        private int buttonY() {
            return 64 + this.listHeight();
        }

        private int panelHeight() {
            return 93 + this.listHeight();
        }

        private IDrawable icon() {
            switch (this.mode) {
                case 1:
                    return NeoEcoTextures.AE2_TERMINAL_STYLE_TALL;
                case 2:
                    return NeoEcoTextures.AE2_TERMINAL_STYLE_FULL;
                case 0:
                default:
                    return NeoEcoTextures.AE2_TERMINAL_STYLE_SMALL;
            }
        }

        private String tooltip() {
            String valueKey;
            switch (this.mode) {
                case 1:
                    valueKey = ButtonToolTips.TerminalStyle_Tall.getUnlocalized();
                    break;
                case 2:
                    valueKey = ButtonToolTips.TerminalStyle_Full.getUnlocalized();
                    break;
                case 0:
                default:
                    valueKey = ButtonToolTips.TerminalStyle_Small.getUnlocalized();
                    break;
            }
            return StatCollector.translateToLocal(ButtonToolTips.TerminalStyle.getUnlocalized()) + "\n"
                + StatCollector.translateToLocal(valueKey);
        }

        private void track(IWidget widget) {
            this.trackedWidgets.add(widget);
        }
    }

    private static final class UploadSnapshot {

        private static final int MAX_TARGETS = 127;
        private static final UploadSnapshot EMPTY = new UploadSnapshot(
            null,
            null,
            false,
            false,
            Collections.<UploadTargetSnapshot>emptyList());

        private final ItemStack pattern;
        private final ItemStack output;
        private final boolean processing;
        private final boolean uploaded;
        private final List<UploadTargetSnapshot> targets;

        private UploadSnapshot(ItemStack pattern, ItemStack output, boolean processing, boolean uploaded,
            List<UploadTargetSnapshot> targets) {
            this.pattern = pattern == null ? null : pattern.copy();
            this.output = output == null ? null : output.copy();
            this.processing = processing;
            this.uploaded = uploaded;
            this.targets = targets;
        }

        private static UploadSnapshot create(PatternUploadSession session) {
            if (session == null) return EMPTY;
            List<UploadTargetSnapshot> targets = new ArrayList<>();
            ItemStack pattern = session.getPattern();
            for (PatternUploadTarget target : session.getTargets()) {
                targets.add(
                    new UploadTargetSnapshot(
                        target.getId(),
                        target.getDisplayName(session.getRouteKey(), session.getPatternDetails()),
                        target.getDisplayIcon(session.getRouteKey(), session.getPatternDetails()),
                        target.getCircuit(session.getRouteKey(), session.getPatternDetails()),
                        target.getKind()
                            .ordinal(),
                        target.getDimension(),
                        target.getX(),
                        target.getY(),
                        target.getZ(),
                        target.getEmptySlots(),
                        target.getSlotCount(),
                        pattern != null && target.firstEmptySlot(pattern) == -1,
                        pattern != null && target.hasPattern(pattern),
                        pattern != null && target
                            .isExactMatch(session.isProcessing(), session.getPatternDetails(), session.getRouteKey()),
                        target.hasProgrammingCover(session.getRouteKey(), session.getPatternDetails())));
            }
            return new UploadSnapshot(
                session.getPattern(),
                session.getPatternOutput(),
                session.isProcessing(),
                session.isUploaded(),
                targets);
        }

        private static void write(UploadSnapshot value, ByteBuf buffer) {
            ByteBufUtils.writeItemStack(buffer, value.pattern);
            ByteBufUtils.writeItemStack(buffer, value.output);
            buffer.writeBoolean(value.processing);
            buffer.writeBoolean(value.uploaded);
            int count = Math.min(MAX_TARGETS, value.targets.size());
            buffer.writeByte(count);
            for (int i = 0; i < count; i++) {
                UploadTargetSnapshot target = value.targets.get(i);
                ByteBufUtils.writeUTF8String(buffer, target.id);
                ByteBufUtils.writeUTF8String(buffer, target.name);
                ByteBufUtils.writeItemStack(buffer, target.icon);
                ByteBufUtils.writeItemStack(buffer, target.circuit);
                buffer.writeByte(target.kind);
                buffer.writeInt(target.dimension);
                buffer.writeInt(target.x);
                buffer.writeInt(target.y);
                buffer.writeInt(target.z);
                buffer.writeShort(target.emptySlots);
                buffer.writeShort(target.capacity);
                buffer.writeBoolean(target.maxed);
                buffer.writeBoolean(target.hasPattern);
                buffer.writeBoolean(target.exact);
                buffer.writeBoolean(target.programmingCover);
            }
        }

        private static UploadSnapshot read(ByteBuf buffer) {
            ItemStack pattern = ByteBufUtils.readItemStack(buffer);
            ItemStack output = ByteBufUtils.readItemStack(buffer);
            boolean processing = buffer.readBoolean();
            boolean uploaded = buffer.readBoolean();
            int count = buffer.readUnsignedByte();
            List<UploadTargetSnapshot> targets = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                targets.add(
                    new UploadTargetSnapshot(
                        ByteBufUtils.readUTF8String(buffer),
                        ByteBufUtils.readUTF8String(buffer),
                        ByteBufUtils.readItemStack(buffer),
                        ByteBufUtils.readItemStack(buffer),
                        buffer.readUnsignedByte(),
                        buffer.readInt(),
                        buffer.readInt(),
                        buffer.readInt(),
                        buffer.readInt(),
                        buffer.readUnsignedShort(),
                        buffer.readUnsignedShort(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readBoolean()));
            }
            return new UploadSnapshot(pattern, output, processing, uploaded, targets);
        }
    }

    private static final class UploadTargetSnapshot {

        private final String id;
        private final String name;
        private final ItemStack icon;
        private final ItemStack circuit;
        private final int kind;
        private final int dimension;
        private final int x;
        private final int y;
        private final int z;
        private final int emptySlots;
        private final int capacity;
        private final boolean maxed;
        private final boolean hasPattern;
        private final boolean exact;
        private final boolean programmingCover;

        private UploadTargetSnapshot(String id, String name, ItemStack icon, ItemStack circuit, int kind, int dimension,
            int x, int y, int z, int emptySlots, int capacity, boolean maxed, boolean hasPattern, boolean exact,
            boolean programmingCover) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.circuit = circuit;
            this.kind = kind;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.emptySlots = emptySlots;
            this.capacity = capacity;
            this.maxed = maxed;
            this.hasPattern = hasPattern;
            this.exact = exact;
            this.programmingCover = programmingCover;
        }
    }

    private static final class SnapshotState<T> {

        private final SnapshotReader<T> reader;
        private T value;

        private SnapshotState(T empty, SnapshotReader<T> reader) {
            this.value = empty;
            this.reader = reader;
        }

        private T get() {
            return this.value;
        }

        private void accept(byte[] bytes) {
            if (bytes == null) return;
            ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
            try {
                this.value = this.reader.read(buffer);
            } finally {
                buffer.release();
            }
        }
    }

    private static final class CachedSnapshotBytes<T> {

        private final Supplier<T> supplier;
        private final SnapshotWriter<T> writer;
        private final int interval;
        private int ticks;
        private byte[] cached;

        private CachedSnapshotBytes(Supplier<T> supplier, SnapshotWriter<T> writer, int interval) {
            this.supplier = supplier;
            this.writer = writer;
            this.interval = Math.max(1, interval);
        }

        private byte[] get() {
            if (this.cached == null || this.ticks++ % this.interval == 0) {
                ByteBuf buffer = Unpooled.buffer();
                try {
                    this.writer.write(this.supplier.get(), buffer);
                    this.cached = new byte[buffer.readableBytes()];
                    buffer.getBytes(buffer.readerIndex(), this.cached);
                } finally {
                    buffer.release();
                }
            }
            return this.cached;
        }
    }

    private static final class PageState {

        private int page;

        private int get() {
            return this.page;
        }

        private void set(int page) {
            this.page = Math.max(0, page);
        }
    }

    private static final class PagedInventoryHandler implements IItemHandlerModifiable {

        private final IItemHandlerModifiable delegate;
        private final PageState page;
        private final int pageSize;

        private PagedInventoryHandler(IItemHandlerModifiable delegate, PageState page, int pageSize) {
            this.delegate = delegate;
            this.page = page;
            this.pageSize = pageSize;
        }

        private int actual(int slot) {
            return this.page.get() * this.pageSize + slot;
        }

        @Override
        public int getSlots() {
            return this.pageSize;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return this.delegate.getStackInSlot(this.actual(slot));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return this.delegate.insertItem(this.actual(slot), stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return this.delegate.extractItem(this.actual(slot), amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return this.delegate.getSlotLimit(this.actual(slot));
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return this.delegate.isItemValid(this.actual(slot), stack);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            this.delegate.setStackInSlot(this.actual(slot), stack);
        }

        @Override
        public boolean isSlotFromInventory(int slot, IInventory inventory, int inventorySlot) {
            return this.delegate.isSlotFromInventory(this.actual(slot), inventory, inventorySlot);
        }
    }
}
