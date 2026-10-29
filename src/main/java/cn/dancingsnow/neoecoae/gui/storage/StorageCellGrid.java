package cn.dancingsnow.neoecoae.gui.storage;

import java.util.ArrayList;
import java.util.List;

import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import cn.dancingsnow.neoecoae.gui.common.HostPanelTier;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;

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
            lines.add(
                ItemStacks.isEmpty(cell.stack())
                    ? Component.translatable("gui.neoecoae.storage.legacy.cell_info", name, tierName(cell))
                    : Component.literal(
                        cell.stack()
                            .getDisplayName()));
            long byteCapacity = cell.infinite() || cell.totalBytes() == Long.MAX_VALUE ? -1 : cell.totalBytes();
            lines.add(
                Component.translatable(
                    "gui.neoecoae.storage.legacy.cell_bytes",
                    HostText.usedPart(HostText.fullBytes(cell.usedBytes()), cell.usedBytes(), byteCapacity),
                    HostText.totalPart(fullCapacity(byteCapacity))));
            lines.add(
                Component.translatable(
                    "gui.neoecoae.storage.legacy.cell_types",
                    HostText.usedPart(HostText.ae2Amount(cell.usedTypes()), cell.usedTypes(), cell.totalTypes()),
                    HostText.totalPart(capacity(cell.totalTypes()))));
            if (cell.extractionBlockReason() != null) lines.add(
                cell.extractionBlockReason()
                    .copy()
                    .withColor(0xFF5555));
            // Only a configured offset adds a line; the tooltip does not repeat interaction hints.
            int cellPriority = cn.dancingsnow.neoecoae.impl.storage.ECOStorageCellSettings.priority(cell.stack());
            if (cellPriority != 0
                && cn.dancingsnow.neoecoae.impl.storage.ECOStorageCellSettings.canConfigure(cell.stack()))
                lines.add(Component.translatable("gui.neoecoae.storage.cell_priority.value", cellPriority));
        } else {
            lines.add(Component.translatable("gui.neoecoae.storage.ui.cell_state.absent"));
        }
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
