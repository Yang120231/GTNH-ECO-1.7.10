"""Import the complete core CPU/controllers, omitting explicitly excluded optional-mod branches."""
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

from import_business import adapt
from import_scope import replace_java_body
from import_execution_runtime import remove_body

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT.parent / "NeoECOAEExtension-1.21.1"
COMMIT = "f6003889a1352ea0d7e2e23a3868eb69943789ca"
PREFIX = "src/main/java/cn/dancingsnow/neoecoae/"
CPU = ["ECOCraftingCPU", "ECOCraftingCPULogic", "ECOCraftingCpuPersistence", "ECOCraftingCpuView",
    "ECOCraftingTaskScheduler", "ECOCraftingJobLifecycleController", "ECOCraftingOutputDelivery",
    "ECOCraftingPlanUpdater", "ECOCraftingDispatchRequest", "ECOCraftingDispatchAccounting",
    "ECOCraftingFastPathDispatcher", "ECOCraftingProviderDispatcher", "ECOProcessingPatternDispatcher",
    "ECOBatchDispatchPlanning", "ECODispatchStallDiagnostics", "ECOBigOrderController", "ECOCraftingWorkerRecovery"]
PATHS = ["crafting/execution/" + name + ".java" for name in CPU] + [
    "crafting/execution/fastpath/ECOSingleCraftingExecutor.java",
    "api/me/bigorder/ECOBigOrderRequest.java", "api/me/diagnostics/ECOPatternPushDiagnostics.java",
    "impl/storage/ECOCreativeSupply.java", "compat/ae2/ECOProcessingExecutionPattern.java",
    "crafting/execution/worker/ECOCraftingJobLifecycle.java",
]
TESTS = ["ECOCraftingDispatchAccountingTest", "ECOExactInventoryLifecycleTest", "ECOCraftingPlanUpdaterTest"]


