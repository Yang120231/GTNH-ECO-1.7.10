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
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.Stencil;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationOverclock;
import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationThermal;
import cn.dancingsnow.neoecoae.gui.common.HostText;
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
import cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle;
import cn.dancingsnow.neoecoae.platform.ui.crafting.NativeListCard;
import cn.dancingsnow.neoecoae.platform.ui.crafting.ProcessingTooltip;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Native counterpart of LargeWorkstationUI; the physical host supplies all state and mutations. */
public final class NativeLargeWorkstationPages {

    public static final int PAGE_SIZE = 3, SNAPSHOT_PADDING = 2, SNAPSHOT_SIZE = 7;
    private static final String ATLAS = "neoecoae:textures/guis/workstation/integrated_working_station_widget.png";
    private static final int TEXT = 0xff303448, MUTED = 0xff596177, SUCCESS = 0xff226b58, WARNING = 0xff9b4438;

    private static final int[] BAND_COLORS = { 0xFF8A7FA0, 0xFFA48AD0, 0xFFD08AC0, 0xFFF0A080 };

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
                    return gauge(config.tile(), config.input(), "input");
                case "output_fluid":
                    return gauge(config.tile(), config.output(), "output");
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
            cardRegion(() -> temperatureCard(session.state())).pos(172, 69)
                .size(15, 55));
        panel.child(
            cardRegion(() -> energyCard(session.state())).pos(189, 69)
                .size(12, 55));
        panel.child(
            cardRegion(() -> temperatureCard(session.state())).pos(99, 117)
                .size(64, 10));
        panel.child(
            cardRegion(() -> temperatureCard(session.state())).pos(176, 130)
                .size(68, 24));
        panel.child(
            cardRegion(() -> coolingCard(session.state(), false)).pos(176, 155)
                .size(68, 24));
        panel.child(
            cardRegion(() -> overclockCard(session.state(), false)).pos(176, 180)
                .size(68, 24));
        panel.child(
            region(
                () -> java.util.Arrays.asList(
                    Component.translatable("block.neoecoae.large_integrated_working_station")
                        .withStyle(EnumChatFormatting.YELLOW),
                    pauseText(session.state()))).pos(25, 6)
                        .size(115, 14));
        panel.child(
            region(() -> statusTooltip(session.state())).pos(6, 6)
                .size(15, 15));
        panel.child(
            region(() -> statusTooltip(session.state())).pos(176, 34)
                .size(69, 31));
        var guide = sync.syncedPanel(
            "workstation.large.guide",
            true,
            (panelSync, handler) -> cn.dancingsnow.neoecoae.platform.ui.NativeGuidePage
                .create("large_integrated_working_station", handler));
        panel.child(
            NativeHostSideBar.left(
                false,
                NativeHostSideBar.button(
                    176,
                    0,
                    () -> Component.translatable("gui.neoecoae.guide"),
                    () -> cn.dancingsnow.neoecoae.platform.ui.NativeGuidePage
                        .open("large_integrated_working_station", player, guide::openPanel))));
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

    private static FluidSlot gauge(TileEntity tile, IFluidTank tank, String role) {
        return new FluidSlot() {

            @Override
            protected void addToolTip(RichTooltip tooltip) {
                tooltip.addLine(
                    Component.translatable("gui.neoecoae.large_integrated_working_station." + role + "_fluid")
                        .withStyle(EnumChatFormatting.YELLOW)
                        .nativeText()
                        .getFormattedText());
                super.addToolTip(tooltip);
                tooltip.addLine(
                    tr(role + "_fluid_hint").withStyle(EnumChatFormatting.GRAY)
                        .nativeText()
                        .getFormattedText());
            }
        }.syncHandler(new NativeOwnedFluidSync(tile, tank, false, false))
            .alwaysShowFull(false)
            .contentPadding(0)
            .background(IDrawable.EMPTY)
            .hoverBackground(IDrawable.EMPTY);
    }

    private static Widget<?> cardRegion(Supplier<NativeListCard.Builder> content) {
        return new Widget<>().tooltip(new NativeListCard(content));
    }

    private static Widget<?> region(Supplier<List<Component>> description) {
        return new Widget<>()
            .tooltipDynamic(
                lines -> {
                    for (Component line : description.get()) lines.add(
                        line.nativeText()
                            .getFormattedText())
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
            tooltip(
                new NativeListCard(
                    () -> overclock ? overclockCard(session.state(), true) : coolingCard(session.state(), true)));
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
            NativeUiStyle.plainHover(this);
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
                int y = (int) Math.floor(getContext().getMouseY() - scroll.rowShift());
                boolean returning = row != null && row.getBoolean("returnable")
                    && getContext().getMouseX() >= 56
                    && y % 26 >= 13
                    && y % 26 < 24;
                List<Component> content = taskTooltip(row, returning);
                for (int i = 0; i < content.size(); i++) {
                    if (i > 0) lines.newLine();
                    lines.add(
                        content.get(i)
                            .nativeText()
                            .getFormattedText());
                }
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
            NativeUiStyle.plainHover(this);
            tooltipDynamic(lines -> {
                lines.add(
                    tr("queue_heading").withStyle(EnumChatFormatting.YELLOW)
                        .nativeText()
                        .getFormattedText())
                    .newLine();
                int total = session.state()
                    .getInt("totalTasks");
                lines
                    .add(
                        (total == 0 ? tr("task_count", 0)
                            : tr("queue_range", scroll.firstVisibleRow() + 1, scroll.lastVisibleRow(), total))
                                .getString())
                    .newLine();
                lines.add(
                    tr(total <= PAGE_SIZE ? "queue_all_visible" : "queue_scroll_hint")
                        .withStyle(EnumChatFormatting.GRAY)
                        .nativeText()
                        .getFormattedText());
            });
            onUpdateListener(widget -> widget.markTooltipDirty());
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

    private static List<Component> taskTooltip(CompoundTag row, boolean returning) {
        if (row == null) return java.util.Arrays.asList(
            tr("empty_task").withStyle(EnumChatFormatting.YELLOW),
            tr("queue_empty_hint").withStyle(EnumChatFormatting.GRAY));
        GenericStack output = output(row);
        Component title = output == null ? tr("empty_task")
            : Component.literal(
                output.what()
                    .getDisplayName());
        if (returning) return java.util.Arrays.asList(
            tr("return_task").withStyle(EnumChatFormatting.YELLOW),
            title,
            tr("return_task_hint").withStyle(EnumChatFormatting.GRAY));
        List<Component> lines = new ArrayList<>();
        lines.add(title.withStyle(EnumChatFormatting.YELLOW));
        lines.add(
            tr(
                output != null && output.what() instanceof AEFluidKey ? "task_fluid_amount" : "task_amount",
                HostText.expandedNumber(amount(row, output))));
        boolean current = row.getBoolean("current");
        String status = row.getString("status");
        boolean problem = current && !status.isEmpty() && !status.endsWith(".none") && !status.endsWith(".task_output");
        lines.add(
            Component.translatable(status)
                .withStyle(
                    !current ? EnumChatFormatting.YELLOW
                        : row.getInt("progress") >= 200 ? EnumChatFormatting.GRAY
                            : problem ? EnumChatFormatting.GOLD : EnumChatFormatting.GREEN));
        if (!row.getString("detail")
            .isEmpty()) lines.add(Component.translatable(row.getString("detail")));
        lines.add(tr("progress", Math.max(0, Math.min(200, row.getInt("progress"))) / 2));
        if (row.getInt("processingTicks") > 0) lines.add(
            tr(
                "task_duration",
                HostText.expandedNumber(row.getInt("elapsedTicks")),
                HostText.expandedNumber(row.getInt("processingTicks"))));
        if (row.getBoolean("returnable")) lines.add(tr("task_return_hint").withStyle(EnumChatFormatting.GRAY));
        return lines;
    }

    private static List<Component> parallelTooltip(CompoundTag s) {
        return java.util.Arrays.asList(
            tr("parallel").withStyle(EnumChatFormatting.YELLOW),
            tr("parallel_used", HostText.expandedNumber(s.getLong("parallelUsed"))),
            tr("parallel_capacity", HostText.expandedNumber(s.getLong("parallelCapacity"))),
            tr("parallel_hint").withStyle(EnumChatFormatting.GRAY));
    }

    private static NativeListCard.Builder energyCard(CompoundTag s) {
        long stored = (long) s.getDouble("energyStored"), capacity = (long) s.getDouble("energyCapacity");
        double fraction = ratio(stored, capacity);
        return NativeListCard.builder()
            .header(NativeListCard.Glyph.BATTERY, card("energy_monitor"), card("last_second"), ProcessingTooltip.MUTED)
            .metricUnit(
                card("actual_energy"),
                Component.literal(number(s.getDouble("energyUsage"))),
                ProcessingTooltip.PRIMARY,
                Component.literal("AE/t"),
                ProcessingTooltip.WARN)
            .row(
                null,
                ProcessingTooltip.MUTED,
                card("internal_storage"),
                card("ae_stored", HostText.ae2Amount(stored), HostText.ae2Amount(capacity)),
                ProcessingTooltip.PRIMARY)
            .bar(fraction, ProcessingTooltip.WARN)
            .row(
                null,
                ProcessingTooltip.MUTED,
                Component.literal(HostText.percent(fraction))
                    .withColor(ProcessingTooltip.MUTED),
                card("ae_amount", HostText.expandedNumber(capacity)),
                ProcessingTooltip.MUTED)
            .divider()
            .wrapped(NativeListCard.Glyph.INFO, tr("energy_hint"), ProcessingTooltip.MUTED);
    }

    private static NativeListCard.Builder overclockCard(CompoundTag s, boolean toggle) {
        boolean enabled = s.getBoolean("overclocked");
        boolean preheat = thermal(s);
        var card = NativeListCard.builder()
            .header(
                NativeListCard.Glyph.BOLT,
                tr("overclock"),
                tr(enabled ? "enabled" : "disabled"),
                enabled ? ProcessingTooltip.OK : ProcessingTooltip.MUTED)
            .largeValue(
                card("parallel_limit"),
                HostText.expandedNumber(s.getLong("parallelCapacity")),
                ProcessingTooltip.PRIMARY)
            .row(null, ProcessingTooltip.MUTED, card("recipe_energy"), Component.literal("×1"), ProcessingTooltip.OK);
        if (preheat) {
            boolean running = s.getBoolean("preheating");
            card.panel(
                NativeListCard.Glyph.THERMOMETER,
                card("auto_preheat"),
                card(running ? "preheat_running" : "preheat_off"),
                running ? ProcessingTooltip.WARN : ProcessingTooltip.MUTED,
                java.util.Arrays.asList(
                    new NativeListCard.PanelLine(
                        null,
                        card("preheat_max_energy"),
                        ProcessingTooltip.MUTED,
                        card("ae_rate", HostText.expandedNumber((long) LargeWorkstationThermal.PREHEAT_AE_PER_TICK)),
                        ProcessingTooltip.WARN),
                    new NativeListCard.PanelLine(
                        null,
                        card("preheat_stop"),
                        ProcessingTooltip.MUTED,
                        Component.empty(),
                        ProcessingTooltip.MUTED)));
        }
        card.wrapped(
            NativeListCard.Glyph.INFO,
            tr(thermal(s) ? "overclock_thermal_hint" : "overclock_coolant_hint"),
            ProcessingTooltip.MUTED);
        if (toggle) {
            card.divider()
                .text(
                    mouseHint("mouse_left", "toggle_overclock"),
                    preheat ? mouseHint("mouse_right", "toggle_preheat") : Component.empty(),
                    ProcessingTooltip.MUTED);
        }
        return card;
    }

    private static Component mouseHint(String button, String action) {
        return Component.empty()
            .append(
                card(button).withStyle(EnumChatFormatting.BOLD)
                    .withColor(ProcessingTooltip.PRIMARY))
            .append(" ")
            .append(card(action).withColor(ProcessingTooltip.MUTED));
    }

    /** Temperature and automatic band card drawn inside the vanilla tooltip frame. */
    private static NativeListCard.Builder temperatureCard(CompoundTag s) {
        if (!thermal(s)) {
            return NativeListCard.builder()
                .header(
                    NativeListCard.Glyph.THERMOMETER,
                    tr("thermal"),
                    Component.translatable("gui.neoecoae.large_integrated_working_station.temperature_disabled"),
                    ProcessingTooltip.MUTED)
                .text(tr("thermal_disabled_hint"), Component.empty(), ProcessingTooltip.MUTED);
        }
        double temperature = s.getDouble("temperature");
        boolean overheated = s.getBoolean("overheated");
        var profile = LargeWorkstationOverclock.forTemperature(true, temperature);
        long limit = overheated ? 0 : profile.maxParallelism();
        int[] tiers = { 2, 6, 9 };
        List<NativeListCard.Band> bands = new ArrayList<>();
        List<Component> ticks = new ArrayList<>();
        List<NativeListCard.Tile> tiles = new ArrayList<>();
        ticks.add(Component.literal("0%"));
        for (int i = 0; i < tiers.length; i++) {
            var threshold = LargeWorkstationOverclock.forCoolingTier(tiers[i]);
            int minimum = (int) threshold.minimumTemperature();
            bands.add(new NativeListCard.Band(minimum / 100.0, BAND_COLORS[i]));
            ticks.add(Component.literal(minimum + "%"));
            tiles.add(
                new NativeListCard.Tile(
                    Component.literal("≥" + minimum + "%"),
                    Component.literal(HostText.expandedNumber(threshold.maxParallelism())),
                    card("tile_caption", displayTier(tiers[i])),
                    !overheated && profile.coolingTier() == tiers[i]));
        }
        bands.add(new NativeListCard.Band(1.0, BAND_COLORS[tiers.length]));
        ticks.add(Component.literal("100%"));

        var card = NativeListCard.builder()
            .header(
                NativeListCard.Glyph.THERMOMETER,
                tr("temperature_setting"),
                band(s),
                overheated ? NativeListCard.DANGER : ProcessingTooltip.MUTED)
            .metrics(
                card("current_temperature"),
                Component.literal(precise(temperature) + "%"),
                overheated ? NativeListCard.DANGER : ProcessingTooltip.ACCENT,
                card("current_parallel"),
                Component.literal(HostText.expandedNumber(limit)),
                ProcessingTooltip.PRIMARY)
            .scale(bands, temperature / 100.0, ticks)
            .divider()
            .section(card("band_unlock"))
            .tiles(tiles)
            .divider()
            .row(
                null,
                ProcessingTooltip.MUTED,
                card("net_change"),
                card("rate", signedRate(s.getDouble("temperatureChangePerSecond"))),
                ProcessingTooltip.PRIMARY);
        rateRow(card, "processing", "+", s.getDouble("processingHeatPerSecond"), ProcessingTooltip.WARN, true);
        rateRow(card, "preheat", "+", s.getDouble("preheatPerSecond"), ProcessingTooltip.WARN, false);
        rateRow(card, "cooling", "-", s.getDouble("coolingPerSecond"), ProcessingTooltip.CYAN, true);
        rateRow(card, "natural", "-", s.getDouble("naturalCoolingPerSecond"), ProcessingTooltip.CYAN, false);
        int recoverySeconds = LargeWorkstationThermal.RECOVERY_TICKS / 20;
        if (overheated) {
            card.banner(
                NativeListCard.Glyph.WARNING,
                tr("status_overheated"),
                tr("recovery_progress", decimal(s.getInt("recoveryTicks") / 20.0), recoverySeconds),
                NativeListCard.DANGER,
                NativeListCard.DANGER_BACKGROUND);
        } else {
            card.banner(
                NativeListCard.Glyph.WARNING,
                card("overheat_warning"),
                card("overheat_recovery", recoverySeconds),
                NativeListCard.DANGER,
                NativeListCard.DANGER_BACKGROUND);
        }
        return card.text(card("temperature_footer"), Component.empty(), ProcessingTooltip.MUTED);
    }

    private static void rateRow(NativeListCard.Builder card, String key, String sign, double value, int color,
        boolean always) {
        if (!always && !(value > 0)) return;
        card.row(
            null,
            ProcessingTooltip.MUTED,
            card(key).withColor(ProcessingTooltip.MUTED),
            card("rate_short", sign + precise(value)),
            color);
    }

    /** Active cooling card; {@code toggle} adds the left-click hint shown on the toggle button. */
    private static NativeListCard.Builder coolingCard(CompoundTag s, boolean toggle) {
        boolean enabled = s.getBoolean("activeCooling");
        var card = NativeListCard.builder()
            .header(
                NativeListCard.Glyph.SNOW,
                tr("active_cooling"),
                tr(enabled ? "enabled" : "disabled"),
                enabled ? ProcessingTooltip.OK : ProcessingTooltip.MUTED);
        String problem = s.getString("coolingProblem");
        if (enabled && !problem.isEmpty() && !problem.endsWith(".none")) {
            card.banner(
                NativeListCard.Glyph.PAUSE,
                Component.translatable(problem),
                Component.empty(),
                ProcessingTooltip.WARN,
                NativeListCard.WARN_BACKGROUND);
        }
        boolean supplied = s.getInt("coolantTier") > 0;
        card.divider()
            .row(
                null,
                ProcessingTooltip.MUTED,
                tr("coolant"),
                supplied ? coolant(s) : card("coolant_unsupplied"),
                supplied ? ProcessingTooltip.CYAN : ProcessingTooltip.WARN);
        if (thermal(s)) {
            card.row(
                null,
                ProcessingTooltip.MUTED,
                card("actual_cooling"),
                card("rate", precise(s.getDouble("coolingPerSecond"))),
                ProcessingTooltip.PRIMARY)
                .row(
                    null,
                    ProcessingTooltip.MUTED,
                    card("cooling_capacity"),
                    card("rate", precise(LargeWorkstationThermal.coolingPerSecond(s.getInt("coolantTier")))),
                    ProcessingTooltip.PRIMARY);
        }
        card.divider();
        card.text(
            tr(thermal(s) ? "cooling_thermal_hint" : "cooling_coolant_hint"),
            Component.empty(),
            ProcessingTooltip.MUTED)
            .text(tr("cooling_preheat_hint"), Component.empty(), ProcessingTooltip.MUTED);
        Component hint = Component.empty();
        if (toggle) hint.append(card("toggle_hint"));
        if (!hint.getString()
            .isEmpty()) card.text(hint, Component.empty(), ProcessingTooltip.MUTED);
        return card;
    }

    private static List<Component> statusTooltip(CompoundTag s) {
        List<Component> lines = new ArrayList<>();
        lines.add(tr("machine_status").withStyle(EnumChatFormatting.YELLOW));
        lines.add(status(s).withStyle(hasProblem(s) ? EnumChatFormatting.GOLD : EnumChatFormatting.GREEN));
        if (hasProblem(s)) lines.add(pauseText(s));
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

    private static boolean hasProblem(CompoundTag s) {
        String pause = s.getString("pause");
        return !pause.isEmpty() && !pause.endsWith(".none") && !s.getBoolean("preheating");
    }

    private static Component pauseText(CompoundTag s) {
        String key = s.getString("pause");
        return key.isEmpty() ? tr("status_idle")
            : Component.translatable(key)
                .withStyle(key.endsWith(".none") ? EnumChatFormatting.GRAY : EnumChatFormatting.GOLD);
    }

    private static int displayTier(int tier) {
        return tier == 2 ? 4 : tier;
    }

    private static String precise(double value) {
        return decimal(value);
    }

    private static String signedRate(double value) {
        return String.format(Locale.ROOT, "%+.2f", value);
    }

    private static Component card(String key, Object... args) {
        return tr("card." + key, args);
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
