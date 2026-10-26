package cn.dancingsnow.neoecoae.platform.ui.computation;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;

import cn.dancingsnow.neoecoae.crafting.display.format.NEByteFormatter;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.computation.ComputationControllerPanelState;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.ui.NativeUiDraw;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** ComputationControllerArtwork's native canvas, with the source's figures, glyphs and 7px statistic rows. */
public final class NativeComputationArtwork extends Widget<NativeComputationArtwork> {

    private static final String BACKGROUND = "neoecoae:textures/guis/eco_cpu_controller.png";
    private static final String ATLAS = "neoecoae:textures/guis/cpu_widget.png";
    private final NativeComputationSession session;
    private final ItemStack hostItem;
    private long opened;

    public NativeComputationArtwork(NativeComputationSession session, ItemStack hostItem) {
        this.session = session;
        this.hostItem = hostItem;
        size(253, 231);
        background(cn.dancingsnow.neoecoae.platform.ui.NativePageLayout.texture(BACKGROUND, 0, 0, 253, 231));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        CompoundTag values = session.state();
        var state = ComputationControllerPanelState.fromTag(values.getCompound("artwork"));
        NativeUiDraw.text(text("title"), 44, 10, 92, 1, 0x3f3d52, false, false);
        NativeUiDraw.text(text("control_heading"), 9, 90, 65, .75f, 0x3f3d52, false, false);
        String[] switches = {"substitutions", "cycle_planning", "fast_planner"};
        for (int index = 0; index < 3; index++) NativeUiDraw.text(text("switch." + switches[index]),
            6 + index * 23, 104, 23, .75f, 0x3f3d52, true, false);
        String[] panels = {"cpu", "threads", "parallel"};
        for (int index = 0; index < 3; index++) NativeUiDraw.text(text(panels[index]),
            80 + index * 57, 91, 50, .75f, 0x3f3d52, false, false);
        if (state.formed()) {
            if (state.hostTier() > 0) sprite((state.hostTier() - 1) * 13, 0, 13, 18, 23, 5);
            int u = 17 + (Math.max(1, state.hostTier()) - 1) * 16;
            sprite(u, 19, 14, 8, 10, 40);
            sprite(u, 43, 14, 8, 10, 64);
        }
        NativeUiDraw.item(hostItem, 9, 48, context.getCurrentDrawingZ());
        if (!state.formed()) return;
        for (int cell = 0; cell < 11; cell++) {
            if (!state.hasCell(cell)) continue;
            int x = 49 + cell * 18;
            sprite(0, 52, 16, 52, x, 30);
            if (state.upperMember(cell).present()) sprite(17 + (Math.max(1, state.upperTier(cell)) - 1) * 12, 67, 10, 5, x + 3, 47);
            if (state.coreMember(cell).present()) sprite(17 + (Math.max(1, state.coreTier(cell)) - 1) * 12, 53, 10, 6, x + 3, 53);
            if (state.lowerMember(cell).present()) sprite(17 + (Math.max(1, state.lowerTier(cell)) - 1) * 12, 61, 10, 6, x + 3, 60);
        }
        long total = values.getLong("bytes"), free = values.getLong("freeBytes"), used = Math.max(0, total - free);
        int usedThreads = values.getInt("usedThreads"), threads = values.getInt("threads");
        int parallel = values.getInt("parallel"), multiplier = values.getInt("multiplier");
        row(0, 0, "used", 0x2d69e1, bytes(used));
        row(0, 1, "capacity", 0x3f3d52, bytes(total));
        row(0, 2, "free", free == 0 && total > 0 ? 0xd95b25 : 0x2f8a4b, bytes(free));
        row(1, 0, "busy", 0x915dcd, counts(usedThreads));
        row(1, 1, "total", 0x3f3d52, counts(threads));
        row(1, 2, "free", threads == usedThreads && threads > 0 ? 0xd95b25 : 0x2f8a4b,
            counts(Math.max(0, threads - usedThreads)));
        row(2, 0, "parallel", 0xb738a2, counts(parallel));
        row(2, 1, "multiplier", multiplier > 1 ? 0xd95b25 : 0x3f3d52, "x" + multiplier);
        row(2, 2, "threads", 0x3f3d52, counts(threads));
        bar(0, ratio(used, total));
        bar(1, ratio(usedThreads, threads));
        bar(2, ratio(parallel, values.getInt("fullParallel")));
        boolean connected = values.getBoolean("connected");
        boolean pink = multiplier >= 8 && total == Long.MAX_VALUE && parallel == Integer.MAX_VALUE;
        if (connected) {
            status(0, pink, 180, 153);
            if (values.getBoolean("substitutions")) status(1, pink, 180, 178);
            if (values.getBoolean("cycles")) status(2, pink, 204, 178);
            if (values.getBoolean("fast")) status(3, pink, 228, 178);
            if (multiplier > 1) status(values.getInt("highSwitches") > 0 && !pink ? 5 : 4, pink, 180, 203);
        }
        info(text(connected ? "stat.online" : "stat.offline"), connected ? 0x7be495 : 0xff7a6b,
            text("panel.frequency", values.getInt("frequency")), 0xb4e2ff, 147);
        if (values.getInt("normalSwitches") == 0 && values.getInt("highSwitches") == 0)
            info(text("panel.switch_none"), 0xe4e4ee, text("panel.network", multiplier), 0xb4b6cc, 197);
        else info(text("panel.switch_normal", values.getInt("normalSwitches")), pink ? 0xff8fdd : 0xb4e2ff,
            text("panel.switch_high_energy", values.getInt("highSwitches")), 0xff7a6b, 197);
    }

