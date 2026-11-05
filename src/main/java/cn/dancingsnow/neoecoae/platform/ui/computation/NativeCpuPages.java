package cn.dancingsnow.neoecoae.platform.ui.computation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;

import cn.dancingsnow.neoecoae.crafting.display.format.BigNumberFormatter;
import cn.dancingsnow.neoecoae.crafting.display.format.NEByteFormatter;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.ui.NativeAe2Button;
import cn.dancingsnow.neoecoae.platform.ui.crafting.NativeStatusCardTooltip;
import cn.dancingsnow.neoecoae.platform.ui.crafting.ProcessingTooltip;
import cn.dancingsnow.neoecoae.platform.ui.NativePageLayout;
import cn.dancingsnow.neoecoae.platform.ui.NativeText;
import cn.dancingsnow.neoecoae.platform.ui.NativeUiDraw;
import cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Source CPU selector and processing status artwork, using native hitboxes and server-owned exact pages. */
public final class NativeCpuPages {

    private static final String SELECTOR = "neoecoae:textures/guis/cpu_selector.png";
    private static final String CPU_CARDS = "neoecoae:textures/guis/eco_craftingcpu.png";
    private static final String STATUS = "neoecoae:textures/guis/eco_crafting_processing.png";
    static final int STATUS_WIDTH = 427, STATUS_HEIGHT = 272;

    private NativeCpuPages() {}

    public static ParentWidget<?> selector(NativeComputationSession session, IntConsumer openDetails) {
        ParentWidget<?> page = new ParentWidget<>().size(90, 164)
            .background(NativePageLayout.texture(SELECTOR, 0, 0, 90, 164));
        page.child(
            new NativeText(
                () -> Component.literal(
                    "CPUs (" + session.state()
                        .getInt("cpuCount") + ")"),
                .75f,
                0x3f3d52).pos(8, 6)
                    .size(70, 9));
        for (int row = 0; row < 6; row++) page.child(
            new CpuRow(session, row, false, openDetails).name("cpu-row-" + row)
                .pos(8, 19 + row * 23)
                .size(67, 22));
        page.child(
            new CpuScrollbar(session, false, false).pos(78, 19)
                .size(7, 137));
        return page;
    }

