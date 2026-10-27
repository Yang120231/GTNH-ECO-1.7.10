package cn.dancingsnow.neoecoae.platform.ui.workstation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.Stencil;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.Tag;
import cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar;
import cn.dancingsnow.neoecoae.platform.ui.NativeOwnedFluidSync;
import cn.dancingsnow.neoecoae.platform.ui.NativePageLayout;
import cn.dancingsnow.neoecoae.platform.ui.NativeUiDraw;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Native counterpart of LargeWorkstationUI; the physical host supplies all state and mutations. */
public final class NativeLargeWorkstationPages {

    public static final int PAGE_SIZE = 3, SNAPSHOT_PADDING = 2, SNAPSHOT_SIZE = 7;
    private static final String ATLAS = "neoecoae:textures/guis/workstation/integrated_working_station_widget.png";
    private static final int TEXT = 0xff303448, MUTED = 0xff596177, SUCCESS = 0xff226b58, WARNING = 0xff9b4438;

    private NativeLargeWorkstationPages() {}

    /** This boundary also accepts the separately migrated large host without importing its processing state machine. */
    @com.github.bsideup.jabel.Desugar
    public record Config(TileEntity tile, IntFunction<CompoundTag> snapshot, Consumer<UUID> returnTask,
        Runnable toggleOverclocked, Runnable toggleActiveCooling, Runnable togglePreheating, IFluidTank input,
        IFluidTank output) {}

    public static ModularPanel create(Config config, EntityPlayer player, PanelSyncManager sync) {
        NativePageLayout layout = new NativePageLayout("workstation/large");
        NativeLargeWorkstationSession session = new NativeLargeWorkstationSession(config);
        sync.syncValue("workstation.large", session);
        ModularPanel panel = ModularPanel.defaultPanel("neoecoae_large_workstation", layout.width(), layout.height())
            .background(
                NativePageLayout.texture(
                    "neoecoae:textures/guis/workstation/large_integrated_working_station.png",
                    0,
                    0,
                    253,
                    214));
        layout.children(panel, node -> {
            switch (node.get("custom")
                .getAsString()) {
                case "content":
                    return new Artwork(session);
                case "player_inventory":
                    ParentWidget<?> inventory = new ParentWidget<>();
                    for (var entry : node.getAsJsonArray("slots")) {
                        var slot = entry.getAsJsonObject();
                        inventory.child(
                            new cn.dancingsnow.neoecoae.platform.ui.NativeItemSlot()
                                .slot(
                                    new ModularSlot(
                                        new InvWrapper(player.inventory),
                                        slot.get("index")
                                            .getAsInt()))
                                .background(IDrawable.EMPTY)
                                .hoverBackground(IDrawable.EMPTY)
                                .pos(
                                    slot.get("left")
                                        .getAsInt() - 1,
                                    slot.get("top")
                                        .getAsInt() - 1));
                    }
                    return inventory;
                case "input_fluid":
                    return gauge(config.tile(), config.input());
                case "output_fluid":
                    return gauge(config.tile(), config.output());
                default:
                    throw new IllegalArgumentException("Unknown large workstation node: " + node);
            }
        });
        panel.child(
            new Tasks(session).pos(9, 38)
                .size(69, 76));
        panel.child(
            new QueueScrollbar(session).pos(79, 37)
                .size(8, 77));
        panel.child(
            new Toggle(session, true).pos(102, 89)
                .size(15, 26));
        panel.child(
            new Toggle(session, false).pos(146, 89)
                .size(15, 26));
        panel.child(
            region(() -> parallelTooltip(session.state())).pos(99, 35)
                .size(64, 32));
        panel.child(
            region(() -> temperatureTooltip(session.state())).pos(172, 69)
                .size(15, 55));
        panel.child(
            region(() -> energyTooltip(session.state())).pos(189, 69)
                .size(12, 55));
        panel.child(
            region(() -> temperatureTooltip(session.state())).pos(99, 117)
                .size(64, 10));
        panel.child(
            region(() -> temperatureTooltip(session.state())).pos(176, 130)
                .size(68, 24));
        panel.child(
            region(() -> coolingTooltip(session.state())).pos(176, 155)
                .size(68, 24));
        panel.child(
            region(() -> overclockTooltip(session.state())).pos(176, 180)
                .size(68, 24));
        var guide = sync.syncedPanel(
            "workstation.large.guide",
            true,
            (panelSync, handler) -> cn.dancingsnow.neoecoae.platform.ui.NativeGuidePage
                .create("large_integrated_working_station", handler));
        panel.child(
            NativeHostSideBar.left(
                false,
                NativeHostSideBar
                    .button(176, 0, () -> Component.translatable("gui.neoecoae.guide"), guide::openPanel)));
        panel.child(
            new ParentWidget<>().pos(251, 0)
                .size(23, 30)
                .background(
                    com.cleanroommc.modularui.drawable.UITexture
                        .fullImage("neoecoae:textures/gui/common/side_bar/mirrored_button_slot_up.png"))
                .child(
                    region(() -> statusTooltip(session.state())).pos(0, 3)
                        .size(21, 21)
                        .background(
                            com.cleanroommc.modularui.drawable.UITexture
                                .fullImage("neoecoae:textures/gui/widget/information.png"))
                        .hoverBackground(
                            com.cleanroommc.modularui.drawable.UITexture
                                .fullImage("neoecoae:textures/gui/widget/information_highlight.png"))));
        return panel;
    }

