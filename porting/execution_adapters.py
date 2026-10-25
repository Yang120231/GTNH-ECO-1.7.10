"""Used execution-side Minecraft/Java API substitutions; execution decisions remain source methods."""
import re

ROOT = "cn.dancingsnow.neoecoae.platform."


def preserve_empty_stack_lists(text):
    # EMPTY is an object on modern Minecraft and null in the native API. Preserve these valid list members.
    edits = []
    for match in re.finditer(r"\bList\.(?:<ItemStack>)?of\(", text):
        depth = 1
        cursor = match.end()
        while cursor < len(text) and depth:
            if text[cursor] == "(": depth += 1
            elif text[cursor] == ")": depth -= 1
            cursor += 1
        if depth == 0 and "ItemStack.EMPTY" in text[match.end():cursor]:
            edits.append((match.start(), match.end(), ROOT + "minecraft.ItemStacks.listOf("))
    for start, end, replacement in reversed(edits): text = text[:start] + replacement + text[end:]
    return text


def adapt_execution(text):
    text = re.sub(r"\b(\w+)\.implementationClass\(\)", ROOT + r"ae2.crafting.PatternRuntime.implementationClass(\1)", text)
    text = text.replace("ExecutingCraftingJob.TaskProgress", "ECOExecutionTaskProgress")
    text = text.replace("Math.ceilDiv(", ROOT + "jdk.JdkCompat.ceilDiv(")
    text = text.replace("Path.of(", "java.nio.file.Paths.get(")
    text = text.replace("Files.writeString(", ROOT + "jdk.JdkCompat.writeString(")
    text = re.sub(r"\b(\w+)\.set\(CellComponents.CUSTOM_NAME,\s*", ROOT + r"nbt.CellComponents.writeCustomName(\1, ", text)
    if "class TechModPlannerScenarios" in text:
        text = text.replace("net.neoforged.neoforge.fluids.FluidStack", ROOT + "fluid.FluidStack")
        text = text.replace("net.minecraftforge.fluids.FluidStack", ROOT + "fluid.FluidStack")
    if "@Test" in text:
        text = re.sub(r"\b(\w+)\.toArray\(([\w.]+)\[\]::new\)", r"\1.toArray(new \2[0])", text)
    text = re.sub(r"\bCraftingService\b", "ICraftingService", text)
    text = text.replace("AE2PatternIntrospection.class", "PatternRuntime.class")
    text = text.replace('getDeclaredField("reloadGeneration")', 'getDeclaredField("generation")')
    for old, new in [("CreativeCellItem", "ItemCreativeStorageCell"), ("CreativeCellHandler", "NativeCreativeCellHandler")]:
        text = re.sub(r"\b" + old + r"\b", new, text)
    if "class ECOCreativeCell" in text:
        text = text.replace("CellConfig.create(stack).size()", "new CellConfig(stack).getSizeInventory()")
    if "class ECOPlannerInventory" in text:
        text = re.sub(r"(?m)^import cn\.dancingsnow\.neoecoae\.compat\.storage\.ECOOptionalUnboundedCells;\n", "", text)
        text = re.sub(r"(?m)^\s*ECOOptionalUnboundedCells\.collect\(cell, unbounded\);\n", "\n", text)
        text = text.replace("host.getOriginalCellInventory(slot)", ROOT + "ae2.storage.NativeDriveReaders.cell(host, slot)")
        text = text.replace("host.getCellItem(slot)", ROOT + "ae2.storage.NativeDriveReaders.cellItem(host, slot)")
    if "class ECOPlanningService" in text:
        text = text.replace("grid.getCraftingService()", "grid.captureCraftingService(level)")
    if "class ECOBigOrderPlanner" in text:
        text = text.replace("grid.getCraftingService()", "grid.captureCraftingService()")
    text = text.replace("Executors.newFixedThreadPool(", ROOT + "jdk.CloseableExecutor.fixed(")
    text = text.replace("appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern", ROOT + "ae2.crafting.IMolecularAssemblerSupportedPattern")
    if "public final class ECOFastPathFacade" in text:
        text = text.replace("resolveProvider(provider)", ROOT + "ae2.crafting.NativeProviderAdapters.resolve(provider)")
        text = text.replace("import cn.dancingsnow.neoecoae.compat.ae2.ECOExternalProviderBlocking;", "import " + ROOT + "ae2.crafting.NativeProviderAdapters;")
        text = text.replace("ECOExternalProviderBlocking.isEnabled(", "NativeProviderAdapters.isEnabled(")
    text = text.replace("net.minecraft.world.item.crafting.CraftingInput", ROOT + "minecraft.crafting.CraftingInput")
    text = text.replace("net.minecraft.world.inventory.TransientCraftingContainer", ROOT + "minecraft.crafting.TransientCraftingContainer")
    text = text.replace(".setDamageValue(", ".setItemDamage(")
    text = re.sub(r"\b(\w+\.\w+\([^()]*\))\.copyWithCount\(([^()]*)\)", ROOT + r"minecraft.ItemStacks.copyWithCount(\1,\2)", text)
    if "class ECOCraftingFastPathCache" in text:
        text = text.replace(ROOT + "jdk.JdkCompat.removeFirst(cache)", "cache.removeFirst()")
    text = text.replace("level.dimension().location()", ROOT + "minecraft.NativeWorldIds.dimension(level)")
    text = text.replace("byte type = before.getId();", "byte type = (byte) before.getId();")
    text = text.replace("CustomData initialData = initialBase.get(CellComponents.CUSTOM_DATA);", "CustomData initialData = CellComponents.readCustomDataOrNull(initialBase);")
    text = text.replace("CustomData resultData = resultBase.get(CellComponents.CUSTOM_DATA);", "CustomData resultData = CellComponents.readCustomDataOrNull(resultBase);")
    text = text.replace("result.set(CellComponents.CUSTOM_DATA, CustomData.of(customDataDelta.apply(crafts)))", "CellComponents.writeCustomData(result, CustomData.of(customDataDelta.apply(crafts)))")
    for name in ["initialBase", "resultBase"]:
        text = text.replace(name + ".remove(CellComponents.CUSTOM_DATA)", "CellComponents.clearCustomData(" + name + ")")
    if "package cn.dancingsnow.neoecoae.crafting.execution" in text:
        for name in ["key", "identity", "durability", "state", "child", "finalDamage", "templateAmount"]:
            text = text.replace(name + ".isEmpty()", "!" + name + ".isPresent()")
        text = text.replace("transition.observedResult().isEmpty()", ROOT + "minecraft.ItemStacks.isEmpty(transition.observedResult())")
        text = text.replace("raw.isEmpty()", ROOT + "minecraft.ItemStacks.isEmpty(raw)")
    if "@Test" in text:
        text = re.sub(r"(ECOStateTransitionBatchModel\.applyCustomDataTransition\([^()]*\)|ECODurabilityBatchModel\.calculateFinalDamage\([^()]*\))\.isEmpty\(\)", r"!\1.isPresent()", text)
        if "SharedConstants.tryDetectVersion()" in text:
            text = re.sub(r"@BeforeAll\s+static void bootstrap\(\) \{.*?\n    \}", "@BeforeAll static void bootstrap() { " + ROOT + "InventoryTestBootstrap.initialize(); }", text, flags=re.S)
            text = re.sub(r"(?m)^import (?:net\.minecraft\.(?:SharedConstants|server\.Bootstrap)|net\.neoforged\.fml\.loading\.LoadingModList);\n", "", text)
    return text