    public static ParentWidget<?> status(NativeComputationSession session, Runnable back) {
        ParentWidget<?> page = new ParentWidget<>().size(STATUS_WIDTH, STATUS_HEIGHT)
            .background((context, x, y, width, height, theme) -> {
                NativePageLayout.texture(STATUS, 0, 0, 427, 50).draw(context, x, y, 427, 50, theme);
                for (int row = 0; row < 7; row++)
                    NativePageLayout.texture(STATUS, 0, 50, 427, 23).draw(context, x, y + 50 + row * 23, 427, 23, theme);
                NativePageLayout.texture(STATUS, 0, 165, 427, 35).draw(context, x, y + 211, 427, 35, theme);
                NativePageLayout.texture(STATUS, 95, 200, 237, 26).draw(context, x + 95, y + 246, 237, 26, theme);
            });
        page.child(
            new NativeText(() -> Component.translatable("gui.neoecoae.native_computation.cpu_list_title"), 1, 0x413f54)
                .pos(8, 7)
                .size(75, 9));
        page.child(
            new NativeText(() -> Component.translatable("gui.appliedenergistics2.CraftingStatus"), 1, 0x413f54)
                .pos(152, 7)
                .size(155, 9));
        for (int row = 0; row < 9; row++) page.child(
            new CpuRow(session, row, true, ignored -> {}).pos(11, 27 + row * 23)
                .size(67, 22));
        for (int row = 0; row < 9; row++) for (int column = 0; column < 3; column++) page.child(
            new ResourceCard(session, row * 3 + column).pos(105 + column * 68, 27 + row * 23)
                .size(67, 22));
        page.child(
            new CpuScrollbar(session, false, true).pos(87, 27)
                .size(12, 206));
        page.child(
            new CpuScrollbar(session, true, true).pos(315, 27)
                .size(12, 206));
        for (int row = 0; row < 9; row++) page.child(
            new ResourceCard(session, row, true).pos(333, 27 + row * 23).size(67, 22));
        page.child(new CpuScrollbar(session, true, true, true).pos(409, 27).size(12, 206));
        page.child(
            new ButtonWidget<>()
                .background(NativePageLayout.texture("neoecoae:textures/gui/vendor/ae2/states.png", 160, 192, 20, 20))
                .disableHoverBackground()
                .overlay(NativePageLayout.texture("neoecoae:textures/gui/vendor/ae2/states.png", 96, 16, 16, 16))
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    back.run();
                    return true;
                })
                .pos(403, -5)
                .size(20));
        page.child(
            actionButton(session, true).pos(191, 247)
                .size(50, 20));
        page.child(
            actionButton(session, false).pos(251, 247)
                .size(50, 20));
        return page;
    }

    private static Widget<?> actionButton(NativeComputationSession session, boolean pause) {
        return new NativeAe2Button(
            () -> Component.translatable(
                pause ? selected(session).getBoolean("suspended") ? "gui.neoecoae.native_computation.resume"
                    : "gui.neoecoae.native_computation.pause" : "gui.appliedenergistics2.Cancel"))
                        .activeWhen(() -> selected(session).getBoolean("busy"))
                        .onMousePressed(button -> {
                            if (button != 0 || !selected(session).getBoolean("busy")) return false;
                            session.action(pause ? 6 : 7, selected(session).getInt("token"));
                            return true;
                        });
    }

    private static final class CpuScrollbar extends ButtonWidget<CpuScrollbar> {

        private final NativeComputationSession session;
        private final boolean items, status, tasks;
        private boolean dragging;

        CpuScrollbar(NativeComputationSession session, boolean items, boolean status) {
            this(session, items, status, false);
        }

        CpuScrollbar(NativeComputationSession session, boolean items, boolean status, boolean tasks) {
            this.tasks = tasks;
            this.session = session;
            this.items = items;
            this.status = status;
            NativeUiStyle.plainHover(this);
            onMouseScrolled((direction, amount) -> {
                change(value() + (direction.modifier > 0 ? -1 : 1) * (items ? 3 : 2));
                return true;
            });
        }

        private int value() {
            return tasks ? selected(session).getInt("taskOffset") : items ? selected(session).getInt("itemOffset") / 3
                : session.state()
                    .getInt("offset");
        }

        private int maximum() {
            return Math.max(
                0,
                tasks ? selected(session).getInt("taskCount") - 9 : items ? (selected(session).getInt("itemCount") + 2) / 3 - 9
                    : session.state()
                        .getInt("cpuCount") - (status ? 9 : 6));
        }

        private void change(int row) {
            session.action(tasks ? 20 : items ? 14 : 4, Math.max(0, Math.min(maximum(), row)) * (items && !tasks ? 3 : 1));
        }

        private void drag() {
            change(Math.round((getContext().getMouseY() - 7.5f) * maximum() / (getArea().height - 15)));
        }

        @Override
        public Result onMousePressed(int button) {
            if (button != 0) return Result.IGNORE;
            dragging = true;
            drag();
            return Result.SUCCESS;
        }

        @Override
        public void onMouseDrag(int button, long elapsed) {
            if (dragging && button == 0) drag();
        }

        @Override
        public boolean onMouseRelease(int button) {
            dragging = false;
            return button == 0;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            int max = maximum(), top = max == 0 ? 0 : (getArea().height - 15) * Math.min(max, value()) / max;
            int width = status ? 12 : 7;
            NativeUiDraw.sprite(
                "neoecoae:textures/gui/vendor/ae2/sprites/" + (status ? "big" : "small")
                    + "_scroller"
                    + (max == 0 ? "_disabled" : "")
                    + ".png",
                0,
                0,
                width,
                15,
                0,
                top,
                width,
                15);
        }
    }

    private static final class CpuRow extends ButtonWidget<CpuRow> {

        private final NativeComputationSession session;
        private final int index;
        private final boolean status;

        CpuRow(NativeComputationSession session, int index, boolean status, IntConsumer openDetails) {
            this.session = session;
            this.index = index;
            this.status = status;
            NativeUiStyle.plainHover(this);
            onMousePressed(button -> {
                int token = row().getInt("token");
                if (Boolean.getBoolean("neoecoae.uiParityAcceptance")) cn.dancingsnow.neoecoae.NeoECOAE.LOG.info(
                    "NEOECOAE_UI_CPU_CLICK row={} token={} selected={} details={}",
                    index,
                    token,
                    session.state()
                        .getInt("selectedToken"),
                    status);
                if (button != 0 || token == 0) return false;
                if (session.state()
                    .getInt("selectedToken") == token && !status) openDetails.accept(token);
                else session.action(5, token);
                return true;
            });
            onMouseScrolled((direction, amount) -> {
                session.action(
                    4,
                    session.state()
                        .getInt("offset") + (direction.modifier > 0 ? -2 : 2));
                return true;
            });
            tooltipDynamic(lines -> {
                var cpu = row();
                if (cpu.getInt("token") == 0) return;
                java.util.List<String> out = new java.util.ArrayList<>();
                out.add(NativeCpuPages.name(cpu));
                out.add(field("gui.neoecoae.cpu.tooltip.storage", NEByteFormatter.format(cpu.getLong("storage"))));
                out.add(
                    field("gui.neoecoae.cpu.tooltip.parallel", grouped(BigInteger.valueOf(cpu.getInt("parallel")))));
                if (cpu.getBoolean("busy")) {
                    if (!cpu.getString("output")
                        .isEmpty()) out.add(field("gui.neoecoae.cpu.tooltip.crafting", cpu.getString("output")));
                    BigInteger requested = number(cpu.getString("requested")),
                        completed = number(cpu.getString("completed"));
                    if (requested.signum() > 0) {
                        int percent = completed.multiply(BigInteger.valueOf(100))
                            .divide(requested)
                            .min(BigInteger.valueOf(100))
                            .intValue();
                        out.add(
                            field(
                                "gui.neoecoae.cpu.tooltip.progress",
                                grouped(completed),
                                grouped(requested),
                                percent + "%"));
                    }
                } else out.add(
                    EnumChatFormatting.GRAY + Component.translatable("gui.neoecoae.cpu.status.idle")
                        .getString());
                String bigOrder = bigOrderStatus(cpu);
                if (!bigOrder.isEmpty()) out.add(EnumChatFormatting.GOLD + bigOrder);
                emit(lines, out);
            });
            if (status) tooltip(new NativeStatusCardTooltip(this::cardRows));
            onUpdateListener(widget -> widget.markTooltipDirty());
        }

        private List<ProcessingTooltip.Row> cardRows() {
            CompoundTag cpu = row();
            if (cpu.getInt("token") == 0) return Collections.emptyList();
            List<ProcessingTooltip.Row> rows = new ArrayList<>();
            String state = !cpu.getBoolean("busy") ? "idle" : cpu.getBoolean("suspended") ? "suspended" : "running";
            rows.add(ProcessingTooltip.Row.header(NativeCpuPages.name(cpu),
                Component.translatable("gui.neoecoae.cpu.status." + state).getString(),
                !cpu.getBoolean("busy") ? ProcessingTooltip.OK : cpu.getBoolean("suspended")
                    ? ProcessingTooltip.WARN : ProcessingTooltip.CYAN));
            rows.add(ProcessingTooltip.Row.separator());
            boolean exact = com.cleanroommc.modularui.api.widget.Interactable.hasShiftDown();
            rows.add(ProcessingTooltip.Row.text(card("storage_capacity"), exact
                ? grouped(BigInteger.valueOf(cpu.getLong("storage"))) + " B" : NEByteFormatter.format(cpu.getLong("storage"))));
            rows.add(ProcessingTooltip.Row.text(card("co_processors"), exact
                ? grouped(BigInteger.valueOf(cpu.getInt("parallel"))) : BigNumberFormatter.formatSI(BigInteger.valueOf(cpu.getInt("parallel")), 1), ProcessingTooltip.ACCENT));
            if (cpu.getBoolean("busy")) {
                BigInteger requested = number(cpu.getString("requested")), completed = number(cpu.getString("completed"));
                float progress = requested.signum() <= 0 ? 0 : Math.max(0, Math.min(1,
                    new java.math.BigDecimal(completed).divide(new java.math.BigDecimal(requested), 6,
                        java.math.RoundingMode.DOWN).floatValue()));
                rows.add(ProcessingTooltip.Row.separator());
                rows.add(ProcessingTooltip.Row.text(cpu.getString("output"), Math.round(progress * 100) + "%"));
                rows.add(ProcessingTooltip.Row.progress(progress, ProcessingTooltip.CYAN, 0xFF332E3B));
                rows.add(ProcessingTooltip.Row.text(field("gui.neoecoae.cpu.tooltip.progress",
                    exact ? grouped(completed) : BigNumberFormatter.formatSI(completed, 1),
                    exact ? grouped(requested) : BigNumberFormatter.formatSI(requested, 1), Math.round(progress * 100) + "%")));
            }
            String big = bigOrderStatus(cpu);
            if (!big.isEmpty()) rows.add(ProcessingTooltip.Row.text(big));
            if (!exact) rows.add(ProcessingTooltip.Row.hint(appeng.core.localization.GuiText.HoldShiftForTooltip.getLocal()));
            return rows;
        }

        private CompoundTag row() {
            return session.cpu(index);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            if (row().getInt("token") == 0) return;
            boolean chosen = row().getInt("token") == session.state()
                .getInt("selectedToken");
            NativeUiDraw.sprite(
                status ? CPU_CARDS : SELECTOR,
                status ? chosen ? 69 : 0 : 100,
                status ? 260 : chosen ? 22 : 0,
                67,
                22,
                0,
                0,
                67,
                22);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            var row = row();
            if (row.getInt("token") == 0) return;
            NativeUiDraw.text(Component.literal(NativeCpuPages.name(row)), 3, 2, 50, .666f, 0x413f54, false, false);
            String tier = row.getInt("tier") >= 3 ? "l9" : row.getInt("tier") == 2 ? "l6" : "l4";
            NativePageLayout.texture("neoecoae:textures/gui/computation/cpu_overlay/" + tier + ".png", 0, 0, 10, 10)
                .draw(55, 0, 10, 10);
            if (row.getBoolean("busy")) {
                icon(208, 244, 2, 10);
                AEKey key = AEKey.fromTagGeneric(RegistryAccess.EMPTY, row.getCompound("outputKey"));
                NativeUiDraw.text(
                    Component.literal(amount(row.getString("requested"), key)),
                    14,
                    13,
                    39,
                    .666f,
                    0x413f54,
                    false,
                    false);
                drawKey(key, 55, 10, .625f, context.getCurrentDrawingZ());
                BigInteger requested = number(row.getString("requested")),
                    completed = number(row.getString("completed"));
                int progress = requested.signum() <= 0 ? 0
                    : completed.multiply(BigInteger.valueOf(66))
                        .divide(requested)
                        .min(BigInteger.valueOf(66))
                        .intValue();
                if (progress > 0) GuiDraw.drawRect(1, 20, progress, 1, 0xfface9ff);
            } else {
                if (row.getInt("parallel") > 0) icon(208, 234, 2, 10);
                icon(208, 224, 27, 10);
                if (!"ANY".equals(row.getString("mode")))
                    icon(192, "PLAYER_ONLY".equals(row.getString("mode")) ? 224 : 234, 55, 10);
                // Both readouts share one scale so the parallel count and the byte size never differ in size.
                String parallel = HostText.ae2Amount(row.getInt("parallel"));
                String storage = NEByteFormatter.format(row.getLong("storage"));
                var font = net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
                float scale = Math.min(
                    .666f,
                    Math.min(
                        12f / Math.max(1, font.getStringWidth(parallel)),
                        26f / Math.max(1, font.getStringWidth(storage))));
                float y = 16 - 4.5f * scale; // keep the icon-row centre line of the old .666 layout
                NativeUiDraw.text(Component.literal(parallel), 14, y, 12, scale, 0x413f54, false, false);
                NativeUiDraw.text(Component.literal(storage), 39, y, 26, scale, 0x413f54, false, false);
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private static void icon(int u, int v, int x, int y) {
        NativeUiDraw.sprite("neoecoae:textures/gui/vendor/ae2/states.png", u, v, 10, 10, x, y, 10, 10);
    }

    private static final class ResourceCard extends ButtonWidget<ResourceCard> {

        private final NativeComputationSession session;
        private final int index;
        private final boolean task;

        ResourceCard(NativeComputationSession session, int index) {
            this(session, index, false);
        }

        ResourceCard(NativeComputationSession session, int index, boolean task) {
            this.task = task;
            this.session = session;
            this.index = index;
            background(IDrawable.EMPTY);
            hoverBackground(IDrawable.EMPTY);
            onMousePressed(button -> {
                var value = entry();
                if (button != 1 || !value.getBoolean("canControl") || value.getInt("controlToken") == 0) return false;
                session.action(value.getBoolean("stepStopped") ? 17 : 16, value.getInt("controlToken"));
                return true;
            });
            onMouseScrolled((direction, amount) -> {
                var value = entry();
                if (com.cleanroommc.modularui.api.widget.Interactable.hasShiftDown()
                    && value.getInt("controlToken") != 0) {
                    session.action(18, value.getInt("controlToken"));
                    return true;
                }
                session.action(task ? 20 : 14, selected(session).getInt(task ? "taskOffset" : "itemOffset")
                    + (direction.modifier > 0 ? -1 : 1) * (task ? 1 : 3));
                return true;
            });
            tooltip(new NativeStatusCardTooltip(this::cardRows));
            onUpdateListener(widget -> widget.markTooltipDirty());
        }

        private List<ProcessingTooltip.Row> cardRows() {
            CompoundTag value = entry();
            if (value.isEmpty()) return Collections.emptyList();
            List<ProcessingTooltip.Row> rows = new ArrayList<>();
            AEKey key = AEKey.fromTagGeneric(RegistryAccess.EMPTY, value.getCompound("key"));
            appeng.api.storage.data.IAEStack<?> stack = key == null ? null : key.toNative(1);
            String mod = stack == null ? "" : stack.getModId();
            var container = cpw.mods.fml.common.Loader.instance().getIndexedModList().get(mod);
            rows.add(ProcessingTooltip.Row.iconHeader(stack, value.getString("name"), container == null ? mod : container.getName()));
            rows.add(ProcessingTooltip.Row.separator());
            boolean active = number(value.getString("active")).signum() > 0;
            boolean pending = number(value.getString("pending")).signum() > 0;
            rows.add(ProcessingTooltip.Row.section(card("crafting_status"),
                card("state." + (active ? "crafting" : pending ? "scheduled" : "stored")),
                active ? ProcessingTooltip.OK : pending ? ProcessingTooltip.CYAN : ProcessingTooltip.MUTED));
            String[] fields = { "stored", "active", "pending" };
            String[] labels = { "stored", "crafting", "scheduled" };
            for (int i = 0; i < fields.length; i++) {
                String quantity = value.getString(fields[i]);
                if (number(quantity).signum() > 0) rows.add(ProcessingTooltip.Row.value(card(labels[i]),
                    com.cleanroommc.modularui.api.widget.Interactable.hasShiftDown() ? grouped(number(quantity))
                        : amount(quantity, key), i == 2 ? 0xFFC9A6FF : ProcessingTooltip.PRIMARY));
            }
            rows.add(ProcessingTooltip.Row.separator());
            var progress = cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.read(value.getCompound("processing"));
            rows.addAll(ProcessingTooltip.rows(active || (pending && progress.machineCount() > 0
                && progress.state() == cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State.WAITING),
                pending, true, progress));
            if (value.getBoolean("canControl")) rows.add(ProcessingTooltip.Row.hint(Component.translatable(
                value.getBoolean("stepStopped") ? "gui.neoecoae.processing.resume_hint" : "gui.neoecoae.processing.stop_hint").getString()));
            return rows;
        }

        private CompoundTag entry() {
            var list = selected(session).getList(task ? "tasks" : "items", 10);
            return index < list.size() ? list.getCompound(index) : new CompoundTag();
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            var entry = entry();
            if (entry.isEmpty()) return;
            if (task) {
                drawTask(entry, context);
                return;
            }
            AEKey key = AEKey.fromTagGeneric(RegistryAccess.EMPTY, entry.getCompound("key"));
            boolean active = number(entry.getString("active")).signum() > 0;
            boolean pending = number(entry.getString("pending")).signum() > 0;
            var processing = cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress
                .read(entry.getCompound("processing"));
            if (active && processing.hasProgress()) {
                // Whole-card fill sweeping left to right, with a faint tint on the unfilled remainder.
                int filled = Math.round(67 * Math.min(1f, Math.max(0f, processing.progress())));
                if (filled < 67) GuiDraw.drawRect(filled, 0, 67 - filled, 22, 0x30005500);
                if (filled > 0) GuiDraw.drawRect(0, 0, filled, 22, 0x90005500);
            } else if (active || pending) GuiDraw.drawRect(0, 0, 67, 22, active ? 0x5a005500 : 0x5a555500);
            if (entry.getBoolean("stepStopped")) GuiDraw.drawRect(1, 1, 3, 3, 0xffffaa00);
            drawKey(key, 48, 3, 1, context.getCurrentDrawingZ());
            String[] fields = { "stored", "active", "pending" };
            String[] labels = { "FromStorage", "Crafting", "Scheduled" };
            int count = 0;
            for (String field : fields) if (number(entry.getString(field)).signum() > 0) count++;
            float top = Math.round((22 - count * 4.5f - Math.max(0, count - 1)) / 2);
            int row = 0;
            for (int field = 0; field < fields.length; field++) {
                String value = entry.getString(fields[field]);
                if (number(value).signum() <= 0) continue;
                Component line = Component.translatable("gui.appliedenergistics2." + labels[field])
                    .append(": ")
                    .append(amount(value, key));
                int textWidth = net.minecraft.client.Minecraft.getMinecraft().fontRenderer
                    .getStringWidth(line.getString());
                NativeUiDraw.text(line, 46 - textWidth * .5f, top + row++ * 5.5f, 46, .5f, 0x413f54, false, false);
            }
        }
    }

    private static String card(String key) {
        return Component.translatable("gui.neoecoae.computation.ui.card." + key).getString();
    }

    @SideOnly(Side.CLIENT)
    private static void drawTask(CompoundTag entry, ModularGuiContext context) {
        AEKey key = AEKey.fromTagGeneric(RegistryAccess.EMPTY, entry.getCompound("key"));
        var progress = cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.read(entry.getCompound("processing"));
        int rgb = switch (progress.state()) {
            case WAITING -> 0xE2AA4F;
            case PAUSED -> 0x90909F;
            default -> 0x60CD64;
        };
        int filled = progress.hasProgress() ? Math.round(67 * progress.progress()) : 0;
        if (filled < 67) GuiDraw.drawRect(filled, 0, 67 - filled, 22, rgb | 0x30000000);
        if (filled > 0) GuiDraw.drawRect(0, 0, filled, 22, rgb | 0x90000000);
        drawKey(key, 48, 3, 1, context.getCurrentDrawingZ());
        boolean active = number(entry.getString("active")).signum() > 0;
        taskText(entry.getString("name"), 3);
        taskText(Component.translatable("gui.appliedenergistics2." + (active ? "Crafting" : "Scheduled")).getString()
            + ": " + amount(entry.getString(active ? "active" : "pending"), key), 9);
        taskText(progress.hasProgress() && progress.state() != cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State.WAITING
            ? Math.round(progress.progress() * 100) + "%" : Component.translatable("gui.neoecoae.crafting_status.processing.state."
                + progress.state().name().toLowerCase(java.util.Locale.ROOT)).getString(), 15);
    }

    @SideOnly(Side.CLIENT)
    private static void taskText(String text, int y) {
        int width = net.minecraft.client.Minecraft.getMinecraft().fontRenderer.getStringWidth(text);
        NativeUiDraw.text(Component.literal(text), 3, y, 44, Math.min(.5F, 44F / Math.max(1, width)), 0xF2F3F7, false, false);
    }

    @SideOnly(Side.CLIENT)
    private static void drawKey(AEKey key, int x, int y, float scale, int z) {
        org.lwjgl.opengl.GL11.glPushMatrix();
        try {
            org.lwjgl.opengl.GL11.glTranslatef(x, y, 0);
            org.lwjgl.opengl.GL11.glScalef(scale, scale, 1);
            if (key instanceof AEItemKey) NativeUiDraw.item(((AEItemKey) key).toStack(1), 0, 0, z);
            else if (key instanceof AEFluidKey) GuiDraw.drawFluidTexture(
                ((AEFluidKey) key).toNative(1000)
                    .getFluidStack(),
                0,
                0,
                16,
                16,
                z);
        } finally {
            org.lwjgl.opengl.GL11.glPopMatrix();
        }
    }

    private static CompoundTag selected(NativeComputationSession session) {
        return session.state()
            .getCompound("selected");
    }

    /**
     * Parent progress of a big order on this CPU (state and waiting reason), or empty for an ordinary job. The
     * requested and remaining amounts in the row are already the parent's exact totals.
     */
    static String bigOrderStatus(CompoundTag row) {
        String state = row.getString("state");
        if (state.isEmpty()) return "";
        String text = Component.translatable("gui.neoecoae.big_order." + state.toLowerCase(java.util.Locale.ROOT))
            .getString();
        String reason = row.getString("waiting");
        if (reason.isEmpty()) return text;
        String key = "gui.neoecoae.big_order.reason." + reason.toLowerCase(java.util.Locale.ROOT);
        String translated = Component.translatable(key)
            .getString();
        // Failures carry the executor's own message instead of a reason code.
        return text + ": " + (translated.equals(key) ? reason : translated);
    }

    private static String name(CompoundTag row) {
        return row.getString("name")
            .isEmpty() ? "CPU #" + row.getInt("token") : row.getString("name");
    }

    /** RichTooltip.add appends inline; every entry needs its own line break or the tooltip runs together. */
    private static void emit(com.cleanroommc.modularui.screen.RichTooltip lines, java.util.List<String> out) {
        for (int index = 0; index < out.size(); index++) {
            if (index > 0) lines.newLine();
            lines.add(out.get(index));
        }
    }

    private static String field(String key, Object... arguments) {
        String template = Component.translatable(key, arguments)
            .getString();
        int colon = Math.max(template.indexOf(": "), template.indexOf('：'));
        if (colon < 0) return template;
        int split = template.charAt(colon) == '：' ? colon + 1 : colon + 2;
        return EnumChatFormatting.GRAY + template.substring(0, split)
            + EnumChatFormatting.WHITE
            + template.substring(split);
    }

    private static String grouped(BigInteger value) {
        return String.format(java.util.Locale.ROOT, "%,d", value);
    }

    private static BigInteger number(String value) {
        return value.isEmpty() ? BigInteger.ZERO : new BigInteger(value);
    }

    private static String amount(String value, AEKey key) {
        return BigNumberFormatter.formatSI(number(value), key == null ? 1 : key.getAmountPerUnit());
    }
}