def cpu_adapt(relative, raw):
    text = raw.decode("utf8")
    name = Path(relative).stem
    if name == "ECOCraftingCPU":
        text = text.replace("extends cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU", "implements ICraftingCPU")
        text = text.replace("import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;", "")
        text = text.replace("import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity;", "")
        text = re.sub(r"\bNEComputationCluster\b", "ECOCraftingCPUHost", text)
        text = re.sub(r"\bECOComputationThreadingCoreBlockEntity\b", "ECOCraftingCPUOwner", text)
        text = replace_java_body(text, r"public Level getLevel\(\)", " return cluster.getLevel(); ")
        text = replace_java_body(text, r"public IGrid getGrid\(\)", " return cluster.getGrid(); ")
        for signature in ["getOutputClaimSink", "markDirty"]:
            text = re.sub(r"@Override\s+(public [^\n]+ " + signature + r"\()", r"\1", text)
    if name == "ECOCraftingCPULogic":
        text = text.replace("extends cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic", "")
        text = text.replace("implements ECOCraftingProgressSink,", "implements CraftingJobPersistenceContext, ECOCraftingProgressSink,")
        text = re.sub(r"\s*super.job = \w+;", "", text)
        text = remove_body(text, r"    private static boolean thunderboltMixinAnchorEnabled\(\)")
        text = remove_body(text, r"        if \(thunderboltMixinAnchorEnabled\(\)\)")
        text = re.sub(r"        accepted = cn\.dancingsnow\.neoecoae\.compat\.thunderbolt\.[\s\S]+?\.strictMatch\([^;]+;", "", text)
        text = re.sub(r"        return strict \+ cn\.dancingsnow\.neoecoae\.compat\.thunderbolt\.[\s\S]+?\.claim\([^;]+;", "        return strict;", text)
        text = re.sub(r"@Override\s+(protected void finishJob)", r"\1", text)
        text = text.replace("            ClientboundPacket message = new CraftingJobStatusPacket(\n                    jobId, job.finalOutput.what(), job.finalOutput.amount(), job.remainingAmount, status);\n            connectedPlayer.connection.send(message);",
            "            CraftingJobStatusPacket.send(connectedPlayer, jobId, job.finalOutput, job.remainingAmount, status);")
        text = re.sub(r"(?m)^import appeng.core.network.ClientboundPacket;\n", "", text)
        text = text.rsplit("}", 1)[0] + " public Level getLevel() { return cpu.getLevel(); }\n public appeng.api.networking.crafting.ICraftingCPU nativeCPU() { return cpu.getCluster().nativeCPU(cpu); }\n}\n"
    if name == "ECOCraftingJobLifecycleController":
        text = remove_body(text, r"        if \(executionPlan == null && !\(plan instanceof cn\.dancingsnow\.neoecoae\.crafting\.adapter\.ae2\.ECOExactCraftingPlan\)\)")
        text = text.replace("), host.cpu);", "), host.nativeCPU());")
    if name in {"ECOCraftingJobLifecycleController", "ECOCraftingCpuPersistence", "ECOBigOrderController"}:
        text = re.sub(r"        if \((?:job\.exactOrder && linkCpu|restoredJob\.exactOrder && restoredJob\.link)\.isStandalone\(\)\)\s*\w+(?:\.link)?\.setNexus\([^;]+;", "", text)
        text = replace_java_body(text, r"private void bindStandaloneLink\(\)", "") if name == "ECOBigOrderController" else text
        text = re.sub(r"(?m)^\s*cn\.dancingsnow\.neoecoae\.compat\.thunderbolt\.[^;]+;\n", "\n", text)
    if name == "ECOCraftingDispatchAccounting":
        text = re.sub(r"(?m)^\s*cn\.dancingsnow\.neoecoae\.compat\.omnisequence\.[^;]+;\n", "\n", text)
        text = re.sub(r"            boolean virtual = cn\.dancingsnow\.neoecoae\.compat\.extendedaeplus\.[\s\S]+?hasTerminalVirtualOutput\(request\);", "            boolean virtual = false;", text)
    if name == "ECOCraftingOutputDelivery":
        text = re.sub(r"\s*\|\| cn\.dancingsnow\.neoecoae\.compat\.thunderbolt\.[\s\S]+?\.strictMatch\([^;]+?\) != Long.MAX_VALUE", "", text)
    if name == "ECOCraftingFastPathDispatcher":
        text = text.replace("import cn.dancingsnow.neoecoae.compat.useless.ECOUselessDynamicOutputBridge;", "")
        text = re.sub(r"        var registration = ECOUselessDynamicOutputBridge\.prepareExact\([^;]+;", "", text)
        text = remove_body(text, r"        if \(registration == null\)")
        text = re.sub(r"accounting\.applyExact\(request, batch, \(\) -> registration\.commit\([^;]+?\), provider\);", "accounting.applyExact(request, batch, () -> {}, provider);", text)
        start = text.index("        ECOUselessDynamicOutputBridge.Registration registration;")
        end = text.index("        long batchCount", start)
        text = text[:start] + text[end:]
        start = text.index("            accounting.apply(request, result, () -> {")
        end = text.index("            }, provider);", start) + len("            }, provider);")
        text = text[:start] + "            accounting.apply(request, result, () -> {}, provider);" + text[end:]
    if name == "ECOCraftingProviderDispatcher":
        text = text.replace("import cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtDirectDispatch;", "")
        text = re.sub(r"(?m)^    private final Map<ICraftingProvider, Set<IPatternDetails>> omniDeferredPatterns[^;]+;\n", "", text)
        text = re.sub(r"(?m)^    private long omniDispatchTick[^;]+;\n", "", text)
        text = replace_java_body(text, r"void beginTick\(long gameTick\)", " processing.beginTick(gameTick); ")
        text = replace_java_body(text, r"void reset\(\)", " processing.reset(); ")
        text = replace_java_body(text, r"boolean isEligible\(ICraftingProvider provider, ECOCraftingDispatchBudget budget\)",
            " if (ECOExternalProviderBlocking.isEnabled(provider)) return budget.canAttemptOrdinary();\n return budget.canAttemptOrdinary() || fastPath.supportsBatch(provider) || ECOParallelCraftingProviders.find(provider) != null; ")
        text = re.sub(r"(?m)^\s*if \(isOmniDispatchDeferred\([^;]+;\n", "\n", text)
        start = text.index("            // Processing providers get")
        end = text.index("            if (scaledProvider)", start)
        text = text[:start] + "            boolean scaledProvider = !blocking && processing.supportsScaledDispatchCached(request, provider);\n" + text[end:]
        start = text.index("            var omniResult =")
        end = text.index("            var parallelResult", start)
        text = text[:start] + text[end:]
        text = remove_body(text, r"    private Result tryDispatchOmniSequenceBatch\(")
        text = remove_body(text, r"    private boolean isOmniDispatchDeferred\(")
        text = re.sub(r"        var overload = cn\.dancingsnow\.neoecoae\.compat\.thunderbolt\.[\s\S]+?request\.job\(\)\.waitingFor.list\);", "", text)
        text = re.sub(r"(?m)^\s*if \(!overload\.canDispatch\(\)\) continue;\n", "\n", text)
        text = re.sub(r"(?m)^\s*overload\.registerAccepted\([^;]+;\n", "\n", text)
    if name == "ECOProcessingPatternDispatcher":
        text = remove_body(text, r"    boolean supports\(ICraftingProvider provider, @Nullable IPatternDetails pattern\)")
        text = remove_body(text, r"    @Nullable ECOCraftingDispatchResult tryDispatch\(")
        text = remove_body(text, r"    private static final class Contract")
        text = remove_body(text, r"    private static final class AmbiguousDispatchException")
        text = remove_body(text, r"    private static boolean blocksScaledDispatch\(")
        text = re.sub(r"(?m)^import (?:appeng\.helpers\.patternprovider\.PatternProviderLogic|cn\.dancingsnow\.neoecoae\.(?:mixins\.ae2\.accessor\.PatternProviderLogicAccessor|compat\.(?:thunderbolt|ae2lt|mekenergistics|advanced_ae)\.[^;]+));\n", "", text)
        text = text.replace("        var direct = ECOAe2LtDirectDispatch.open(provider);\n", "")
        text = text.replace("blocksScaledDispatch(provider, direct)", "ECOExternalProviderBlocking.isEnabled(provider)")
        text = text.replace("        Object logic = direct == null ? providerLogic(provider) : null;", "        Object logic = providerLogic(provider);")
        text = re.sub(r"(?m)^\s*boolean advancedPattern =[^;]+;\n", "\n", text)
        text = re.sub(r"(?m)^\s*long transportLimit =[^;]+;\n", "        long transportLimit = Long.MAX_VALUE;\n", text)
        text = re.sub(r"        var overload = ECOOverloadCpuAccountingBridge\.prepare\([\s\S]+?request\.job\(\)\.waitingFor.list\);\n        if \(!overload.canDispatch\(\)\) return null;", "", text)
        text = text.replace("direct == null && cachedCopies != offer", "cachedCopies != offer")
        text = text.replace(": advancedPattern ? ECOAdvancedAEPatternScaling.scale(request.pattern(), offer)\n                        : new ECOProcessingExecutionPattern", ": new ECOProcessingExecutionPattern")
        text = re.sub(r"(?m)^\s*if \(direct != null\) return direct.submit\([^;]+;\n", "\n", text)
        text = re.sub(r"(?m)^\s*overload.registerAccepted\([^;]+;\n", "\n", text)
        text = replace_java_body(text, r"private static boolean fullyInserted\(Object logic\)", " return logic instanceof cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider && ((cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider) logic).isScaledProcessingFullyInserted(); ")
        text = replace_java_body(text, r"private static boolean isProcessingPattern\(Object pattern\)", " return pattern instanceof AEProcessingPattern; ")
        text = replace_java_body(text, r"private static Object providerLogic\(ICraftingProvider provider\)", " return provider; ")
        text = replace_java_body(text, r"static boolean supportsScaledDispatch\(ECOCraftingDispatchRequest request, ICraftingProvider provider\)", """
            if (ECOExternalProviderBlocking.isEnabled(provider) || !(provider instanceof cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider)) return false;
            if (provider instanceof ECOParallelCraftingProvider) return false;
            IPatternDetails base = ECOProviderPatternIntrospection.unwrap(request.pattern());
            return base == request.pattern() && isProcessingPattern(base) && request.pattern().getInputs().length > 0
                && !request.pattern().getOutputs().isEmpty() && request.pattern().supportsPushInputsToExternalInventory()
                && ((cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider) provider).supportsScaledProcessing();
        """)
    if name == "ECOCraftingJobLifecycle":
        text = text.replace("extends SavedData", "extends net.minecraft.world.WorldSavedData")
        text = re.sub(r"    private static final Factory<ECOCraftingJobLifecycle> FACTORY =[\s\S]+?;\n", "", text)
        text = replace_java_body(text, r"private static @Nullable ECOCraftingJobLifecycle get\(@Nullable Level level\)", """
            if (!(level instanceof ServerLevel)) return null;
            var server = net.minecraft.server.MinecraftServer.getServer();
            if (server == null) return null;
            var world = server.worldServerForDimension(0);
            if (world == null) return null;
            var data = (ECOCraftingJobLifecycle) world.mapStorage.loadData(ECOCraftingJobLifecycle.class, DATA_NAME);
            if (data == null) {
                data = new ECOCraftingJobLifecycle();
                world.mapStorage.setData(DATA_NAME, data);
            }
            return data;
        """)
        text = text.replace("data.setDirty()", "data.markDirty()")
        text = re.sub(r"@Override\s+(public CompoundTag save)", r"\1", text)
        text = text.rsplit("}", 1)[0] + """
            public ECOCraftingJobLifecycle() { this(DATA_NAME); }
            public ECOCraftingJobLifecycle(String name) { super(name); }
            @Override public void readFromNBT(net.minecraft.nbt.NBTTagCompound data) {
                terminatedJobs.clear();
                terminatedJobs.putAll(load(new CompoundTag(data), cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess.EMPTY).terminatedJobs);
            }
            @Override public void writeToNBT(net.minecraft.nbt.NBTTagCompound data) {
                save(new CompoundTag(data), cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess.EMPTY);
            }
        }
        """
    text = re.sub(r"\n\s*@Nullable\s*\n\s*\n", "\n", text)
    text = text.replace("import appeng.me.service.CraftingService;", "import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;")
    text = text.replace("import appeng.api.features.IPlayerRegistry;", "import cn.dancingsnow.neoecoae.platform.ae2.crafting.PlayerRegistry;")
    text = text.replace("appeng.api.features.IPlayerRegistry.", "cn.dancingsnow.neoecoae.platform.ae2.crafting.PlayerRegistry.")
    text = re.sub(r"\bIPlayerRegistry\b", "PlayerRegistry", text)
    text = text.replace("import net.minecraft.server.level.ServerPlayer;", "import net.minecraft.entity.player.EntityPlayerMP;")
    text = re.sub(r"\bServerPlayer\b", "EntityPlayerMP", text)
    text = text.replace("import appeng.api.networking.crafting.ICraftingCPU;", "import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingCPU;")
    for kind in ["ICraftingLink", "ICraftingSubmitResult", "CraftingSubmitErrorCode"]:
        text = text.replace("import appeng.api.networking.crafting." + kind + ";", "import cn.dancingsnow.neoecoae.platform.ae2.crafting." + kind + ";")
    text = text.replace("appeng.api.networking.crafting.CraftingSubmitErrorCode", "cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingSubmitErrorCode")
    for kind in ["CraftingSubmitResult", "CraftingLink"]:
        text = text.replace("import appeng.crafting." + ("execution." if kind == "CraftingSubmitResult" else "") + kind + ";", "import cn.dancingsnow.neoecoae.platform.ae2.crafting." + kind + ";")
    text = text.replace("import appeng.core.network.clientbound.CraftingJobStatusPacket;", "import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingJobStatusPacket;")
    text = text.replace("import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;", "import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingPatternBus;")
    text = re.sub(r"\bECOCraftingPatternBusBlockEntity\b", "ECOCraftingPatternBus", text)
    text = text.replace("import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;", "import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingWorkerHandle;")
    text = re.sub(r"\bECOCraftingWorkerBlockEntity\b", "ECOCraftingWorkerHandle", text)
    text = text.replace("ECOCraftingWorkerHandle.getLoadedServerWorkers()", "cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingWorkers.loaded()")
    text = text.replace("grid.getMachines(ECOCraftingWorkerHandle.class)", "cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingWorkers.onGrid(grid)")
    text = text.replace("worker.getLevel()", "worker.getNativeLevel()")
    text = text.replace("worker.getNativeLevel().getServer()", "cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage.server(worker.getNativeLevel())")
    text = text.replace("import cn.dancingsnow.neoecoae.compat.ae2.ECOExternalProviderBlocking;", "import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeProviderAdapters;")
    text = text.replace("ECOExternalProviderBlocking.", "NativeProviderAdapters.")
    text = text.replace("import cn.dancingsnow.neoecoae.compat.ae2.ECOProviderPatternIntrospection;", "import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternWrappers;")
    text = text.replace("ECOProviderPatternIntrospection.", "ECOPatternWrappers.")
    adapted = adapt(text.encode("utf8")).decode("utf8")
    adapted = adapted.replace("cn.dancingsnow.neoecoae.platform.nbt.NBTTagCompound", "net.minecraft.nbt.NBTTagCompound")
    adapted = adapted.replace("TickClock.instance()::getCurrentTick", "TickClock::currentTick")
    adapted = adapted.replace("public cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingCPU nativeCPU()", "public appeng.api.networking.crafting.ICraftingCPU nativeCPU()")
    return adapted.encode("utf8")


