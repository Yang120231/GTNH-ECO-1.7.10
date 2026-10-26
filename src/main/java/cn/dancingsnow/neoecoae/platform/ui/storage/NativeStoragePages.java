package cn.dancingsnow.neoecoae.platform.ui.storage;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.google.gson.JsonObject;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.storage.StorageCellGrid;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostCharts;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostUI;
import cn.dancingsnow.neoecoae.gui.storage.StoragePanelState;
import cn.dancingsnow.neoecoae.gui.storage.StoragePanelTextures;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.ui.NativeItemHandler;
import cn.dancingsnow.neoecoae.platform.ui.NativePageLayout;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Native widgets for every controller JSON node. All statistics, pagination and item actions use source contracts. */
public final class NativeStoragePages {

    private static final String ATLAS = "neoecoae:textures/guis/storage/storage_widget.png";
    private final StorageHostUI.Config config;
    private final NativeStorageSession session;
    private final StorageHostCharts charts;
    private final StorageCellGrid cells;
    private final NativeStoragePrioritySession priority;
    private final com.cleanroommc.modularui.api.IPanelHandler priorityPanel;
    private int typeOffset;

    private NativeStoragePages(ECOStorageSystemBlockEntity host, EntityPlayer player, PanelSyncManager sync) {
        config = host.storageUiConfig(player);
        session = new NativeStorageSession(host, config);
        charts = new StorageHostCharts(session::state);
        cells = new StorageCellGrid(session::state, 11);
        sync.syncValue("storage.snapshot", session);
        priority = new NativeStoragePrioritySession(host);
        sync.syncValue("storage.priority", priority);
        priorityPanel = sync.syncedPanel(
            "storage.priority.window",
            true,
            (panelSync, handler) -> NativeStoragePriorityPages.create(priority, handler));
    }

    public static ModularPanel controller(ECOStorageSystemBlockEntity host, EntityPlayer player,
        PanelSyncManager sync) {
        return new NativeStoragePages(host, player, sync).build();
    }

