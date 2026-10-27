package cn.dancingsnow.neoecoae.platform.ui.crafting;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;

import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingControllerPanelState;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.ui.NativeUiDraw;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Direct rendering of CraftingControllerArtwork: every rectangle uses the source's native pixel coordinates. */
public final class NativeCraftingArtwork extends ParentWidget<NativeCraftingArtwork> {

    private static final String BACKGROUND = "neoecoae:textures/guis/crafting_controller.png";
    private static final String ATLAS = "neoecoae:textures/guis/crafting_widgets.png";
    private static final int PRIMARY = 0xe4e4ee, SECONDARY = 0xb4b6cc;
    private final NativeCraftingSession session;
    private final ItemStack hostItem;
    private long opened;

    public NativeCraftingArtwork(NativeCraftingSession session, ItemStack hostItem) {
        this.session = session;
        this.hostItem = hostItem;
        size(253, 214);
        background(cn.dancingsnow.neoecoae.platform.ui.NativePageLayout.texture(BACKGROUND, 0, 0, 253, 214));
        for (int index = 0; index < 11; index++) {
            final int cell = index;
            child(new cn.dancingsnow.neoecoae.platform.ui.NativeSlotButton<>().slotInset(0)
                .setEnabledIf(widget -> {
                    var state = CraftingControllerPanelState.fromTag(session.state().getCompound("artwork"));
                    return state.formed() && state.hasFx(cell);
                })
                .playClickSound(false)
                .onMousePressed(button -> false)
                .tooltipDynamic(lines -> {
                    var cells = session.state().getList("displayCells", 10);
                    if (cell >= cells.size()) return;
                    var entry = cells.getCompound(cell);
                    var stack = ItemStacks.parseOptional(RegistryAccess.EMPTY, entry.getCompound("item"));
                    if (ItemStacks.isEmpty(stack)) return;
                    lines.addFromItem(stack);
                    if (!entry.getString("amount").isEmpty()) lines.add(
                        text("amount_short").getString() + ": "
                            + cn.dancingsnow.neoecoae.crafting.display.format.BigNumberFormatter.formatTooltip(
                                new java.math.BigInteger(entry.getString("amount")), 1));
                })
                .onUpdateListener(widget -> widget.markTooltipDirty())
                .pos(49 + cell * 18, 38).size(16));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        CompoundTag values = session.state();
        CraftingControllerPanelState state = CraftingControllerPanelState.fromTag(values.getCompound("artwork"));
        label("title", 44, 10, 105, 1, false);
        NativeUiDraw.text(Component.literal(performance(values.getLong("averageNanos"))),
            210, 10, 39, 1, PRIMARY, true, true);
        heading("control_heading", 9, 68, 43);
        heading("cooling_short", 10, 84, 15);
        heading("overclock_short", 37, 84, 15);
        heading("queue_heading", 65, 68, 49);
        heading("parallel_heading", 118, 68, 49);
        heading("energy_heading", 169, 68, 22);
        heading("coolant_heading", 192, 68, 26);
        heading("output_heading", 223, 68, 26);
        NativeUiDraw.item(hostItem, 9, 38, context.getCurrentDrawingZ());
        if (!state.formed()) return;
        if (state.hostTier() > 0) sprite((state.hostTier() - 1) * 13, 0, 13, 18, 23, 5);
        int marker = 19 + (Math.max(1, state.hostTier()) - 1) * 16;
        sprite(marker, 19, 10, 7, 12, 31);
        sprite(marker, 44, 10, 7, 12, 54);
        var cells = values.getList("displayCells", 10);
        for (int cell = 0; cell < 11; cell++) {
            if (!state.hasFx(cell)) continue;
            int x = 49 + cell * 18;
            sprite(0, 18, 16, 34, x, 29);
            if (state.upperTier(cell) > 0) sprite(1 + (state.upperTier(cell) - 1) * 12, 53, 10, 6, x + 3, 32);
            if (state.lowerTier(cell) > 0) sprite(1 + (state.lowerTier(cell) - 1) * 12, 59, 10, 5, x + 3, 55);
            CompoundTag entry = cell < cells.size() ? cells.getCompound(cell) : new CompoundTag();
            NativeUiDraw.item(ItemStacks.parseOptional(RegistryAccess.EMPTY, entry.getCompound("item")),
                x, 38, context.getCurrentDrawingZ());
            if (!entry.getString("amount").isEmpty()) NativeUiDraw.text(
                Component.literal(cn.dancingsnow.neoecoae.crafting.display.format.BigNumberFormatter
                    .formatSI(new java.math.BigInteger(entry.getString("amount")), 1)), x, 48, 16, .5f, PRIMARY, false, true);
        }
        stat(65, 0, "queue_count_short", HostText.ae2Amount(values.getLong("queued")));
        stat(65, 1, "queue_capacity_short", values.getLong("batchCapacity") == Long.MAX_VALUE ? "∞"
            : HostText.ae2Amount(values.getLong("batchCapacity")));
        coloredStat(65, 2, "queue_used_short", percent(state.queuePermille()), 0xb4e2ff);
        coloredStat(118, 0, "parallel_count_short", values.getBoolean("virtual") ? "∞" : HostText.ae2Amount(values.getLong("parallel")), 0xffc2ed);
        stat(118, 1, "parallel_limit_short", values.getBoolean("virtual") ? "∞"
            : HostText.ae2Amount(Math.max(0, values.getLong("parallel") - values.getLong("overflow"))));
        if (values.getBoolean("virtual")) {
            stat(118, 2, "virtual_crafting_short");
            stat(118, 3, "runtime_ticks_short", values.getInt("executionTicks"));
        } else {
            stat(118, 2, "parallel_overflow_short", HostText.ae2Amount(values.getLong("overflow")));
            coloredStat(118, 3, "parallel_overflow_percent_short", percent(CraftingControllerPanelState.permille(
                values.getLong("overflow"), Math.max(1, values.getLong("parallel") - values.getLong("overflow")))),
                values.getLong("overflow") > 0 ? 0xffdb99 : PRIMARY);
        }
        bar(66, 114, state.queuePermille(), 8, 115);
        bar(121, 114, CraftingControllerPanelState.permille(1, values.getInt("executionTicks")), 8, 122);
        int energy = Math.round(46 * reveal(state.energyPermille() / 1000d));
        if (energy > 0) sprite(0, 161 - energy, 8, energy, 176, 124 - energy);
        fluid(values, "coolantFluid", values.getLong("coolant"), values.getLong("coolantCapacity"), 195, 209,
            context.getCurrentDrawingZ());
        fluid(values, "outputFluid", -1, values.getInt("outputCapacity"), 226, 240, context.getCurrentDrawingZ());
        boolean virtual = values.getBoolean("virtual");
        if (values.getBoolean("overclock")) sprite(0, virtual ? 77 : 65, 12, 12, 180, 136);
        if (values.getBoolean("cooling")) sprite(12, virtual ? 77 : 65, 12, 12, 180, 161);
        if (state.switchOverlay()) sprite(virtual ? 24 : values.getInt("highSwitches") > 0 ? 36 : 24,
            virtual ? 77 : 65, 12, 12, 180, 186);
        info(text("fx_short", values.getInt("activeWorkers"), values.getInt("physicalWorkers")),
            text("ft_short", HostText.ae2Amount(values.getLong("parallel"))), 130);
        var coolant = net.minecraftforge.fluids.FluidStack.loadFluidStackFromNBT(values.getCompound("coolantFluid").nativeTag());
        info(coolant == null ? Component.translatable("gui.neoecoae.native_fluid.empty") : Component.literal(coolant.getLocalizedName()),
            text("coolant_overclock", Math.max(0, values.getInt("coolantOverclock"))), 155);
        if (values.getInt("normalSwitches") == 0 && values.getInt("highSwitches") == 0)
            info(text("switch_none"), text("network_multiplier", values.getInt("multiplier")), 180);
        else info(text("switch_normal", values.getInt("normalSwitches")),
            text("switch_high_energy", values.getInt("highSwitches")), 180);
    }

    @SideOnly(Side.CLIENT)
    private void fluid(CompoundTag values, String key, long amount, long capacity, int x, int ticks, float z) {
        var fluid = net.minecraftforge.fluids.FluidStack.loadFluidStackFromNBT(values.getCompound(key).nativeTag());
        long actual = amount >= 0 ? amount : fluid == null ? 0 : fluid.amount;
        int height = Math.round(46 * reveal(capacity <= 0 ? 0 : Math.min(1, actual / (double) capacity)));
        if (fluid != null && height > 0) GuiDraw.drawFluidTexture(fluid, x + 1, 124 - height, 18, height, z);
        for (int y = 84; y <= 114; y += 10) NativeUiDraw.sprite(BACKGROUND, ticks, y, 5, 1, ticks, y, 5, 1);
        for (int y = 89; y <= 119; y += 10) NativeUiDraw.sprite(BACKGROUND, ticks + 2, y, 3, 1, ticks + 2, y, 3, 1);
    }

    private float reveal(double target) {
        long now = System.nanoTime();
        if (opened == 0) opened = now;
        double progress = Math.min(1, (now - opened) / 500_000_000d);
        return (float) (Math.min(1, Math.max(0, target)) * progress * progress * (3 - 2 * progress));
    }

    @SideOnly(Side.CLIENT)
    private void bar(int x, int y, int permille, int u, int v) {
        int width = Math.round(44 * reveal(permille / 1000d));
        if (width > 0) sprite(u, v, width, 7, x, y);
    }

    @SideOnly(Side.CLIENT)
    private static void sprite(int u, int v, int w, int h, int x, int y) {
        NativeUiDraw.sprite(ATLAS, u, v, w, h, x, y, w, h);
    }

    @SideOnly(Side.CLIENT)
    private static void label(String key, int x, int y, int width, float scale, boolean center) {
        NativeUiDraw.text(text(key), x, y, width, scale, PRIMARY, center, true);
    }

    @SideOnly(Side.CLIENT)
    private static void heading(String key, int x, int y, int width) { label(key, x, y, width, .75f, true); }

    @SideOnly(Side.CLIENT)
    private static void stat(int x, int row, String key, Object... arguments) {
        NativeUiDraw.text(text(key, arguments), x, 81 + row * 8, 49, .75f, PRIMARY, false, true);
    }

    @SideOnly(Side.CLIENT)
    private static void coloredStat(int x, int row, String key, String value, int color) {
        String template = text(key, "\u0001").getString();
        int split = template.indexOf('\u0001');
        Component line = split < 0 ? Component.literal(template)
            : Component.literal(template.substring(0, split)).append(Component.literal(value).withColor(color))
                .append(template.substring(split + 1));
        NativeUiDraw.text(line, x, 81 + row * 8, 49, .75f, PRIMARY, false, true);
    }

    @SideOnly(Side.CLIENT)
    private static void info(Component first, Component second, int y) {
        NativeUiDraw.text(first, 196, y + 4, 48, .75f, PRIMARY, false, true);
        NativeUiDraw.text(second, 196, y + 14, 48, .75f, SECONDARY, false, true);
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("gui.neoecoae.crafting.ui." + key, arguments);
    }

    private static String percent(int permille) { return Math.round(permille / 10f) + "%"; }

    private static String performance(long nanos) {
        long micros = Math.round(Math.max(0L, nanos) / 1000d);
        return micros < 1000 ? micros + " us" : new java.text.DecimalFormat("#,##0.###",
            java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT)).format(nanos / 1_000_000d) + " ms";
    }
}