def main():
    imports = []
    for relative in PATHS:
        source = PREFIX + relative
        raw = subprocess.check_output(["git", "-C", str(SOURCE), "show", COMMIT + ":" + source])
        target = ROOT / source
        if not target.exists():
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(cpu_adapt(relative, raw))
        imports.append(dict(source=source, target=source, source_sha256=hashlib.sha256(raw).hexdigest(),
            target_sha256=hashlib.sha256(target.read_bytes()).hexdigest(), kind="business", complete=True,
            adaptation="Full core CPU behavior; typed live host interfaces and native AE2/Minecraft adapters. Optional-mod transport, accounting and binary anchors excluded; native processing scaled-dispatch algorithm retained."))
    for name in TESTS:
        source = "src/test/java/cn/dancingsnow/neoecoae/crafting/execution/" + name + ".java"
        raw = subprocess.check_output(["git", "-C", str(SOURCE), "show", COMMIT + ":" + source])
        target = ROOT / source
        if not target.exists() or "--reapply-tests" in sys.argv:
            text = raw.decode("utf8")
            if name == "ECOExactInventoryLifecycleTest":
                text = remove_body(text, r"    @Test\s+void dismantledExactInventoryTravelsOnCoreItemWithoutChangingOrdinarySlots\(\)[^{]+")
            text = text.replace("import appeng.crafting.CraftingLink;", "import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingLink;")
            text = text.replace("appeng.crafting.CraftingLink.class", "cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingLink.class")
            text = text.replace("appeng.api.networking.crafting.ICraftingCPU.class", "cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingCPU.class")
            text = text.replace("import appeng.api.crafting.PatternDetailsHelper;", "import cn.dancingsnow.neoecoae.platform.ae2.crafting.PatternDetailsHelper;")
            text = text.replace("mock(ECOCraftingCPU.class)", "mock(ECOCraftingCPU.class, RETURNS_DEEP_STUBS)")
            text = text.replace("appeng.api.networking.IGrid.class", "cn.dancingsnow.neoecoae.platform.ae2.grid.GridView.class")
            text = text.replace("net.minecraft.world.item.Item ", "net.minecraft.item.Item ")
            text = text.replace("true, false), cpu)", "true, false), cpu.getCluster().nativeCPU(cpu))")
            text = text.replace("new CraftingLink(CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false), cpu)", "new CraftingLink(CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false), cpu.getCluster().nativeCPU(cpu))")
            text = text.replace("ExecutedCraftingJob.TaskProgress", "ECOExecutionTaskProgress")
            text = adapt(text.encode("utf8")).decode("utf8")
            text = text.replace("cn.dancingsnow.neoecoae.platform.registry.Items", "cn.dancingsnow.neoecoae.platform.TestItems")
            text = re.sub(r"\bItems\b", "TestItems", text)
            text = text.replace("TestItems.BOOK", "TestItems.BRICK").replace("TestItems.MAP", "TestItems.EMERALD")
            text = re.sub(r"([\w.]+)\.cn\.dancingsnow\.neoecoae\.platform\.jdk\.JdkCompat\.first\(executionRuntime\.candidates\(\)\)", r"cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(\1.executionRuntime.candidates())", text)
            text = re.sub(r"cn\.dancingsnow\.neoecoae\.platform\.minecraft\.RegistryAccess\s*\.fromRegistryOfRegistries\([^;]+\)", "cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT", text)
            text = re.sub(r";\s*var internal = mockStatic\(cn\.dancingsnow\.neoecoae\.platform\.ae2\.key\.AEKeyTypesInternal\.class\)", "", text)
            text = re.sub(r"\s*internal.when\(cn\.dancingsnow\.neoecoae\.platform\.ae2\.key\.AEKeyTypesInternal::getRegistry\)\s*\.thenReturn\([^;]+;", "", text)
            text = text.replace("cn.dancingsnow.neoecoae.platform.registry.Fluids", "cn.dancingsnow.neoecoae.platform.TestFluids")
            text = text.replace("keyTypes.when(AEKeyTypes::getAll).thenReturn(types)", "keyTypes.when(AEKeyTypes::getAll).thenReturn(new java.util.ArrayList<>(types))")
            target.write_text(text, encoding="utf8")
        imports.append(dict(source=source, target=source, source_sha256=hashlib.sha256(raw).hexdigest(),
            target_sha256=hashlib.sha256(target.read_bytes()).hexdigest(), kind="test", complete=True,
            adaptation="Complete source CPU accounting/exact lifecycle/replanning regressions; native APIs and test registry only."))
        if name == "ECOExactInventoryLifecycleTest":
            imports[-1]["complete"] = False
            imports[-1]["partial_source_scope"] = dict(scope="Complete CPU exact custody, cancellation, save and rollback cases. Threading-core item transfer waits for the real machine batch.",
                exclude_members=["dismantledExactInventoryTravelsOnCoreItemWithoutChangingOrdinarySlots"])
    (ROOT / "porting/batches/execution-04-cpu.json").write_text(json.dumps(
        dict(source_commit=COMMIT, title="完整核心 CPU 调度与订单执行", imports=imports), ensure_ascii=False, indent=2) + "\n", encoding="utf8")


if __name__ == "__main__":
    main()