    private ModularPanel build() {
        NativePageLayout layout = new NativePageLayout("storage/storage_controller");
        ModularPanel panel = ModularPanel.defaultPanel("neoecoae_storage", layout.width(), layout.height());
        for (var value : layout.root()
            .getAsJsonArray("children")) {
            JsonObject node = value.getAsJsonObject();
            Widget<?> widget;
            if ("texture".equals(
                node.get("type")
                    .getAsString())) {
                var sprite = node.getAsJsonArray("sprite");
                widget = new Widget<>().background(
                    NativePageLayout.texture(
                        node.get("texture")
                            .getAsString(),
                        sprite.get(0)
                            .getAsInt(),
                        sprite.get(1)
                            .getAsInt(),
                        sprite.get(2)
                            .getAsInt(),
                        sprite.get(3)
                            .getAsInt()));
            } else {
                String custom = node.get("custom")
                    .getAsString();
                switch (custom) {
                    case "player_inventory":
                        widget = SlotGroupWidget.playerInventory(false);
                        break;
                    case "infinite_component_slot":
                        widget = componentSlot();
                        break;
                    case "storage_cell_grid":
                        widget = cellGrid();
                        break;
                    case "storage_caption":
                    case "storage_tier":
                    case "storage_host_icon":
                    case "storage_capacity_bar":
                    case "storage_resource_bar":
                    case "storage_energy_bar":
                    case "storage_type_panel":
                    case "storage_type_scrollbar":
                    case "storage_infinite_status":
                    case "storage_infinite_animation":
                    case "storage_information":
                        widget = new ArtNode(custom, node);
                        break;
                    default:
                        throw new IllegalArgumentException("Unbound source storage node: " + custom);
                }
            }
            layout.place(widget, node);
            panel.child(widget);
        }
        panel.child(
            new ButtonWidget<>().pos(-20, 44)
                .size(18, 20)
                .background(NativePageLayout.texture("neoecoae:textures/gui/vendor/ae2/states.png", 176, 128, 18, 20))
                .overlay(NativePageLayout.texture("neoecoae:textures/gui/vendor/ae2/states.png", 144, 64, 16, 16))
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    priorityPanel.openPanel();
                    return true;
                })
                .tooltipDynamic(
                    tooltip -> tooltip.add(
                        Component.translatable("gui.neoecoae.storage_priority.open")
                            .getString())));
        return panel;
    }

    private ItemSlot componentSlot() {
        NativeItemHandler handler = new NativeItemHandler(
            config.infiniteComponentInventory(),
            config.canExtractInfiniteComponents());
        ModularSlot slot = new ModularSlot(handler, 0) {

            @Override
            public boolean canTakeStack(EntityPlayer player) {
                boolean allowed = player.worldObj.isRemote ? session.canExtractComponents()
                    : config.canExtractInfiniteComponents()
                        .getAsBoolean();
                if (!allowed && !player.worldObj.isRemote) player.addChatMessage(
                    new net.minecraft.util.ChatComponentTranslation(
                        "tooltip.neoecoae.storage.infinite_component_locked"));
                return allowed && super.canTakeStack(player);
            }
        };
        return new ItemSlot().slot(slot)
            .background((IDrawable) null);
    }

    private ParentWidget<?> cellGrid() {
        ParentWidget<?> grid = new ParentWidget<>();
        for (int index = 0; index < NativeStorageSession.PAGE_SIZE; index++) grid.child(
            new CellButton(index).pos(index % 11 * 18, index / 11 * 18)
                .size(16));
        return grid;
    }

    private final class CellButton extends ButtonWidget<CellButton> {

        private final int index;

        CellButton(int index) {
            this.index = index;
            onMousePressed(button -> (button == 0 || button == 1) && session.click(index, Interactable.hasShiftDown()));
            onMouseScrolled((direction, amount) -> {
                session.scroll(direction.modifier > 0 ? -amount : amount);
                return true;
            });
            tooltipDynamic(tooltip -> {
                var cell = cell();
                if (!ItemStacks.isEmpty(cell.stack())) tooltip.addFromItem(cell.stack());
                for (Component line : cells.tooltip(index)) tooltip.add(
                    line.nativeText()
                        .getFormattedText())
                    .newLine();
            });
            onUpdateListener(widget -> widget.markTooltipDirty());
        }

        private StorageHostUI.CellEntry cell() {
            return index < session.state()
                .cells()
                .size() ? session.state()
                    .cells()
                    .get(index) : StorageHostUI.CellEntry.EMPTY;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            if (session.state()
                .formed() && cell().position() != null)
                atlas(0, 52 + index / 11 * 18, 16, index / 11 == 2 ? 16 : 18, 0, 0, 16, index / 11 == 2 ? 16 : 18);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            if (!ItemStacks.isEmpty(cell().stack()))
                GuiDraw.drawItem(cell().stack(), 0, 0, 16, 16, context.getCurrentDrawingZ());
            if (isHovering() && cell().position() != null)
                net.minecraft.client.gui.Gui.drawRect(0, 0, 16, 16, 0x80ffffff);
        }
    }

    private final class ArtNode extends ButtonWidget<ArtNode> {

        private final String kind;
        private final JsonObject definition;

        ArtNode(String kind, JsonObject definition) {
            this.kind = kind;
            this.definition = definition;
            playClickSound(false);
            tooltipDynamic(
                tooltip -> {
                    for (Component line : tooltipLines()) tooltip.add(
                        line.nativeText()
                            .getFormattedText())
                        .newLine();
                });
            onUpdateListener(widget -> widget.markTooltipDirty());
            onMouseScrolled((direction, amount) -> {
                if (!kind.equals("storage_type_panel") && !kind.equals("storage_type_scrollbar")) return false;
                typeOffset = Math
                    .max(0, Math.min(maxTypeOffset(), typeOffset + (direction.modifier > 0 ? -amount : amount)));
                return true;
            });
            if (kind.equals("storage_type_scrollbar")) onMousePressed(button -> {
                if (button != 0 && button != 1) return false;
                typeOffset = Math.max(0, Math.min(maxTypeOffset(), typeOffset + (button == 0 ? 1 : -1)));
                return true;
            });
        }

        private List<Component> tooltipLines() {
            switch (kind) {
                case "storage_capacity_bar":
                    return charts.capacityTooltip();
                case "storage_resource_bar":
                case "storage_type_panel":
                    return charts.resourceTooltip();
                case "storage_energy_bar":
                    return charts.energyTooltip();
                case "storage_caption":
                    switch (definition.get("metric")
                        .getAsString()) {
                        case "energy":
                        case "energy_stored":
                            return charts.energyTooltip();
                        case "bytes":
                        case "usage":
                            return charts.capacityTooltip();
                        case "resources":
                            return charts.resourceTooltip();
                        default:
                            return charts.domainTooltip();
                    }
                default:
                    return charts.domainTooltip();
            }
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> theme) {}

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            StoragePanelState state = session.state();
            int w = getArea().width, h = getArea().height;
            switch (kind) {
                case "storage_caption":
                    drawCaption(state, w, h);
                    break;
                case "storage_tier":
                    if (state.tier() > 0) atlas((Math.min(3, state.tier()) - 1) * 13, 0, 13, 18, 0, 0, w, h);
                    break;
                case "storage_host_icon":
                    atlas(0, 18, 16, 34, 0, 0, w, h);
                    if (state.formed()) {
                        int sx = 18 + (Math.max(1, Math.min(3, state.tier())) - 1) * 16;
                        atlas(sx, 19, 13, 8, (w - 13) / 2, 1, 13, 8);
                        atlas(sx, 43, 13, 8, (w - 13) / 2, h - 9, 13, 8);
                    }
                    GuiDraw.drawItem(
                        config.hostItem()
                            .get(),
                        (w - 16) / 2,
                        (h - 16) / 2,
                        16,
                        16,
                        context.getCurrentDrawingZ());
                    break;
                case "storage_capacity_bar":
                    drawCapacity(state, w, h);
                    break;
                case "storage_resource_bar":
                    drawSegments(state, w, h);
                    break;
                case "storage_energy_bar":
                    int energyWidth = Math.round(w * HostText.usageRatio(state.energy(), state.maxEnergy()));
                    if (energyWidth > 0) atlas(0, 133, energyWidth, 7, 0, 0, energyWidth, h);
                    break;
                case "storage_type_panel":
                    drawTypes(state);
                    break;
                case "storage_type_scrollbar":
                    typeOffset = Math.min(typeOffset, maxTypeOffset());
                    if (maxTypeOffset() > 0)
                        atlas(68, 113, 6, 15, 0, Math.round((h - 15) * typeOffset / (float) maxTypeOffset()), 6, 15);
                    break;
                case "storage_infinite_status":
                    if (state.unbounded()) atlas(16, 52, 24, 10, 0, 0, w, h);
                    break;
                case "storage_infinite_animation":
                    if (state.infinite()) UITexture.fullImage("neoecoae:textures/gui/storage/infinity.png")
                        .draw(0, 0, w, h);
                    break;
                case "storage_information":
                    UITexture.fullImage("neoecoae:textures/gui/common/side_bar/mirrored_button_slot_up.png")
                        .draw(0, 0, w, h);
                    UITexture
                        .fullImage(
                            "neoecoae:textures/gui/widget/information" + (isHovering() ? "_highlight" : "") + ".png")
                        .draw(0, 3, 21, 21);
                    break;
                default:
                    throw new IllegalStateException("Unbound source art: " + kind);
            }
        }

        @SideOnly(Side.CLIENT)
        private void drawCaption(StoragePanelState state, int width, int height) {
            String metric = definition.get("metric")
                .getAsString();
            Component value;
            switch (metric) {
                case "title":
                    value = Component.translatable("gui.neoecoae.storage.ui.title");
                    break;
                case "bytes":
                    value = Component.translatable(
                        "gui.neoecoae.storage.legacy.graph.total_bytes",
                        StorageHostCharts.capacityValue(state));
                    break;
                case "usage":
                    value = state.unbounded() ? Component.empty() : Component.literal(HostText.percent(state.usage()));
                    break;
                case "energy_stored":
                    value = Component.translatable(
                        "gui.neoecoae.storage.ui.energy",
                        HostText.ae2Amount(state.energy()),
                        HostText.ae2Amount(state.maxEnergy()));
                    break;
                case "energy":
                    value = Component.translatable(
                        "gui.neoecoae.storage.legacy.graph.energy_usage",
                        HostText.ae2Amount(state.drain()));
                    break;
                case "resources":
                    value = Component.translatable("gui.neoecoae.storage.ui.resource_distribution");
                    break;
                default:
                    throw new IllegalArgumentException("Unknown source caption metric: " + metric);
            }
            float scale = definition.has("font_scale") ? definition.get("font_scale")
                .getAsFloat() : .75f;
            if (metric.equals("energy") || metric.equals("energy_stored")) {
                var font = net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
                String stored = Component
                    .translatable(
                        "gui.neoecoae.storage.ui.energy",
                        HostText.ae2Amount(state.energy()),
                        HostText.ae2Amount(state.maxEnergy()))
                    .getString();
                String drain = Component
                    .translatable("gui.neoecoae.storage.legacy.graph.energy_usage", HostText.ae2Amount(state.drain()))
                    .getString();
                scale = Math.max(
                    .5f,
                    Math.min(
                        scale,
                        width / (float) Math.max(1, font.getStringWidth(stored) + 8 + font.getStringWidth(drain))));
            }
            text(
                value,
                0,
                metric.equals("title") ? Math.max(0, (height - 9) / 2) : 0,
                width,
                scale,
                0x3f3d52,
                definition.has("text_align") && definition.get("text_align")
                    .getAsString()
                    .equals("right"));
        }
    }

    private int maxTypeOffset() {
        return Math.max(
            0,
            session.state()
                .types()
                .size() - 3);
    }

    @SideOnly(Side.CLIENT)
    private static void drawCapacity(StoragePanelState state, int w, int h) {
        if (state.unbounded()) {
            int middle = (w - 16) / 2;
            for (int x = 0; x < w; x += 6) atlas(49, 78, Math.min(6, w - x), 8, x, 0, Math.min(6, w - x), h);
            atlas(32, 78, 16, 8, middle, 0, 16, h);
        } else drawSegments(state, Math.round(w * (float) state.usage()), h);
    }

    @SideOnly(Side.CLIENT)
    private static void drawSegments(StoragePanelState state, int width, int height) {
        int x = 0;
        for (var segment : StorageHostCharts.capacitySegments(state, width)) {
            int[] sprite = swatch(segment.swatch());
            org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
            for (int filled = 0; filled < segment.width(); filled += 6) {
                int part = Math.min(6, segment.width() - filled);
                if (segment.texture() != null) UITexture.fullImage(
                    segment.texture()
                        .toString())
                    .draw(x + filled, 0, part, height);
                else atlas(sprite[0] + 1, sprite[1], part, 8, x + filled, 0, part, height);
            }
            x += segment.width();
        }
    }

    @SideOnly(Side.CLIENT)
    private void drawTypes(StoragePanelState state) {
        typeOffset = Math.min(typeOffset, maxTypeOffset());
        for (int row = 0; row < 3 && typeOffset + row < state.types()
            .size(); row++) {
            var type = state.types()
                .get(typeOffset + row);
            int y = row * 29;
            atlas(0, 104, 68, 29, 0, y, 68, 29);
            int[] icon = swatch(type.swatch());
            if (type.icon() == null) atlas(icon[0], icon[1] + 24, 8, 8, 2, y + 2, 6, 6);
            else UITexture.fullImage(
                type.icon()
                    .toString())
                .draw(2, y + 2, 6, 6);
            text(
                Component.literal(
                    type.name()
                        .getString()),
                10,
                y + 2,
                21,
                .65f,
                type.color(),
                false);
            text(
                Component.literal(
                    HostText.ae2Amount(type.usedTypes()) + (state.unbounded() || type.totalTypes() < 0 ? ""
                        : "/" + HostText.ae2Amount(type.totalTypes()))),
                3,
                y + 13,
                29,
                .6f,
                StoragePanelTextures.VALUE_TEXT,
                false);
            text(
                Component.literal(
                    type.bytesText() + (state.unbounded() || type.totalBytes() < 0 ? ""
                        : "/" + HostText.ae2Amount(type.totalBytes()))),
                35,
                y + 13,
                30,
                .6f,
                StoragePanelTextures.VALUE_TEXT,
                false);
            int types = Math.round(
                29 * (state.unbounded() || type.totalTypes() < 0 ? 1
                    : HostText.usageRatio(type.usedTypes(), type.totalTypes())));
            int bytes = Math.round(
                29 * (state.unbounded() || type.totalBytes() < 0 ? 1
                    : HostText.usageRatio(type.usedBytes(), type.totalBytes())));
            if (types > 0) atlas(68, 105, types, 4, 3, y + 21, types, 4);
            if (bytes > 0) atlas(68, 109, bytes, 4, 36, y + 21, bytes, 4);
        }
    }

    private static int[] swatch(int index) {
        if (index < 0 || index >= 18) return new int[] { 16, 78 };
        switch (index) {
            case 14:
                return new int[] { 48, 70 };
            case 15:
                return new int[] { 96, 62 };
            case 16:
                return new int[] { 104, 62 };
            case 17:
                return new int[] { 56, 70 };
            default:
                return index < 6 ? new int[] { 16 + index * 8, 62 }
                    : index < 10 ? new int[] { 16 + (index - 6) * 8, 70 } : new int[] { 64 + (index - 10) * 8, 62 };
        }
    }

    @SideOnly(Side.CLIENT)
    private static void atlas(int sx, int sy, int sw, int sh, int x, int y, int w, int h) {
        NativePageLayout.texture(ATLAS, sx, sy, sw, sh)
            .draw(x, y, w, h);
    }

    @SideOnly(Side.CLIENT)
    private static void text(Component value, int x, int y, int width, float limit, int color, boolean right) {
        var font = net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
        String string = value.nativeText()
            .getFormattedText();
        if (string.isEmpty() || width <= 0) return;
        float scale = Math.max(.5f, Math.min(limit, width / (float) Math.max(1, font.getStringWidth(string))));
        string = font.trimStringToWidth(string, Math.max(1, (int) (width / scale)));
        if (right) x += Math.max(0, width - Math.round(font.getStringWidth(string) * scale));
        com.cleanroommc.modularui.utils.Platform.setupDrawFont();
        org.lwjgl.opengl.GL11.glPushMatrix();
        try {
            org.lwjgl.opengl.GL11.glTranslated(x, y, 0);
            org.lwjgl.opengl.GL11.glScalef(scale, scale, 1);
            font.drawString(string, 0, 0, color);
        } finally {
            org.lwjgl.opengl.GL11.glPopMatrix();
        }
    }
}
