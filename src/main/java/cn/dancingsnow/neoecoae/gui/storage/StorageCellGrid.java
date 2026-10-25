package cn.dancingsnow.neoecoae.gui.storage;

import java.util.ArrayList;
import java.util.List;

import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import cn.dancingsnow.neoecoae.gui.common.HostPanelTier;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;

public final class StorageCellGrid {

    private StorageHostUI.CellEntry cell(int index) {
        List<StorageHostUI.CellEntry> cells = snapshot.get()
            .cells();
        return index < cells.size() ? cells.get(index) : StorageHostUI.CellEntry.EMPTY;
    }

    public List<Component> tooltip(int index) {
        StoragePanelState state = snapshot.get();
        List<Component> lines = new ArrayList<>();
        if (index < state.cells()
            .size() && state.cells()
                .get(index)
                .installed()) {
            StorageHostUI.CellEntry cell = state.cells()
                .get(index);
            StoragePanelState.TypeLine type = state.type(cell.typeId());
            Component name = type != null ? type.name() : Component.translatable(switch (cell.kind()) {
                case StorageHostUI.CellEntry.KIND_ITEM -> "gui.neoecoae.storage.legacy.cell_info.item";
                case StorageHostUI.CellEntry.KIND_FLUID -> "gui.neoecoae.storage.legacy.cell_info.fluid";
                default -> "gui.neoecoae.storage.legacy.cell_info.other";
            });
            lines.add(Component.translatable("gui.neoecoae.storage.legacy.cell_info", name, tierName(cell)));
            lines.add(
                Component.translatable(
                    "gui.neoecoae.storage.legacy.cell_types",
                    HostText.usedPart(HostText.ae2Amount(cell.usedTypes()), cell.usedTypes(), cell.totalTypes()),
                    HostText.totalPart(capacity(cell.totalTypes()))));
            lines.add(
                Component.translatable(
                    "gui.neoecoae.storage.legacy.cell_bytes",
                    HostText.usedPart(HostText.fullBytes(cell.usedBytes()), cell.usedBytes(), cell.totalBytes()),
                    HostText.totalPart(fullCapacity(cell.totalBytes()))));
            lines.add(
                Component.translatable(
                    "gui.neoecoae.storage.ui.cell_state." + (!cell.online() || !state.formed() ? "offline"
                        : cell.infinite() ? "infinite"
                            : cell.state()
                                .name()
                                .toLowerCase(java.util.Locale.ROOT))));
            if (cell.extractionBlockReason() != null) lines.add(
                cell.extractionBlockReason()
                    .copy()
                    .withColor(0xFF5555));
            else lines.add(
                Component.translatable("gui.neoecoae.storage.ui.take_cell")
                    .withColor(HostText.MUTED));
        } else {
            lines.add(Component.translatable("gui.neoecoae.storage.ui.cell_state.absent"));
            if (cell(index).position() != null) {
                lines.add(
                    Component.translatable("gui.neoecoae.storage.ui.insert_cell")
                        .withColor(HostText.MUTED));
            }
        }
        lines.add(pageText(state, columns * 3));
        lines.add(
            Component.translatable("gui.neoecoae.storage.ui.scroll_cells")
                .withColor(HostText.MUTED));
        return lines;
    }

    public static Component pageText(StoragePanelState state, int pageSize) {
        return Component.translatable(
            "gui.neoecoae.storage.ui.cell_page",
            state.cellCount(),
            state.cellOffset() / pageSize + 1,
            Math.max(1, (state.cellCount() + pageSize - 1) / pageSize));
    }

    static String capacity(long value) {
        return value < 0 ? "\u221E" : HostText.ae2Amount(value);
    }

    private static String fullCapacity(long value) {
        return value < 0 ? "\u221E" : HostText.fullBytes(value);
    }

    static Component tierName(StorageHostUI.CellEntry cell) {
        if (cell.panelTierName() != null) return cell.panelTierName();
        return HostPanelTier.name(
            !cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(cell.stack()) && cell.stack()
                .getItem() instanceof IBasicECOCellItem item ? item.getTier() : null,
            cell.tier());
    }

    private final java.util.function.Supplier<StoragePanelState> snapshot;

    private final int columns;

    public StorageCellGrid(java.util.function.Supplier<StoragePanelState> snapshot, int columns) {
        this.snapshot = snapshot;
        this.columns = columns;
    }
}