    @SideOnly(Side.CLIENT)
    private void bar(int column, double ratio) {
        long now = System.nanoTime();
        if (opened == 0) opened = now;
        double time = Math.min(1, (now - opened) / 500_000_000d);
        int fill = (int) Math.round(44 * ratio * time * time * (3 - 2 * time));
        if (fill > 0) sprite(0, 154 + column * 7, fill, 7, 83 + column * 57, 131);
    }

    @SideOnly(Side.CLIENT)
    private static void row(int panel, int row, String label, int color, String... candidates) {
        var font = net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
        Component title = text("stat." + label);
        int x = 83 + panel * 57, y = 102 + row * 7;
        float room = 44 - font.getStringWidth(title.getString()) * .75f - 2;
        String value = candidates[candidates.length - 1];
        for (String candidate : candidates) if (font.getStringWidth(candidate) * .75f <= room) { value = candidate; break; }
        NativeUiDraw.text(title, x, y, 44, .75f, 0x5e5c72, false, false);
        NativeUiDraw.text(Component.literal(value), x + 44 - font.getStringWidth(value) * .75f, y,
            Math.max(1, font.getStringWidth(value)), .75f, color, false, false);
    }

    @SideOnly(Side.CLIENT)
    private static void info(Component first, int firstColor, Component second, int secondColor, int y) {
        NativeUiDraw.text(first, 196, y + 4, 47, .75f, firstColor, false, true);
        NativeUiDraw.text(second, 196, y + 14, 47, .75f, secondColor, false, true);
    }

    @SideOnly(Side.CLIENT)
    private static void status(int index, boolean pink, int x, int y) { sprite(index * 12, pink ? 116 : 104, 12, 12, x, y); }

    @SideOnly(Side.CLIENT)
    private static void sprite(int u, int v, int w, int h, int x, int y) { NativeUiDraw.sprite(ATLAS, u, v, w, h, x, y, w, h); }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("gui.neoecoae.computation.ui." + key, arguments);
    }

    private static double ratio(long used, long total) { return total <= 0 ? 0 : Math.min(1, Math.max(0, used / (double) total)); }

    private static String[] counts(long count) { return new String[] {Long.toString(count), HostText.ae2Amount(count)}; }

    private static String[] bytes(long bytes) {
        String full = NEByteFormatter.format(bytes);
        int space = full.lastIndexOf(' ');
        String compact = new java.math.BigDecimal(full.substring(0, space).replace(",", ""))
            .setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + full.substring(space);
        return new String[] {full, compact, compact.replace(" ", "")};
    }
}
