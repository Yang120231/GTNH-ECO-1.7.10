"""Import only explicitly selected, compiled sources from an immutable Git revision.

No source-set exclusions or replacement classes in dependency namespaces are used.
Run --refresh-manifest after a deliberate adaptation to record its resulting hash.
"""
import argparse
import hashlib
import json
import re
import subprocess
import io
import tarfile
from storage_adapters import adapt_storage
from execution_adapters import adapt_execution, preserve_empty_stack_lists
from import_scope import source_exclusion, resource_exclusion, adapt_resource, adapt_core_business, SHARED_CORE_ASSETS
from pathlib import Path
from batch_imports import merge_batch_imports, reviewed_batch_imports, reviewed_platform_adaptations

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT.parent / "NeoECOAEExtension-1.21.1"
COMMIT = "f6003889a1352ea0d7e2e23a3868eb69943789ca"
PACKAGE = "cn/dancingsnow/neoecoae/"
PARTIAL_IMPORTS = json.loads((ROOT / "porting/partial-imports.json").read_text(encoding="utf8"))
BUSINESS = [
    "crafting/execution/ECOExecutionRuntime.java",
    "api/me/menu/ECOCycleItemList.java", "multiblock/network/NEFrequencyAllocator.java", "crafting/execution/ECOCraftingDispatchResult.java", "blocks/entity/crafting/ECOCraftingPerformanceMeter.java",
    "crafting/execution/worker/ECOExactVirtualLedger.java", "crafting/execution/worker/ECOCraftingThreadWork.java", "crafting/execution/worker/ECOCraftingStackCodec.java",
    "api/me/attachment/ECOCraftingJobAttachment.java", "api/me/attachment/ECOCraftingJobAttachmentRegistry.java", "api/me/attachment/ECOCraftingJobAttachments.java",
    "api/me/lifecycle/ECOCraftingDispatchEvent.java", "api/me/lifecycle/ECOCraftingJobContext.java", "api/me/lifecycle/ECOCraftingJobResult.java",
    "api/me/lifecycle/ECOCraftingLifecycle.java", "api/me/lifecycle/ECOCraftingLifecycleListener.java",
    "api/me/dispatch/ECOCraftingCpuContext.java", "api/me/dispatch/ECOCraftingDispatchPolicy.java", "api/me/dispatch/ECOCraftingDispatchPolicyRegistry.java",
    "api/me/planning/ECOCraftingCalculationSettings.java",
    "crafting/execution/ECOCraftingInputPreview.java", "crafting/execution/ECOCraftingInputResolver.java",
    "crafting/execution/ECOCraftingInputTemplateCache.java", "crafting/execution/ECOCraftingRemainderCache.java", "crafting/execution/ECOProviderCursor.java",
    "crafting/planner/ECOPlanningService.java", "crafting/planner/ECOBigOrderPlanner.java", "crafting/planner/ECOPlannerInventory.java",
    "api/me/planning/ECOPlannerOptions.java", "api/me/network/ECOCraftingNetworkSettings.java", "grid/NEMachineLookup.java", "impl/storage/ECOCreativeCell.java",
    "api/me/provider/ECOFastPathDispatchProvider.java", "api/me/provider/ECOBatchDispatchContext.java",
    "api/me/provider/ECOParallelCraftingProvider.java", "api/me/provider/ECOParallelCraftingProviders.java",
    "api/me/provider/ECOJobAwareCraftingProvider.java", "api/me/provider/ECOBatchCapacityProvider.java",
    "crafting/execution/batch/ECOStatefulBatchPlanner.java", "crafting/execution/batch/ECOExactBatchPlanner.java",
    "crafting/execution/batch/ECOParallelProviderAdapter.java",
    "api/me/ECOFastPathFacade.java", "crafting/execution/ECOCraftingEnergyTransaction.java",
    "crafting/execution/batch/ECOBatchDispatchRequest.java", "crafting/execution/batch/ECOBatchExecutionView.java",
    "crafting/execution/batch/ECOBatchExecutor.java", "crafting/execution/batch/ECOBatchMaterialized.java",
    "crafting/execution/batch/ECOBatchMaterializer.java", "crafting/execution/batch/ECOBatchProvider.java",
    "crafting/execution/batch/ECOStatefulBatchProvider.java", "crafting/execution/batch/ECOLinearBatchExecutionView.java",
    "crafting/execution/batch/ECOBatchInputLease.java",
    "api/me/bigorder/ECOBigOrderAdmission.java", "api/me/bigorder/ECOBigOrderProgress.java", "api/me/bigorder/ECOBigOrderState.java",
    "crafting/execution/bigorder/ECOBigCraftingOrder.java", "crafting/adapter/ae2/ECOExactCraftingPlan.java",
    "crafting/execution/fastpath/ECOFastPathStacks.java",
    "api/computation/ECOComputationCells.java",
    "api/me/bigorder/ECOExactInventory.java",
    "api/me/completion/ECOVirtualCraftingCompletionSink.java",
    "api/me/progress/ECOCraftingProgressSink.java", "api/me/progress/ECOCraftingProgressSnapshot.java", "api/me/progress/ECOCraftingProgressView.java",
    "api/me/output/ECOCraftingOutputClaimRequest.java", "api/me/output/ECOCraftingOutputClaimResult.java", "api/me/output/ECOCraftingOutputClaimSink.java",
    "api/me/output/ECOCraftingOutputRouter.java", "api/me/output/ECOJobOutputReceiver.java",
    "api/me/provider/ECOIndeterminateBatchException.java",
    "crafting/execution/ElapsedTimeTracker.java", "crafting/execution/ECOCraftingDispatchStrategy.java", "crafting/execution/ECOCraftingDispatchBudget.java",
    "crafting/execution/batch/ECOBatchAdmission.java", "crafting/execution/batch/ECOBatchLimits.java", "crafting/execution/batch/ECOBatchMode.java",
    "crafting/execution/batch/ECOBatchPlan.java", "crafting/execution/batch/ECOBatchPlanRequest.java", "crafting/execution/batch/ECOBatchPlanner.java",
    "crafting/execution/batch/ECOPatternIdentity.java", "crafting/execution/fastpath/ECOBatchCraftingHelper.java",
    "blocks/CasingBlock.java",
    "crafting/amount/ExactAmount.java", "crafting/amount/PlannerAmount.java",
    "crafting/amount/NEMath.java", "crafting/display/format/BigNumberFormatter.java",
    "crafting/display/format/DisplayNumbers.java", "crafting/display/format/ExtendedDecimalUnits.java",
    "crafting/display/format/ExactAmountFormatter.java", "impl/storage/StorageByteAccounting.java",
    "impl/storage/SaturatingStackAccumulator.java", "impl/storage/ECOStorageInterfaceMode.java",
    "impl/storage/infinite/HugeAmount.java", "impl/storage/infinite/InfiniteStorageAmounts.java",
    "impl/storage/infinite/MutableInfiniteStorageTotal.java", "impl/storage/infinite/ECOStorageHostMode.java",
    "impl/storage/infinite/ECOInfiniteStorageData.java", "impl/storage/infinite/ECOInfiniteStorageEngine.java",
    "impl/storage/infinite/SavedDataInfiniteStorageEngine.java",
    "impl/storage/ECOStorageCell.java", "impl/storage/ECOCellMutationBatch.java",
    "impl/storage/infinite/ECOInfiniteStorageMember.java", "api/storage/ECOCellType.java",
    "api/storage/IBasicECOCellItem.java", "api/storage/IECOStorageCell.java",
    "api/storage/IECOStorageCellItem.java", "api/storage/IECOStorageMigrationCell.java",
    "api/storage/ECOStorageCells.java", "api/storage/IECOCellHandler.java",
    "impl/storage/infinite/ECOInfiniteStorageDomains.java", "impl/storage/infinite/LegacyInfiniteStorageReader.java",
    "impl/storage/infinite/ECOInfiniteStorageTransfer.java", "api/capability/ECOFeature.java",
    "crafting/execution/fastpath/ECORecipeClassifier.java",
    "crafting/ECOProcessingComponentMatching.java",
    "api/me/planning/ECOPlanningResultRegistry.java", "api/me/diagnostics/ECOCraftingPlanDiagnostics.java",
    "api/me/provider/ECOCraftingProviderRevision.java",
    "api/storage/ECOBigIntegerStorage.java", "api/storage/IECOStoragePriorityHost.java",
    "crafting/display/terminal/ExactAmountSource.java",
    "crafting/display/terminal/CombinedExactAmountSource.java",
    "crafting/display/terminal/ExactAmountCollector.java",
    "crafting/display/terminal/StorageListingAccumulator.java",
    "impl/storage/StorageFaults.java", "impl/storage/StorageInterfaceFilter.java",
    "impl/storage/bulk/BulkUnits.java", "impl/storage/infinite/ECOInfiniteStorage.java",
    "impl/storage/ECOInfiniteResourceCell.java", "items/ECOInfiniteResourceCellItem.java",
    "gui/storage/StoragePriority.java",
    "util/MultiBlockUtil.java", "api/extension/ECOExtensionRegistry.java", "api/extension/ECOAddonApi.java",
    "api/power/ECOPowerRole.java", "api/power/ECOPowerProfiles.java",
    "api/multiblock/ECOMultiblockParts.java", "api/multiblock/ECOMultiblockRequirements.java",
    "blocks/entity/NEBlockEntity.java", "blocks/entity/ECOMachineCasingBlockEntity.java",
    "multiblock/cluster/NECluster.java", "multiblock/calculator/NEClusterCalculator.java",
    "blocks/entity/storage/ECODriveBlockEntity.java", "blocks/entity/storage/ECOEnergyCellBlockEntity.java",
    "blocks/entity/storage/ECOStorageVentBlockEntity.java", "blocks/entity/storage/ECOStorageSystemBlockEntity.java",
    "blocks/entity/storage/ECOInfiniteStorageModeController.java", "blocks/entity/storage/ECOStorageInfiniteRestore.java",
    "blocks/entity/storage/ECOStorageStageRunner.java", "blocks/entity/storage/ECOStorageInterfaceTransfer.java",
    "blocks/entity/storage/StorageDriveLayout.java", "blocks/entity/storage/ECOStorageHostStatistics.java",
    "blocks/entity/ECOMachineInterfaceBlockEntity.java", "multiblock/cluster/NEStorageCluster.java",
    "multiblock/calculator/NEStorageClusterCalculator.java", "util/ICellHost.java", "util/CellHostItemHandler.java",
    "api/storage/ICellHost.java",
    "impl/storage/transfer/ECOIOPortTransfer.java", "impl/storage/transfer/StorageBusTransferFilter.java",
    "impl/storage/transfer/ECOCreativeExtractionFilter.java", "gui/storage/StorageHostUI.java", "gui/common/HostText.java",
    "gui/storage/StoragePanelState.java", "gui/storage/StoragePanelTextures.java", "api/storage/ECOStorageChannelDisplays.java",
    "blocks/entity/storage/StorageCellUiExtraction.java", "blocks/entity/storage/StorageCellUiInsertion.java",
    "api/computation/IECOComputationCellItem.java", "items/ECOComputationCellItem.java",
    "gui/storage/StorageHostCharts.java", "gui/storage/StorageCellGrid.java", "gui/common/HostPanelTier.java",
]
TESTS = [
    "crafting/execution/ECOExecutionRuntimeStartupSeedTest.java",
    "crafting/execution/worker/ECOExactVirtualLedgerTest.java",
    "crafting/execution/ECOCraftingInputCachingTest.java",
    "crafting/planner/ECOPlanningServiceTest.java", "crafting/planner/ECOBigOrderPlannerTest.java", "crafting/planner/ECOProgressivePlanningTest.java",
    "crafting/execution/batch/ECOBatchExecutorTest.java",
    "crafting/execution/fastpath/ECOFastPathExpandedSupportTest.java", "crafting/execution/fastpath/ECOExecutionContractGenerationTest.java",
    "crafting/execution/fastpath/ECODurabilityBatchModelTest.java", "crafting/execution/fastpath/ECOCraftingFastPathCacheTest.java",
    "crafting/execution/fastpath/ECOBigBatchSafetyTest.java",
    "crafting/execution/ECOCraftingEnergyTransactionTest.java",
    "crafting/execution/ECOBatchInputLeaseTest.java",
    "crafting/execution/bigorder/ECOBigCraftingOrderTest.java",
    "crafting/execution/ECOCraftingDispatchStrategyTest.java",
    "gui/storage/StoragePanelStateTest.java",
    "crafting/display/format/ExactAmountFormatterTest.java",
    "impl/storage/infinite/ECOInfiniteStorageDurabilityTest.java",
    "crafting/planner/graph/TarjanSccAnalyzerTest.java", "crafting/planner/cycle/CycleStateEquationTest.java",
    "crafting/planner/cycle/CycleBoundaryImportCapTest.java", "crafting/planner/cycle/CycleCompletenessReviewTest.java",
    "crafting/planner/cycle/CycleResourceRegressionTest.java", "crafting/planner/cycle/ExactGrowthRingTest.java",
    "crafting/planner/cycle/RepeatableCycleGrowthTest.java", "crafting/planner/solve/CycleFailureCacheTest.java",
    "crafting/planner/solve/ExternalDemandCacheTest.java", "crafting/planner/solve/ComponentExternalDemandCacheTest.java",
    "crafting/planner/solve/JointOrderedProposalTest.java", "crafting/planner/ECOPlanningBudgetTest.java",
    "crafting/planner/provenance/MaterialProvenanceTest.java",
    "crafting/display/terminal/StorageListingAccumulatorTest.java",
    "impl/storage/infinite/ECOInfiniteStorageHotPathTest.java",
    "impl/storage/infinite/ECOInfiniteStorageMemberReadTest.java",
    "util/MultiBlockUtilTest.java", "api/extension/ECOExtensionRegistryTest.java",
    "blocks/entity/storage/CombinedStorageAmountTest.java", "blocks/entity/storage/StorageDriveLayoutTest.java",
    "blocks/entity/storage/ECOInfiniteStorageAccessTest.java", "gui/common/HostTextTest.java",
    "impl/storage/StorageInterfaceFilterTest.java", "impl/storage/transfer/ECOIOPortTransferTest.java",
]
REMAP = {
    "appeng.api.storage.AEKeyFilter": "cn.dancingsnow.neoecoae.platform.ae2.crafting.AEKeyFilter",
    "appeng.menu.me.crafting.CraftingPlanSummary": "cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingPlanSummary",
    "appeng.api.networking.crafting.ICraftingCPU": "cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingCPU",
    "appeng.api.networking.crafting.CraftingJobStatus": "cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingJobStatus",
    "appeng.api.config.CpuSelectionMode": "cn.dancingsnow.neoecoae.platform.ae2.crafting.CpuSelectionMode",
    "appeng.crafting.execution.InputTemplate": "cn.dancingsnow.neoecoae.platform.ae2.crafting.InputTemplate",
    "appeng.crafting.execution.CraftingCpuHelper": "cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingCpuHelper",
    "appeng.me.service.CraftingService": "cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService",
    "appeng.api.networking.crafting.CalculationStrategy": "cn.dancingsnow.neoecoae.platform.ae2.crafting.CalculationStrategy",
    "appeng.api.implementations.blockentities.IChestOrDrive": "appeng.api.implementations.tiles.IChestOrDrive",
    "appeng.items.storage.CreativeCellItem": "appeng.items.storage.ItemCreativeStorageCell",
    "appeng.me.cells.CreativeCellHandler": "cn.dancingsnow.neoecoae.platform.ae2.storage.NativeCreativeCellHandler",
    "appeng.api.networking.crafting.ICraftingProvider": "cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider",
    "appeng.crafting.inv.": "cn.dancingsnow.neoecoae.platform.ae2.crafting.inventory.",
    "appeng.me.cluster.IAECluster": "cn.dancingsnow.neoecoae.platform.ae2.multiblock.IAECluster",
    "appeng.me.cluster.IAEMultiBlock": "cn.dancingsnow.neoecoae.platform.ae2.multiblock.IAEMultiBlock",
    "appeng.me.cluster.MBCalculator": "cn.dancingsnow.neoecoae.platform.ae2.multiblock.MBCalculator",
    "appeng.api.networking.GridHelper": "cn.dancingsnow.neoecoae.platform.ae2.grid.GridHelper",
    "appeng.api.networking.IGridMultiblock": "cn.dancingsnow.neoecoae.platform.ae2.grid.IGridMultiblock",
    "appeng.api.networking.IGridNodeListener": "cn.dancingsnow.neoecoae.platform.ae2.grid.NodeListener",
    "appeng.api.networking.IManagedGridNode": "cn.dancingsnow.neoecoae.platform.ae2.grid.ManagedNode",
    "appeng.api.orientation.": "cn.dancingsnow.neoecoae.platform.ae2.grid.",
    "appeng.blockentity.grid.AENetworkedBlockEntity": "cn.dancingsnow.neoecoae.platform.ae2.grid.NativeNetworkBlockEntity",
    "appeng.me.helpers.BlockEntityNodeListener": "cn.dancingsnow.neoecoae.platform.ae2.grid.BlockNodeListener",
    "appeng.me.helpers.IGridConnectedBlockEntity": "appeng.api.networking.IGridHost",
    "com.lowdragmc.lowdraglib2.syncdata.holder.ISyncMangedHolder": "cn.dancingsnow.neoecoae.platform.sync.ManagedHolder",
    "cn.dancingsnow.neoecoae.blocks.NENetworkSwitchBlock": "cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock",
    "cn.dancingsnow.neoecoae.blocks.NEBlock": "cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock",
    "cn.dancingsnow.neoecoae.blocks.ECOMachineCasing": "cn.dancingsnow.neoecoae.platform.registry.NativeCasingBlock",
    "net.minecraft.world.level.block.entity.BlockEntityType": "cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType",
    "net.minecraft.world.level.block.entity.BlockEntity": "net.minecraft.tileentity.TileEntity",
    "net.minecraft.world.level.block.state.properties.": "cn.dancingsnow.neoecoae.platform.minecraft.",
    "net.minecraft.world.level.block.state.BlockState": "cn.dancingsnow.neoecoae.platform.minecraft.BlockState",
    "net.minecraft.world.level.block.Block": "net.minecraft.block.Block",
    "net.minecraft.core.Holder": "cn.dancingsnow.neoecoae.platform.minecraft.Holder",
    "net.minecraft.core.BlockPos": "cn.dancingsnow.neoecoae.platform.minecraft.BlockPos",
    "net.minecraft.core.Direction": "cn.dancingsnow.neoecoae.platform.minecraft.Direction",
    "cn.dancingsnow.neoecoae.mixins.ae2.accessor.DelegatingMEInventoryAccessor": "cn.dancingsnow.neoecoae.platform.ae2.storage.DelegatingStorage",
    "appeng.api.storage.MEStorage": "cn.dancingsnow.neoecoae.platform.ae2.storage.MEStorage",
    "net.minecraft.world.level.material.Fluids": "cn.dancingsnow.neoecoae.platform.registry.Fluids",
    "appeng.api.upgrades.UpgradeInventories": "cn.dancingsnow.neoecoae.platform.ae2.storage.UpgradeInventories",
    "appeng.crafting.pattern.AEProcessingPattern": "cn.dancingsnow.neoecoae.platform.ae2.crafting.AEProcessingPattern",
    "appeng.api.crafting.IPatternDetails": "cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails",
    "appeng.api.networking.crafting.ICraftingService": "cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService",
    "appeng.api.networking.crafting.ICraftingPlan": "cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan",
    "appeng.crafting.CraftingPlan": "cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingPlan",
    "cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection": "cn.dancingsnow.neoecoae.platform.ae2.crafting.PatternRuntime",
    "cn.dancingsnow.neoecoae.config.NEConfig": "cn.dancingsnow.neoecoae.platform.forge.PlanningSettings",
    "appeng.menu.guisync.PacketWritable": "cn.dancingsnow.neoecoae.platform.network.PacketWritable",
    "net.minecraft.network.RegistryFriendlyByteBuf": "cn.dancingsnow.neoecoae.platform.network.RegistryFriendlyByteBuf",
    "java.util.zip.CRC32C": "cn.dancingsnow.neoecoae.platform.jdk.CRC32C",
    "appeng.api.networking.security.IActionSource": "cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource",
    "appeng.api.storage.cells.ICellWorkbenchItem": "appeng.api.storage.ICellWorkbenchItem",
    "appeng.api.storage.cells.": "cn.dancingsnow.neoecoae.platform.ae2.storage.",
    "appeng.api.storage.StorageCells": "cn.dancingsnow.neoecoae.platform.ae2.storage.StorageCells",
    "appeng.api.upgrades.IUpgradeInventory": "cn.dancingsnow.neoecoae.platform.ae2.storage.IUpgradeInventory",
    "appeng.core.definitions.AEItems": "cn.dancingsnow.neoecoae.platform.ae2.storage.AEItems",
    "appeng.util.ConfigInventory": "cn.dancingsnow.neoecoae.platform.ae2.storage.ConfigInventory",
    "appeng.util.prioritylist.IPartitionList": "cn.dancingsnow.neoecoae.platform.ae2.storage.IPartitionList",
    "appeng.api.ids.AEComponents": "cn.dancingsnow.neoecoae.platform.nbt.CellComponents",
    "cn.dancingsnow.neoecoae.api.IECOTier": "cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor",
    "cn.dancingsnow.neoecoae.items.ECOStorageCellItem": "cn.dancingsnow.neoecoae.platform.registry.NativeStorageCellItem",
    "net.minecraft.network.chat.Component": "cn.dancingsnow.neoecoae.platform.minecraft.Component",
    "net.minecraft.server.level.ServerLevel": "net.minecraft.world.WorldServer",
    "net.minecraft.world.level.Level": "net.minecraft.world.World",
    "net.minecraft.world.level.storage.LevelResource": "cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage",
    "net.minecraft.world.item.component.CustomData": "cn.dancingsnow.neoecoae.platform.nbt.CustomData",
    "net.minecraft.core.component.DataComponents": "cn.dancingsnow.neoecoae.platform.nbt.CellComponents",
    "net.minecraft.world.item.ItemStack": "net.minecraft.item.ItemStack",
    "org.slf4j.LoggerFactory": "org.apache.logging.log4j.LogManager",
    "org.slf4j.Logger": "org.apache.logging.log4j.Logger",
    "net.minecraft.util.StringRepresentable": "cn.dancingsnow.neoecoae.platform.minecraft.StringRepresentable",
    "appeng.api.stacks.": "cn.dancingsnow.neoecoae.platform.ae2.key.",
    "net.minecraft.nbt.": "cn.dancingsnow.neoecoae.platform.nbt.",
    "net.minecraft.core.HolderLookup": "cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup",
    "net.minecraft.core.RegistryAccess": "cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess",
    "net.minecraft.core.registries.BuiltInRegistries": "cn.dancingsnow.neoecoae.platform.registry.BuiltInRegistries",
    "net.minecraft.resources.ResourceLocation": "cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation",
    "net.minecraft.world.item.Items": "cn.dancingsnow.neoecoae.platform.registry.Items",
    "net.minecraft.world.level.saveddata.SavedData": "cn.dancingsnow.neoecoae.platform.minecraft.SavedData",
    "net.neoforged.neoforge.common.IOUtilities": "cn.dancingsnow.neoecoae.platform.nbt.IOUtilities",
    "cn.dancingsnow.neoecoae.api.ECOTier": "cn.dancingsnow.neoecoae.platform.registry.StorageTiers",
    "cn.dancingsnow.neoecoae.util.InventoryTestBootstrap": "cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap",
}