    private static FluidSlot gauge(TileEntity tile, IFluidTank tank) {
        return new FluidSlot().syncHandler(new NativeOwnedFluidSync(tile, tank, false, false))
            .alwaysShowFull(false)
            .contentPadding(0)
            .background(IDrawable.EMPTY)
            .hoverBackground(IDrawable.EMPTY);
    }

    private static Widget<?> region(Supplier<List<Component>> description) {
        return new Widget<>()
            .tooltipDynamic(
                lines -> {
                    for (Component line : description.get()) lines.add(line.getString())
                        .newLine();
                })
            .onUpdateListener(widget -> widget.markTooltipDirty());
    }

    private static final class Toggle extends ButtonWidget<Toggle> {

        private final NativeLargeWorkstationSession session;
        private final boolean overclock;

        Toggle(NativeLargeWorkstationSession session, boolean overclock) {
            this.session = session;
            this.overclock = overclock;
            background(IDrawable.EMPTY);
            onMousePressed(button -> {
                if (button == 0) session.action(
                    overclock ? NativeLargeWorkstationSession.OVERCLOCK : NativeLargeWorkstationSession.COOLING,
                    0);
                else if (button == 1 && overclock && thermal(session.state()))
                    session.action(NativeLargeWorkstationSession.PREHEAT, 0);
                else return false;
                return true;
            });
            tooltipDynamic(lines -> {
                for (Component line : overclock ? overclockTooltip(session.state()) : coolingTooltip(session.state()))
                    lines.add(line.getString())
                        .newLine();
                lines.add(tr("toggle_hint").getString());
            });
            onUpdateListener(widget -> widget.markTooltipDirty());
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            sprite(
                (session.state()
                    .getBoolean(overclock ? "overclocked" : "activeCooling") ? 0 : 30) + (isHovering() ? 15 : 0),
                135,
                15,
                26,
                0,
                0);
        }
    }

    private static final class Artwork extends Widget<Artwork> {

        private final NativeLargeWorkstationSession session;

