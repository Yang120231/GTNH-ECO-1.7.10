"""Select source storage display algorithms; native widgets live in platform/ui/storage."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
path = ROOT / "porting/partial-imports.json"
parts = json.loads(path.read_text(encoding="utf8"))
parts["gui/storage/StorageHostCharts.java"] = {
    "scope": "Source exact capacity distribution, value formatting and tooltips. Widget drawing is implemented by native MUI2 adapters.",
    "header": "public final class StorageHostCharts",
    "members": ["TEXT", "CapacitySegment", "ResourceColor", "capacitySegments", "capacityValue", "fullCapacityValue", "fullUsedBytes", "capacityTooltip", "energyTooltip", "resourceTooltip", "domainTooltip"],
    "extra_members": ["private final java.util.function.Supplier<StoragePanelState> snapshot;", "public StorageHostCharts(java.util.function.Supplier<StoragePanelState> snapshot) { this.snapshot = snapshot; }", "private StoragePanelState state() { return snapshot.get(); }"]
}
parts["gui/storage/StorageCellGrid.java"] = {
    "scope": "Source physical drive indexing and cell/page tooltips. Native display slots call the unchanged source extraction/insertion callbacks.",
    "header": "public final class StorageCellGrid",
    "members": ["cell", "tooltip", "pageText", "capacity", "fullCapacity", "tierName"],
    "extra_members": ["private final java.util.function.Supplier<StoragePanelState> snapshot;", "private final int columns;", "public StorageCellGrid(java.util.function.Supplier<StoragePanelState> snapshot, int columns) { this.snapshot = snapshot; this.columns = columns; }"]
}
parts["gui/common/HostPanelTier.java"] = {
    "scope": "Source tier resolution and display name; native GUI drawing is a platform concern.",
    "header": "public final class HostPanelTier", "members": ["resolve", "name"], "extra_members": ["private HostPanelTier() {}"]
}
path.write_text(json.dumps(parts, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