def git(*args, cwd=SOURCE):
    return subprocess.check_output(["git", "-c", "http.proxy=", "-c", "https.proxy=", "-C", str(cwd), *args])


def digest(data):
    return hashlib.sha256(data).hexdigest()


def source_members(relative, original):
    parser=ROOT/"porting/SourceMembers.java"
    classes=ROOT/"build/porting-tools"
    classes.mkdir(parents=True,exist_ok=True)
    compiled=classes/"SourceMembers.class"
    if not compiled.exists() or compiled.stat().st_mtime<parser.stat().st_mtime:
        subprocess.check_call(["javac","-encoding","UTF-8","-d",str(classes),str(parser)])
    temporary=ROOT/"build/porting-parse"/relative.replace(":", "/")
    temporary.parent.mkdir(parents=True,exist_ok=True)
    temporary.write_bytes(original)
    return json.loads(subprocess.check_output(["java","-Dfile.encoding=UTF-8","-cp",str(classes),"SourceMembers",str(temporary)],encoding="utf8"))


def select_members(path, original):
    relative=("test:"+path.removeprefix("src/test/java/"+PACKAGE)) if path.startswith("src/test/java/"+PACKAGE) else path.removeprefix("src/main/java/"+PACKAGE)
    selection=PARTIAL_IMPORTS.get(relative)
    if selection is None:return original
    members=source_members(relative,original)
    known={entry["name"] for entry in members}
    included=selection.get("members",[entry["name"] for entry in members if entry["name"] not in selection.get("exclude_members",[])])
    missing=set(included)-known
    if missing:raise SystemExit("Unknown source member selection: "+repr(missing))
    kept=[]
    grouped=set()
    for entry in members:
        if entry["name"] not in included:continue
        if entry["kind"]=="VARIABLE":
            group=[member for member in members if member["kind"]=="VARIABLE" and member["start"]==entry["start"]]
            if entry["start"] in grouped:continue
            if any(member["name"] not in included for member in group):
                raise SystemExit("Cannot partially select a shared variable declaration: "+path+":"+entry["name"])
            grouped.add(entry["start"])
            kept.append(max(group,key=lambda member:member["end"])["text"])
        else:kept.append(entry["text"])
    content=selection["header"]+" {\n"+"\n\n".join(kept+selection.get("extra_members",[]))+"\n}\n"
    source=original.decode("utf8")
    package=re.search(r"(?m)^package [^;]+;",source)[0]
    imports=[line for line in re.findall(r"(?m)^import [^;]+;",source)
             if line.endswith(".*;") or re.search(r"\b"+re.escape(line.removesuffix(";").rsplit(".",1)[-1])+r"\b",content)]
    return (package+"\n\n"+"\n".join(imports)+"\n\n"+content).encode("utf8")