        Artwork(NativeLargeWorkstationSession session) {
            this.session = session;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            CompoundTag s = session.state();
            text(
                Component.translatable("block.neoecoae.large_integrated_working_station"),
                25,
                9,
                115,
                .85f,
                TEXT,
                false);
            text(tr("queue_heading"), 9, 28, 72, .7f, TEXT, false);
            // This port excludes module cards; use the existing circuit board for machine status.
            text(tr("machine_status"), 176, 28, 69, .7f, TEXT, false);
            text(status(s), 181, 41, 35, .75f, 0xfff2f2f2, false);
            text(tr("task_count", s.getInt("totalTasks")), 181, 53, 35, .65f, 0xffbec6dd, false);
            int total = s.getInt("totalTasks"), offset = s.getInt("offset");
            text(
                total == 0 ? tr("task_count", 0)
                    : tr("queue_range", offset + 1, Math.min(total, offset + PAGE_SIZE), total),
                9,
                119,
                77,
                .65f,
                MUTED,
                true);
            text(tr("parallel"), 103, 39, 57, .7f, TEXT, false);
            text(
                Component.literal(number(s.getLong("parallelUsed")) + " / " + number(s.getLong("parallelCapacity"))),
                103,
                49,
                57,
                .8f,
                TEXT,
                false);
            int width = (int) Math.round(57 * ratio(s.getLong("parallelUsed"), s.getLong("parallelCapacity")));
            if (width > 0) sprite(18, 161, width, 7, 103, 59);
            text(tr("controls_heading"), 102, 72, 59, .7f, TEXT, false);
            text(tr("overclock_short"), 102, 82, 15, .65f, TEXT, true);
            text(tr("cooling"), 146, 82, 15, .65f, TEXT, true);
            GuiDraw.drawRect(119, 90, 25, 24, 0xff9b9eb0);
            GuiDraw.drawRect(120, 91, 23, 10, s.getBoolean("overclocked") ? 0xffd4e6de : 0xffd5d6df);
            GuiDraw.drawRect(120, 102, 23, 11, 0xffdadbe4);
            text(tr(s.getBoolean("overclocked") ? "enabled" : "disabled"), 120, 94, 23, .6f, MUTED, true);
            text(tr(thermal(s) ? "thermal_mode" : "coolant_mode"), 120, 105, 23, .55f, MUTED, true);
            text(thermal(s) ? tr("current_band", band(s)) : tr("coolant_mode"), 99, 119, 64, .65f, MUTED, true);
            text(tr("thermal_short"), 172, 70, 15, .65f, TEXT, true);
            text(tr("energy_short"), 189, 70, 12, .65f, TEXT, true);
            text(tr("input_short"), 203, 70, 20, .7f, TEXT, true);
            text(tr("output_short"), 226, 70, 20, .7f, TEXT, true);
            int heat = 7 + (int) Math.round(39 * ratio(thermal(s) ? s.getDouble("temperature") : 0, 100));
            sprite(8, 207 - heat, 10, heat, 175, 124 - heat);
            int energy = (int) Math.round(46 * ratio(s.getDouble("energyUsage"), s.getDouble("energyScale")));
            if (energy > 0) sprite(0, 207 - energy, 8, energy, 192, 124 - energy);
            double temperature = thermal(s) ? s.getDouble("temperature") : 0;
            sprite(0, temperature < 20 ? 87 : temperature < 50 ? 99 : temperature < 80 ? 111 : 123, 12, 12, 180, 136);
            int tier = s.getInt("coolantTier");
            if (tier > 0) sprite(12, tier == 6 ? 99 : tier == 9 ? 111 : 87, 12, 12, 180, 161);
            if (s.getBoolean("overclocked")) sprite(24, 87, 12, 12, 180, 186);
            text(
                thermal(s) ? band(s) : tr("coolant_mode"),
                198,
                134,
                44,
                .75f,
                s.getBoolean("overheated") ? WARNING : TEXT,
                false);
            text(
                thermal(s) ? tr("temperature_short", decimal(temperature)) : tr("thermal_disabled_short"),
                198,
                144,
                44,
                .65f,
                MUTED,
                false);
            text(coolant(s), 198, 159, 44, .75f, TEXT, false);
            text(
                thermal(s) ? tr("cooling_rate_short", decimal(s.getDouble("coolingPerSecond")))
                    : tr("thermal_disabled_short"),
                198,
                169,
                44,
                .65f,
                MUTED,
                false);
            text(tr(s.getBoolean("overclocked") ? "overclock_on" : "overclock_off"), 198, 184, 44, .75f, TEXT, false);
            text(tr("parallel_short", number(s.getLong("parallelCapacity"))), 198, 194, 44, .65f, MUTED, false);
        }
    }

