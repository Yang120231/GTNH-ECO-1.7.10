"""Declare the current storage slice. Excluded source members remain in pending_business."""
import json
from pathlib import Path

path=Path(__file__).with_name("partial-imports.json")
partials=json.loads(path.read_text(encoding="utf8"))
interface=partials["blocks/entity/ECOMachineInterfaceBlockEntity.java"]
interface["extra_members"]=["public boolean supportsInterfaceUi() { return supportsStorageInterfaceUi(); }"]
partials["blocks/entity/storage/ECOStorageSystemBlockEntity.java"]={
    "scope":"Storage host lifecycle, complete normal/infinite migration and restore state, domain membership, interface transfer, persistence and priority. Construction, optional MEGA filtering and interactive page members remain pending.",
    "header":"public class ECOStorageSystemBlockEntity extends NEBlockEntity<NEStorageCluster, ECOStorageSystemBlockEntity> implements ISyncPersistRPCBlockEntity, InternalInventoryHost, IStorageProvider, IECOStoragePriorityHost",
    "exclude_members":["selectedBuildLength","mirrorBuild","megaController","selectedEcoMegaBulkCell","selectedEcoMegaPage","buildInProgress","buildController", "createUI","createActionUI",
        "hasEcoMegaUpgradeCard","hasEcoMegaBulkCell","getEcoMegaBulkCellCount","getSelectedEcoMegaBulkCell","getEcoMegaPageCount","isEcoMegaFilterSlotAvailable","getSelectedEcoMegaPage","changeSelectedEcoMegaBulkCell","changeSelectedEcoMegaPage","setEcoMegaFilterFromClient","setEcoMegaFilter","cycleEcoMegaCutoffFromClient","cycleEcoMegaCutoff",
        "selectedEcoMegaBulkCell","selectEcoMegaBulkCell","selectedEcoMegaPage","selectEcoMegaPage",
        "getBuildDefinition","getMinBuildLength","getMaxBuildLength","getBuildLevel","getBuildPosition","getBuildState","getSelectedBuildLength","setSelectedBuildLength","isMirrorBuild","setMirrorBuild","isBuildInProgress","setBuildInProgress","rebuildAfterBuild","buildStateChanged"],
    "extra_members":["""public cn.dancingsnow.neoecoae.gui.storage.StorageHostUI.Config storageUiConfig(net.minecraft.entity.player.EntityPlayer player) {
        return new cn.dancingsnow.neoecoae.gui.storage.StorageHostUI.Config(
            () -> cn.dancingsnow.neoecoae.platform.minecraft.Component.literal(getItemFromBlockEntity().getDisplayName()),
            () -> getTier().getTier(),this::isFormed,statistics::getStoredEnergy,statistics::getMaxEnergy,
            statistics::getEnergyConsumePerTick,statistics::getTotalUsedBytesText,statistics::getCellEntries,
            statistics.createStorageTypeLines(),this::isFormedInfiniteMode,this::isMigratingToInfinite,
            this::canExtractInfiniteComponents,this::getInfiniteDomainText,this::getMissingInfiniteMembers,
            infiniteComponentItemHandler,player,
            (position,expected,shift) -> StorageCellUiExtraction.extract(this,player,position,expected,shift),
            (position,expected) -> StorageCellUiInsertion.insert(this,player,position,expected),
            () -> new ItemStack(getBlockState().getBlock()));
    }"""],
    "extra_member_origin":"createUI: original StorageHostUI.Config arguments, native player/type boundary"
}
partials["gui/storage/StorageHostUI.java"]={
    "scope":"Upstream storage page data records used by the host statistics. Layout tree, sync and actions remain pending.",
    "header":"public final class StorageHostUI",
    "members":["ROOT_WIDTH","ROOT_HEIGHT","StorageTypeLine","CellEntry","Config","CellExtractor","CellInserter","safeEntries"],
    "extra_members":["private StorageHostUI() {}"]
}
partials["gui/storage/StoragePanelTextures.java"]={
    "scope":"Source storage-channel palette and swatch identity used by page snapshots. Texture drawing remains a native MUI2 adapter.",
    "header":"public final class StoragePanelTextures",
    "members":["VALUE_TEXT","DATA_SWATCH","RADIOACTIVE_SWATCH","STRESS_SWATCH","MATTER_SWATCH","FALLBACK_SWATCH","COLORS","swatchIndex"],
    "extra_members":["private StoragePanelTextures() {}"]
}
path.write_text(json.dumps(partials,ensure_ascii=False,indent=2)+"\n",encoding="utf8")