def receiver_calls(text, method, adapter):
    """Keep nested method arguments inside the receiver; overload resolution remains with javac."""
    marker="."+method+"()"
    offset=0
    while True:
        position=text.find(marker,offset)
        if position<0:return text
        cursor=position-1
        round_depth=square_depth=0
        while cursor>=0:
            char=text[cursor]
            if char==")":round_depth+=1
            elif char=="]":square_depth+=1
            elif char=="(":
                if not round_depth:break
                round_depth-=1
            elif char=="[":
                if not square_depth:break
                square_depth-=1
            elif not round_depth and not square_depth and not (char.isalnum() or char in "_$."):break
            cursor-=1
        start=cursor+1
        receiver=text[start:position]
        replacement="cn.dancingsnow.neoecoae.platform.jdk.JdkCompat."+adapter+"("+receiver+")"
        text=text[:start]+replacement+text[position+len(marker):]
        offset=start+len(replacement)


def adapt(data):
    text = data.decode("utf-8").replace("\r\n", "\n")
    text = preserve_empty_stack_lists(text)
    if "public class CasingBlock extends Block" in text:
        text=text.replace("net.minecraft.world.level.block.Block", "cn.dancingsnow.neoecoae.platform.registry.NativeSimpleModelBlock")
        text=text.replace("net.minecraft.world.level.BlockGetter", "cn.dancingsnow.neoecoae.platform.minecraft.WorldView")
        text=text.replace("net.minecraft.world.phys.shapes.", "cn.dancingsnow.neoecoae.platform.minecraft.shape.")
        text=text.replace("extends Block", "extends NativeSimpleModelBlock").replace("Block.box(", "Shapes.box(")
        text=text.replace("CasingBlock(Properties properties)", "CasingBlock(cn.dancingsnow.neoecoae.platform.registry.NativeBlockProperties properties)")
        text=text.replace("BlockGetter", "WorldView")
    world_facade=any("package cn.dancingsnow.neoecoae."+path in text for path in
        ["blocks.entity", "multiblock", "api.multiblock", "api.power"])
    if world_facade:
        text=text.replace("net.minecraft.server.level.ServerLevel", "cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView")
        text=text.replace("net.minecraft.world.level.Level", "cn.dancingsnow.neoecoae.platform.minecraft.WorldView")
        text=re.sub(r"\bServerLevel\b", "ServerWorldView", text)
        text=re.sub(r"\bLevel\b", "WorldView", text)
    for old, new in REMAP.items():
        text = text.replace(old, new)
    text = text.replace("LoggerFactory.", "LogManager.")
    text = re.sub(r"@Deprecated\([^)]*\)", "@Deprecated", text)
    text=text.replace("AE2PatternIntrospection.","PatternRuntime.").replace("NEConfig.","PlanningSettings.")
    text=text.replace("DelegatingMEInventoryAccessor", "DelegatingStorage")
    for old,new in [("IGridNodeListener","NodeListener"),("IManagedGridNode","ManagedNode"),
        ("AENetworkedBlockEntity","NativeNetworkBlockEntity"),("BlockEntityNodeListener","BlockNodeListener"),
        ("IGridConnectedBlockEntity","IGridHost"),("ISyncMangedHolder","ManagedHolder"),
        ("NENetworkSwitchBlock","NativeMachineBlock"),("NEBlock","NativeMachineBlock"),
        ("ECOMachineCasing","NativeCasingBlock"),("BlockEntity","TileEntity")]:
        text=re.sub(r"\b"+old+r"\b",new,text)
    text=text.replace("Block.UPDATE_CLIENTS", "2")
    text=re.sub(r"(\w+\.value\(\)|\w+)\.defaultBlockState\(\)",r"BlockState.defaultState(\1)",text)
    optional_names=re.findall(r"\bOptional<[^;\n]+?>\s+(\w+)",text)
    for name in optional_names:text=text.replace(name+".isEmpty()","!"+name+".isPresent()")
    if "public abstract class NEBlockEntity<" in text:
        text=text.replace("@Getter","").replace("@Setter","")
        marker="    private static final NodeListener<NEBlockEntity<?, ?>> NODE_LISTENER"
        getters="""    public boolean isFormed() { return formed; }
    public void setFormed(boolean value) { formed=value; }
    public C getCluster() { return cluster; }
    public NEClusterCalculator<C> getCalculator() { return calculator; }
"""
        text=text.replace(marker,getters+"\n"+marker)
        text=text.replace("return new ChainedIterator<>();", "return java.util.Collections.emptyIterator();")
        text=text.replace("import appeng.util.iterators.ChainedIterator;", "")
    if "public class ECOComputationCellItem " in text:
        text=text.replace("import net.minecraft.world.item.Item;", "import cn.dancingsnow.neoecoae.platform.registry.NativeMaterialItem;")
        text=text.replace("import net.minecraft.world.item.TooltipFlag;", "")
        text=text.replace("import appeng.core.localization.Tooltips;", "import cn.dancingsnow.neoecoae.platform.minecraft.NumberText;")
        text=text.replace("extends Item implements", "extends NativeMaterialItem implements")
        text=text.replace("@Getter", "")
        text=text.replace("ECOComputationCellItem(Properties properties, IECOTier tier)", "ECOComputationCellItem(IECOTier tier)")
        text=text.replace("super(properties);", 'super("eco_computation_cell_" + tier.getPanelTierName().getString().toLowerCase(java.util.Locale.ROOT));\n        setMaxStackSize(8);')
        text=text.replace("public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag)", "protected void appendTooltip(ItemStack stack, List<Component> tooltipComponents)")
        text=text.replace("Tooltips.ofUnformattedNumber", "NumberText.ofUnformattedNumber")
        text=text.replace("    @Override\n    protected void appendTooltip", "    public IECOTier getTier() { return tier; }\n\n    @Override\n    protected void appendTooltip")
    if "public abstract class NECluster<" in text:
        text=text.replace("@Getter","")
        text=text.replace("    private boolean destroyed = false;", "    private boolean destroyed = false;\n    public boolean isDestroyed() { return destroyed; }")
    if "public final class ECOMultiblockParts" in text:
        text=re.sub(r"    public static int order\(TileEntity member\) \{.*?\n    \}", """    public static int order(TileEntity member) {
        BlockState state=cn.dancingsnow.neoecoae.platform.minecraft.TileViews.state(member);
        return state==null?0:order(new Context(cn.dancingsnow.neoecoae.platform.minecraft.TileViews.level(member),
            cn.dancingsnow.neoecoae.platform.minecraft.TileViews.position(member),state,member));
    }""",text,flags=re.S)
        text=re.sub(r"    @Nullable private static Role builtinRole\(BlockState expected\) \{.*?\n    \}", """    @Nullable private static Role builtinRole(BlockState expected) {
        return expected.getBlock() instanceof cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock block
            ? block.partRole():null;
    }""",text,flags=re.S)
    text=text.replace("details.getClass()", "cn.dancingsnow.neoecoae.platform.ae2.crafting.PatternRuntime.implementationClass(details)")
    if "import net.minecraft.world.World;" in text:text=re.sub(r"\bLevel\b","World",text)
    for old,new in [("IECOTier","StorageTierDescriptor"),("ECOStorageCellItem","NativeStorageCellItem"),("ServerLevel","WorldServer"),("AEComponents","CellComponents"),("DataComponents","CellComponents")]:
        text=re.sub(r"\b"+old+r"\b",new,text)
    text=text.replace("NeoECOAE.MOD_ID","NeoECOAE.MODID")
    text=text.replace("level.getServer()","cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage.server(level)")
    text=text.replace("server.getLevel(Level.OVERWORLD)","cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage.overworld(server)")
    text=text.replace("server.getLevel(World.OVERWORLD)","cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage.overworld(server)")
    text=text.replace("server.getWorldPath(LevelResource.ROOT)","cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage.root(server)")
    text=text.replace("overworld.getDataStorage().computeIfAbsent(","cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage.computeIfAbsent(overworld, ")
    text=text.replace("overworld.registryAccess()","cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess.EMPTY")
    text=text.replace("saved.failures().forEach(failures::add)","saved.failures().forEach(value -> failures.add(new com.google.gson.JsonPrimitive(value)))")
    text=text.replace("cellStack.getOrDefault(CellComponents.STORAGE_CELL_INV, List.of())","CellComponents.readStacks(cellStack)")
    text=text.replace("cellStack.remove(CellComponents.STORAGE_CELL_INV)","CellComponents.clearStacks(cellStack)")
    text=text.replace("cellStack.set(CellComponents.STORAGE_CELL_INV, stacks)","CellComponents.writeStacks(cellStack, stacks)")
    text=text.replace("cellStack.getHoverName()","Component.literal(cellStack.getDisplayName())")
    text=text.replace(".getUpgrades(cellStack)",".getBusinessUpgrades(cellStack)")
    text=text.replace("((NativeStorageCellItem) cellStack.getItem()).getConfigInventory(cellStack)","((NativeStorageCellItem) cellStack.getItem()).getBusinessConfigInventory(cellStack)")
    text=text.replace("stack.getOrDefault(CellComponents.CUSTOM_DATA, CustomData.EMPTY)","CellComponents.readCustomData(stack)")
    text=text.replace("stack.set(CellComponents.CUSTOM_DATA, CustomData.of(tag))","CellComponents.writeCustomData(stack, CustomData.of(tag))")
    text=text.replace("stack.remove(CellComponents.CUSTOM_DATA)","CellComponents.clearCustomData(stack)")
    text=text.replace("stack.remove(CellComponents.STORAGE_CELL_INV)","CellComponents.clearStacks(stack)")
    item_variables=set(re.findall(r"\bItemStack\s+(\w+)",text))
    text=re.sub(r"\b(\w+)\.isEmpty\(\)",lambda m:("cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty("+m[1]+")") if m[1] in item_variables else m[0],text)
    text=text.replace("ItemStack.isSameItem(","cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isSameItem(")
    text=text.replace("ItemStack.isSameItemSameComponents(","cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isSameItemSameComponents(")
    text=text.replace(".isDamageableItem()",".isItemStackDamageable()").replace(".getDamageValue()",".getItemDamage()")
    text=re.sub(r"\b(\w+)\.isComponentsPatchEmpty\(\)",r"cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isComponentsPatchEmpty(\1)",text)
    text=re.sub(r"\b([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)\.(getFirst|getLast|removeFirst|removeLast)\(\)",lambda m:"cn.dancingsnow.neoecoae.platform.jdk.JdkCompat."+{"getFirst":"first","getLast":"last","removeFirst":"removeFirst","removeLast":"removeLast"}[m[2]]+"("+m[1]+")",text)
    text=text.replace("cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.removeFirst(RESULTS)","RESULTS.removeFirst()")
    text=re.sub(r"\b(\w+\.\w+\(\))\.(getFirst|getLast)\(\)",lambda m:"cn.dancingsnow.neoecoae.platform.jdk.JdkCompat."+{"getFirst":"first","getLast":"last"}[m[2]]+"("+m[1]+")",text)
    text=receiver_calls(text,"getFirst","first")
    text=receiver_calls(text,"getLast","last")
    text=text.replace("topologicalOrder.reversed()","cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.reversed(topologicalOrder)")
    text=re.sub(r"\b(\w+)\.isBlank\(\)",r"cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.isBlank(\1)",text)
    text=text.replace(".orElseThrow()",".orElseThrow(java.util.NoSuchElementException::new)")
    text=text.replace("BigInteger.TWO","BigInteger.valueOf(2)")
    text=re.sub(r"\b(keys|amounts|outputs|unlocks)\.toArray\(([\w.]+)\[\]::new\)",r"\1.toArray(new \2[0])",text)
    text=text.replace("getOutputs().toArray(GenericStack[]::new)","getOutputs().toArray(new GenericStack[0])")
    text=text.replace("public sealed interface","public interface")
    text=re.sub(r"(interface\s+\w+[^\{]*?)\s+permits\s+[^\{]+\{",r"\1 {",text)
    if "public class ECOStorageCell " in text:
        text=re.sub(r"@Getter\s+private final ([\w<>]+) (\w+);",lambda m:"private final "+m[1]+" "+m[2]+";\n    public "+m[1]+" get"+m[2][0].upper()+m[2][1:]+"() { return "+m[2]+"; }",text)
        text=text.replace("@Getter\n    private long storedItemCount;","private long storedItemCount;\n    public long getStoredItemCount() { return storedItemCount; }")
    if "public record ECOCellType" in text:
        text=text.replace("@Setter","").replace("@Accessors(fluent = true, chain = true)","")
        text=text.replace("public ECOCellType build()", "public Builder desc(Component value) { desc=value; return this; }\n        public Builder typeCount(int value) { typeCount=value; return this; }\n        public Builder visible(boolean value) { visible=value; return this; }\n        public ECOCellType build()")
    text=re.sub(r"(?m)^import lombok[^;]+;\n","",text)
    text=text.replace("@NoArgsConstructor(access = AccessLevel.PRIVATE)","")
    text=text.replace("@NoArgsConstructor", "")
    if "public class ECOStorageCells" in text:text=text.replace("private static final List<IECOCellHandler>","private ECOStorageCells() {}\n    private static final List<IECOCellHandler>")
    text = re.sub(r"(?m)^([ \t]*)((?:(?:public|private|protected|static|final)\s+)*)record\s+(?=[A-Za-z_$][\w$]*(?:\s*<[^\n(]+>)?\s*\()", r"\1@com.github.bsideup.jabel.Desugar\n\1\2record ", text)
    text = text.replace("ECOTier.L9.getTier()", "StorageTiers.L9_TIER")
    # Static factories are Java API differences, not business policy changes.
    text = re.sub(r"\b(?:java\.util\.)?(List|Set|Map)\.(of|copyOf)\(", lambda m: "cn.dancingsnow.neoecoae.platform.jdk.JdkCompat." + m[1].lower() + {"of":"Of","copyOf":"CopyOf"}[m[2]] + "(", text)
    text = re.sub(r"\b(?:java\.util\.)?(List|Set|Map)\.<([^>]+)>(of|copyOf)\(", lambda m: "cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.<"+m[2]+">" + m[1].lower() + {"of":"Of","copyOf":"CopyOf"}[m[3]] + "(", text)
    text = text.replace(".toList()", ".collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList())")
    text = text.replace("input.readNBytes(length)", "cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.readNBytes(input, length)")
    text = text.replace('Files.writeString(blockedParent, "occupied")', 'Files.write(blockedParent, "occupied".getBytes(java.nio.charset.StandardCharsets.UTF_8))')
    text = text.replace('"Q".repeat(group / PREFIXES.length)', 'cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.repeat("Q", group / PREFIXES.length)')
    text = re.sub(r'("(?:[^"\\]|\\.)*")\.repeat\(([^()]*)\)', r'cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.repeat(\1, \2)', text)
    text = re.sub(r"\b(\w+\.(?:domainId|getIdentity)\([^;\n]*?\))\.isEmpty\(\)", r"!\1.isPresent()", text)
    return adapt_core_business(adapt_execution(adapt_storage(text))).encode("utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--refresh-manifest", action="store_true")
    parser.add_argument("--import-missing", action="store_true", help="With explicit manifest refresh, create only newly selected missing targets")
    parser.add_argument("--reapply-adapters", action="store_true", help="Explicitly regenerate already recorded source adaptations")
    parser.add_argument("--reapply-path",action="append",default=[],help="Regenerate only this explicitly reviewed target")
    parser.add_argument("--apply-core-scope", action="store_true", help="Remove only hash-matching prior imports excluded by the user; refresh adapted shared resources")
    args = parser.parse_args()
    lock_path = ROOT / "porting/source-lock.json"
    previous = json.loads(lock_path.read_text(encoding="utf-8")) if lock_path.exists() else {}
    prior_hashes={entry["target"]:entry["target_sha256"] for entry in previous.get("imported",[])}
    def write_import(path,content):
        target=ROOT/path
        if target.exists():
            expected=prior_hashes.get(path)
            current=digest(target.read_bytes())
            if (expected is not None and current!=expected) or (expected is None and target.read_bytes()!=content):
                raise SystemExit("Refusing to overwrite unrecorded work: "+path+"; review it before refreshing the manifest")
            if expected is not None and not args.reapply_adapters and path not in args.reapply_path and not (args.apply_core_scope and "/resources/" in path):
                return
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(content)
    entries = []
    all_paths = git("ls-tree", "-r", "--name-only", COMMIT, "src").decode().splitlines()
    prefix="src/main/java/"+PACKAGE
    for path in all_paths:
        if path.startswith(prefix+"crafting/execution/fastpath/") and Path(path).name != "ECOSingleCraftingExecutor.java" and path[len(prefix):] not in BUSINESS:
            BUSINESS.append(path[len(prefix):])
        if path.startswith(prefix+"crafting/planner/") and Path(path).name not in {"ECOBigOrderPlanner.java","ECOPlanningService.java","ECOPlannerInventory.java"}:BUSINESS.append(path[len(prefix):])
        if path.startswith(prefix+"api/me/pattern/"):BUSINESS.append(path[len(prefix):])
        test_prefix = "src/test/java/" + PACKAGE
        if path.startswith(test_prefix + "crafting/planner/") and path[len(test_prefix):] not in TESTS:
            if Path(path).name not in {"ECOConfirmationAvailabilityTest.java"}:
                TESTS.append(path[len(test_prefix):])
    selected = ["src/main/java/" + PACKAGE + p for p in BUSINESS]
    selected += ["src/test/java/" + PACKAGE + p for p in TESTS]
    # A Git archive otherwise applies Windows autocrlf and changes source evidence hashes.
    with tarfile.open(fileobj=io.BytesIO(git("-c", "core.autocrlf=false", "archive", COMMIT, "src"))) as archive:
        blobs = {member.name: archive.extractfile(member).read() for member in archive if member.isfile()}
    batch_imports = reviewed_batch_imports(ROOT, COMMIT, blobs)
    batch_reapply = set(args.reapply_path) & set(batch_imports)
    if batch_reapply or (args.reapply_adapters and batch_imports):
        raise SystemExit("Batch adaptations must be reviewed in their dedicated adapter, not regenerated: " + repr(sorted(batch_reapply or batch_imports)))
    selected = [path for path in selected if not source_exclusion(path)]
    excluded = [dict(source=path, source_sha256=digest(blobs[path]), reason=source_exclusion(path))
                for path in all_paths if path.startswith(("src/main/java/", "src/test/java/")) and source_exclusion(path)]
    resources = [p for p in all_paths if p.startswith(("src/main/resources/assets/", "src/generated/resources/assets/", "src/generated/resources/data/", "src/main/resources/data/")) and "/.cache/" not in p]
    # Generated assets intentionally supersede a source template with the same resource path.
    targets = {}
    for source in resources:
        reason = resource_exclusion(source, blobs[source])
        if reason:
            excluded.append(dict(source=source, source_sha256=digest(blobs[source]), reason=reason))
            continue
        relative_resource = source.split("/resources/", 1)[-1]
        target = "src/main/resources/" + SHARED_CORE_ASSETS.get(relative_resource, relative_resource)
        targets[target] = source
    for path in selected:
        original = blobs[path]
        target = ROOT / path
        if not args.refresh_manifest or (args.import_missing and not target.exists()) or path in args.reapply_path:
            adapted=adapt(select_members(path,original))
            if "/test/" in path:
                adapted=adapted.replace(b"cn.dancingsnow.neoecoae.platform.registry.BuiltInRegistries",b"cn.dancingsnow.neoecoae.platform.TestItemRegistry")
                adapted=adapted.replace(b"cn.dancingsnow.neoecoae.platform.registry.Items",b"cn.dancingsnow.neoecoae.platform.TestItems")
                adapted=re.sub(rb"\bBuiltInRegistries\b",b"TestItemRegistry",adapted)
                adapted=re.sub(rb"\bItems\b",b"TestItems",adapted)
                adapted=adapted.replace(b"cn.dancingsnow.neoecoae.platform.registry.Fluids",b"cn.dancingsnow.neoecoae.platform.TestFluids")
                adapted=re.sub(rb"\bFluids\b",b"TestFluids",adapted)
            write_import(path,adapted)
        if not target.exists():
            raise SystemExit("Missing selected compiled source: " + path)
        entry=dict(source=path, target=path, source_sha256=digest(original), target_sha256=digest(target.read_bytes()), kind="test" if "/test/" in path else "business")
        partial_key=("test:"+path.removeprefix("src/test/java/"+PACKAGE)) if "/test/" in path else path.removeprefix("src/main/java/"+PACKAGE)
        partial=PARTIAL_IMPORTS.get(partial_key)
        if partial is not None:entry["partial_source_scope"]=partial
        entries.append(entry)
    # Keep the actual task-progress algorithm without importing the optional-mod legacy ABI owner.
    progress_source = "src/main/java/"+PACKAGE+"crafting/execution/ExecutingCraftingJob.java"
    progress_target = "src/main/java/"+PACKAGE+"crafting/execution/ECOExecutionTaskProgress.java"
    progress_original = blobs[progress_source]
    progress_member = next(member["text"] for member in source_members("task-progress/ExecutingCraftingJob.java", progress_original) if member["name"] == "TaskProgress")
    progress_header = "static class TaskProgress extends cn.dancingsnow.neoecoae.api.me.ExecutingCraftingJob.TaskProgress"
    assert progress_header in progress_member
    field_source = "src/main/java/"+PACKAGE+"api/me/ExecutingCraftingJob.java"
    assert b"public long value;" in blobs[field_source]
    progress_text = progress_member.replace(progress_header, "public final class ECOExecutionTaskProgress", 1).replace("{", "{\npublic long value;", 1)
    progress_content = ("package cn.dancingsnow.neoecoae.crafting.execution;\n" + progress_text + "\n").encode("utf8")
    progress_path = ROOT / progress_target
    if not args.refresh_manifest or (args.import_missing and not progress_path.exists()) or progress_target in args.reapply_path:
        write_import(progress_target, adapt(progress_content))
    if not progress_path.exists(): raise SystemExit("Missing source task progress model")
    entries.append(dict(source=progress_source, target=progress_target, source_sha256=digest(progress_original),
        target_sha256=digest(progress_path.read_bytes()), kind="business", selected_nested_class="TaskProgress",
        inherited_field=dict(source=field_source, source_sha256=digest(blobs[field_source]), member="TaskProgress.value"),
        partial_source_scope=dict(scope="Complete TaskProgress ledger algorithm extracted to its own business class; the source CPU/job owner remains separately pending. The inherited public long value field is retained; optional compatibility inheritance is excluded.")))
    for target_path, source_path in sorted(targets.items()):
        original = blobs[source_path]
        resource_data = adapt_resource(source_path, original)
        target = ROOT / target_path
        if not args.refresh_manifest or (args.import_missing and not target.exists()) or args.apply_core_scope or target_path in args.reapply_path:
            write_import(target_path,resource_data)
        entries.append(dict(source=source_path, target=target_path, source_sha256=digest(original), target_sha256=digest(target.read_bytes()), kind="resource"))
        aliases=[]
        if "/textures/item/" in target_path:
            aliases.append((target_path.replace("/textures/item/","/textures/items/"),original))
            aliases.append((target_path.replace("/textures/item/","/textures/blocks/item/"),original))
        if "/textures/block/" in target_path:
            aliases.append((target_path.replace("/textures/block/","/textures/blocks/"),original))
        if "/lang/" in target_path and target_path.endswith(".json"):
            language=Path(target_path).stem
            region=language.split("_",1)
            native_language=region[0]+"_"+region[1].upper() if len(region)==2 else language
            lang_path=str(Path(target_path).with_name(native_language+".lang")).replace("\\","/")
            strings=json.loads(resource_data)
            lines=[]
            for key,value in sorted(strings.items()):
                value=value.replace("\r", "").replace("\n","\\n")
                lines.append(key+"="+value)
                if key.startswith(("item.","block.")):lines.append(key+".name="+value)
            aliases.append((lang_path,("\n".join(lines)+"\n").encode("utf8")))
        for alias_path,alias_data in aliases:
            alias=ROOT/alias_path
            if not args.refresh_manifest or (args.import_missing and not alias.exists()) or args.apply_core_scope or alias_path in args.reapply_path:
                write_import(alias_path,alias_data)
            if not alias.exists():raise SystemExit("Missing resource alias: "+alias_path)
            entries.append(dict(source=source_path,target=alias_path,source_sha256=digest(original),target_sha256=digest(alias.read_bytes()),kind="platform_resource_alias"))
    partial_paths={"src/main/java/"+PACKAGE+p for p in PARTIAL_IMPORTS if not p.startswith("test:")}
    partial_tests={"src/test/java/"+PACKAGE+p.removeprefix("test:") for p in PARTIAL_IMPORTS if p.startswith("test:")}
    entries = merge_batch_imports(entries, batch_imports)
    for entry in batch_imports.values():
        if entry["kind"] == "resource":
            continue
        if entry.get("complete") is True:
            partial_paths.discard(entry["source"])
            partial_tests.discard(entry["source"])
        elif entry.get("partial_source_scope") is not None or entry.get("complete") is False or entry["source"] != entry["target"]:
            if entry["kind"] == "test":
                partial_tests.add(entry["source"])
            else:
                partial_paths.add(entry["source"])
    selected = set(selected) | {entry["source"] for entry in batch_imports.values()}
    pruned = list(previous.get("removed_excluded_imports", []))
    retained = {entry["target"] for entry in entries}
    for entry in previous.get("imported", []):
        if entry["target"] in retained:
            continue
        target = (ROOT / entry["target"]).resolve()
        if not target.is_relative_to(ROOT.resolve()):
            raise SystemExit("Import removal escapes workspace: " + str(target))
        if target.exists():
            if not args.apply_core_scope or digest(target.read_bytes()) != entry["target_sha256"]:
                raise SystemExit("Excluded import has unreviewed changes: " + entry["target"])
            target.unlink()
        pruned.append(entry)
    lock = dict(previous, schema_version=1, observed_date="2026-10-25", primary_source=dict(repository=str(SOURCE), commit=COMMIT, selected_patches=[], working_tree_status=git("status", "--porcelain").decode("utf-8"), unselected_patch_sha256=digest(git("diff", "--binary", "HEAD"))), reference_port=dict(previous.get("reference_port", {}), repository=str(ROOT.parent / "NeoECOAEExtension-1.20.1"), commit="36dada343abff7533f63cce0d5729950342488f5", purpose="Read-only platform organization reference; no reference business copied"), repositories=previous.get("repositories", []), imported=entries, excluded_sources=excluded, removed_excluded_imports=pruned, pending_business=[p for p in all_paths if p.startswith("src/main/java/") and not source_exclusion(p) and (p not in selected or p in partial_paths)])
    lock["pending_test_members"] = sorted(partial_tests)
    lock["platform_adaptations"] = reviewed_platform_adaptations(ROOT, COMMIT, blobs)
    lock["source_byte_policy"] = "Canonical Git object bytes; archive explicitly disables core.autocrlf. Batch snapshots remain historical; target hashes describe current reviewed files."
    lock_path.write_text(json.dumps(lock, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(dict(business=sum(e["kind"]=="business" for e in entries), tests=sum(e["kind"]=="test" for e in entries), resources=len(targets), pending_business=len(lock["pending_business"]))))


if __name__ == "__main__":
    main()