    private static final class Tasks extends ButtonWidget<Tasks> {

        private final NativeLargeWorkstationSession session;
        private final NativeLargeWorkstationScroll scroll;
        private CompoundTag received;
        private List<CompoundTag> rows = new ArrayList<>();
        private long lastFrame;

        Tasks(NativeLargeWorkstationSession session) {
            this.session = session;
            this.scroll = scrollFor(session);
            background(IDrawable.EMPTY);
            onMouseScrolled((direction, amount) -> {
                scroll.scrollBy((direction.modifier > 0 ? -1 : 1) * amount);
                request(session, scroll);
                return true;
            });
            onMousePressed(button -> {
                CompoundTag row = hovered();
                int y = (int) Math.floor(getContext().getMouseY() - scroll.rowShift());
                if (button != 0 || row == null
                    || !row.getBoolean("returnable")
                    || getContext().getMouseX() < 56
                    || y % 26 < 13
                    || y % 26 >= 24) return false;
                scroll.stop();
                session.action(NativeLargeWorkstationSession.RETURN, row.getInt("token"));
                return true;
            });
            tooltipDynamic(lines -> {
                CompoundTag row = hovered();
                if (row == null) {
                    lines.add(tr("queue_empty_hint").getString());
                    return;
                }
                GenericStack output = output(row);
                lines.add(
                    output == null ? tr("empty_task").getString()
                        : output.what()
                            .getDisplayName());
                lines.add(
                    tr(
                        output != null && output.what() instanceof AEFluidKey ? "task_fluid_amount" : "task_amount",
                        String.format(Locale.ROOT, "%,d", amount(row, output))).getString());
                lines.add(
                    Component.translatable(row.getString("status"))
                        .getString());
                if (!row.getString("detail")
                    .isEmpty())
                    lines.add(
                        Component.translatable(row.getString("detail"))
                            .getString());
                lines.add(tr("progress", Math.max(0, Math.min(200, row.getInt("progress"))) / 2).getString());
                if (row.getInt("processingTicks") > 0) lines
                    .add(tr("task_duration", row.getInt("elapsedTicks"), row.getInt("processingTicks")).getString());
                if (row.getBoolean("returnable")) lines.add(tr("task_return_hint").getString());
            });
            onUpdateListener(widget -> widget.markTooltipDirty());
        }

        private CompoundTag hovered() {
            int index = (int) Math.floor((getContext().getMouseY() - scroll.rowShift()) / 26);
            return index < 0 || index >= rows.size() ? null : rows.get(index);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            if (received != session.state()) {
                received = session.state();
                var entries = received.getList("tasks", Tag.TAG_COMPOUND);
                if (scroll.receive(
                    received.getInt("totalTasks"),
                    received.contains("taskOffset") ? received.getInt("taskOffset") : received.getInt("offset"),
                    entries.size())) {
                    rows = new ArrayList<>();
                    for (int i = 0; i < Math.min(SNAPSHOT_SIZE, entries.size()); i++) rows.add(entries.getCompound(i));
                }
            }
            long now = System.nanoTime();
            if (lastFrame != 0) scroll.advance((now - lastFrame) / 1_000_000_000d);
            lastFrame = now;
            request(session, scroll);
            Stencil.apply(0, 0, 69, 76, context);
            try {
                if (rows.isEmpty()) {
                    text(tr("empty_task"), 0, 27, 69, .8f, TEXT, true);
                    text(tr("queue_empty_hint"), 0, 39, 69, .65f, MUTED, true);
                }
                for (int i = 0; i < rows.size(); i++) {
                    int y = (int) Math.round(i * 26 + scroll.rowShift());
                    if (y >= 76 || y + 24 <= 0) continue;
                    CompoundTag row = rows.get(i);
                    GenericStack output = output(row);
                    sprite(0, 62, 69, 24, 0, y);
                    if (output != null && output.what() instanceof AEItemKey) NativeUiDraw
                        .item(((AEItemKey) output.what()).toStack(), 4, y + 4, context.getCurrentDrawingZ());
                    else if (output != null && output.what() instanceof AEFluidKey) GuiDraw.drawFluidTexture(
                        ((AEFluidKey) output.what()).toStack(1000),
                        4,
                        y + 4,
                        16,
                        16,
                        context.getCurrentDrawingZ());
                    text(
                        output == null ? tr("empty_task")
                            : Component.literal(
                                output.what()
                                    .getDisplayName()),
                        24,
                        y + 1,
                        43,
                        .7f,
                        TEXT,
                        false);
                    text(Component.literal("×" + number(amount(row, output))), 24, y + 8, 23, .6f, MUTED, false);
                    String status = row.getString("status");
                    boolean problem = row.getBoolean("current") && !status.isEmpty()
                        && !status.endsWith(".none")
                        && !status.endsWith(".task_output");
                    text(
                        tr(
                            !row.getBoolean("current") ? "task_queued"
                                : row.getInt("progress") >= 200 ? "task_output"
                                    : problem ? "status_paused" : "status_running"),
                        49,
                        y + 8,
                        18,
                        .55f,
                        problem ? WARNING : TEXT,
                        false);
                    GuiDraw.drawRect(24, y + 17, 30, 4, 0xff413f54);
                    GuiDraw.drawRect(25, y + 18, 28, 2, 0xff696d88);
                    int width = row.getInt("progress") <= 0 ? 0
                        : Math.max(1, (int) Math.round(28 * ratio(row.getInt("progress"), 200)));
                    if (width > 0)
                        GuiDraw.drawRect(25, y + 18, width, 2, row.getInt("progress") >= 200 ? 0xff57cdae : 0xffed7ccc);
                    if (row.getBoolean("returnable")) sprite(0, 6, 12, 11, 56, y + 13);
                }
            } finally {
                Stencil.remove();
            }
        }
    }

    // The scrollbar and viewport share one per-page animation state, never a static host/world reference.
    private static NativeLargeWorkstationScroll scrollFor(NativeLargeWorkstationSession session) {
        return session.scroll;
    }

    private static void request(NativeLargeWorkstationSession session, NativeLargeWorkstationScroll scroll) {
        int offset = scroll.requestedRow();
        if (session.requestedOffset != offset) {
            session.requestedOffset = offset;
            session.action(NativeLargeWorkstationSession.PAGE, offset);
        }
    }

    private static final class QueueScrollbar extends ButtonWidget<QueueScrollbar> {

        private final NativeLargeWorkstationSession session;
        private final NativeLargeWorkstationScroll scroll;
        private float grab;

        QueueScrollbar(NativeLargeWorkstationSession session) {
            this.session = session;
            scroll = scrollFor(session);
            background(IDrawable.EMPTY);
            onMouseScrolled((direction, amount) -> {
                scroll.scrollBy((direction.modifier > 0 ? -1 : 1) * amount);
                request(session, scroll);
                return true;
            });
        }

        @Override
        public Result onMousePressed(int button) {
            if (button != 0 || scroll.maximum() == 0) return Result.IGNORE;
            float y = getContext().getMouseY(), top = (float) (62 * scroll.thumbOffset() / scroll.maximum());
            grab = y >= top && y < top + 15 ? y - top : 7.5f;
            drag();
            return Result.SUCCESS;
        }

        private void drag() {
            scroll.jumpTo((getContext().getMouseY() - grab) / 62 * scroll.maximum());
            request(session, scroll);
        }

        @Override
        public void onMouseDrag(int button, long elapsed) {
            if (button == 0) drag();
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            int y = scroll.maximum() == 0 ? 0 : (int) Math.round(62 * scroll.thumbOffset() / scroll.maximum());
            sprite(scroll.maximum() == 0 ? 75 : 69, 62, 6, 15, 1, y);
        }
    }

    private static GenericStack output(CompoundTag row) {
        if (row.contains("output")) return GenericStack.readTag(RegistryAccess.EMPTY, row.getCompound("output"));
        var item = ItemStacks.parseOptional(RegistryAccess.EMPTY, row.getCompound("item"));
        if (!ItemStacks.isEmpty(item)) return new GenericStack(AEItemKey.of(item), row.getLong("amount"));
        FluidStack fluid = FluidStack.loadFluidStackFromNBT(
            row.getCompound("fluid")
                .nativeTag());
        return fluid == null ? null : new GenericStack(AEFluidKey.of(fluid), row.getLong("amount"));
    }

    private static long amount(CompoundTag row, GenericStack output) {
        return row.contains("amount") ? row.getLong("amount") : output == null ? 0 : output.amount();
    }

    private static List<Component> parallelTooltip(CompoundTag s) {
        return java.util.Arrays.asList(
            tr("parallel"),
            tr("parallel_used", number(s.getLong("parallelUsed"))),
            tr("parallel_capacity", number(s.getLong("parallelCapacity"))),
            tr("parallel_hint"));
    }

    private static List<Component> energyTooltip(CompoundTag s) {
        return java.util.Arrays.asList(
            tr("energy"),
            tr("energy_value", decimal(s.getDouble("energyUsage"))),
            tr("energy_stored", number(s.getDouble("energyStored")), number(s.getDouble("energyCapacity"))),
            tr("energy_hint"));
    }

    private static List<Component> temperatureTooltip(CompoundTag s) {
        if (!thermal(s)) return java.util.Arrays.asList(
            tr("thermal"),
            Component.translatable("gui.neoecoae.large_integrated_working_station.temperature_disabled"),
            tr("thermal_disabled_hint"));
        List<Component> lines = new ArrayList<>();
        lines.add(tr("temperature_setting"));
        lines.add(tr("temperature_value", decimal(s.getDouble("temperature"))));
        lines.add(tr("current_band", band(s)));
        lines.add(
            tr("temperature_net_rate", String.format(Locale.ROOT, "%+.2f", s.getDouble("temperatureChangePerSecond"))));
        lines.add(tr("temperature_processing_rate", decimal(s.getDouble("processingHeatPerSecond"))));
        lines.add(tr("temperature_preheat_rate", decimal(s.getDouble("preheatPerSecond"))));
        lines.add(tr("temperature_cooling_rate", decimal(s.getDouble("coolingPerSecond"))));
        lines.add(tr("temperature_natural_rate", decimal(s.getDouble("naturalCoolingPerSecond"))));
        lines.add(tr("temperature_automatic_hint"));
        lines.add(tr("temperature_slowing_hint"));
        if (s.getBoolean("overheated")) lines.add(
            tr(
                "recovery_progress",
                decimal(s.getInt("recoveryTicks") / 20d),
                cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationThermal.RECOVERY_TICKS / 20));
        else lines.add(tr("temperature_overheat_hint"));
        return lines;
    }

    private static List<Component> overclockTooltip(CompoundTag s) {
        List<Component> lines = new ArrayList<>();
        lines.add(tr("overclock"));
        lines.add(tr(s.getBoolean("overclocked") ? "enabled" : "disabled"));
        lines.add(tr("parallel_capacity", number(s.getLong("parallelCapacity"))));
        lines.add(tr(thermal(s) ? "overclock_thermal_hint" : "overclock_coolant_hint"));
        lines.add(tr("overclock_energy_hint"));
        if (thermal(s)) {
            lines.add(tr("preheat_right_click_hint"));
            lines.add(tr(s.getBoolean("preheating") ? "preheat_on" : "preheat_off"));
            lines.add(tr("preheat_stop_hint"));
        }
        return lines;
    }

    private static List<Component> coolingTooltip(CompoundTag s) {
        List<Component> lines = new ArrayList<>();
        lines.add(tr("active_cooling"));
        lines.add(tr(s.getBoolean("activeCooling") ? "enabled" : "disabled"));
        lines.add(tr("coolant_value", coolant(s)));
        if (thermal(s)) lines.add(tr("cooling_rate", decimal(s.getDouble("coolingPerSecond"))));
        lines.add(tr(thermal(s) ? "cooling_thermal_hint" : "cooling_coolant_hint"));
        String problem = s.getString("coolingProblem");
        if (s.getBoolean("activeCooling") && !problem.isEmpty() && !problem.endsWith(".none"))
            lines.add(Component.translatable(problem));
        lines.add(tr("cooling_preheat_hint"));
        return lines;
    }

    private static List<Component> statusTooltip(CompoundTag s) {
        List<Component> lines = new ArrayList<>();
        lines.add(tr("machine_status"));
        lines.add(status(s));
        if (!s.getString("pause")
            .isEmpty()) lines.add(Component.translatable(s.getString("pause")));
        lines.add(tr("task_count", s.getInt("totalTasks")));
        return lines;
    }

    private static Component status(CompoundTag s) {
        String pause = s.getString("pause");
        return tr(
            s.getBoolean("overheated") ? "status_overheated"
                : s.getBoolean("preheating") ? "preheat_on"
                    : s.getInt("totalTasks") == 0 ? "status_idle"
                        : !pause.isEmpty() && !pause.endsWith(".none") ? "status_paused" : "status_running");
    }

    private static boolean thermal(CompoundTag s) {
        return !s.contains("temperatureEnabled") || s.getBoolean("temperatureEnabled");
    }

    private static Component band(CompoundTag s) {
        double value = s.getDouble("temperature");
        return s.getBoolean("overheated") ? tr("status_overheated")
            : value < 20 ? tr("band_regular") : tr("band", value < 50 ? 4 : value < 80 ? 6 : 9);
    }

    private static Component coolant(CompoundTag s) {
        switch (s.getInt("coolantTier")) {
            case 2:
                return tr("cooling_water");
            case 6:
                return tr("cooling_sodium");
            case 9:
                return tr("cooling_cryotheum");
            default:
                return tr("no_coolant");
        }
    }

    private static Component tr(String suffix, Object... args) {
        return Component.translatable("gui.neoecoae.workstation." + suffix, args);
    }

    private static double ratio(double value, double maximum) {
        return Double.isFinite(value) && Double.isFinite(maximum) && maximum > 0
            ? Math.max(0, Math.min(1, value / maximum))
            : 0;
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", Double.isFinite(value) ? value : 0);
    }

    private static String number(double value) {
        if (!Double.isFinite(value) || value <= 0) return "0";
        String[] units = { "", "k", "M", "G", "T", "P", "E" };
        int unit = 0;
        while (value >= 1000 && unit < units.length - 1) {
            value /= 1000;
            unit++;
        }
        return (unit == 0 ? Long.toString((long) value)
            : String.format(Locale.ROOT, "%.1f", value)
                .replaceFirst("\\.0$", ""))
            + units[unit];
    }

    @SideOnly(Side.CLIENT)
    private static void sprite(int u, int v, int width, int height, int x, int y) {
        NativeUiDraw.sprite(ATLAS, u, v, width, height, x, y, width, height);
    }

    @SideOnly(Side.CLIENT)
    private static void text(Component value, int x, int y, int width, float scale, int color, boolean centered) {
        NativeUiDraw.text(value, x, y, width, scale, color, centered, false);
    }
}
