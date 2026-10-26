"""Import complete worker execution from raw locked Git blobs without overwriting reviewed adaptations."""
import hashlib
import json
import re
import subprocess
from pathlib import Path

from import_business import adapt

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT.parent / "NeoECOAEExtension-1.21.1"
COMMIT = "f6003889a1352ea0d7e2e23a3868eb69943789ca"
PREFIX = "src/main/java/cn/dancingsnow/neoecoae/"
PATHS = ["crafting/execution/worker/" + name + ".java" for name in [
    "ECOCraftingThread", "ECOCraftingFastPathVerifier", "ECOCraftingThreadOutputDiagnostics"]] + ["api/NEFakePlayer.java"]
PLATFORM = "cn.dancingsnow.neoecoae.platform."


def worker_adapt(relative, raw):
    text = raw.decode("utf8")
    text = text.replace("import appeng.menu.AutoCraftingMenu;", "")
    text = text.replace("new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3)", "new TransientCraftingContainer(3, 3)")
    text = text.replace("import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;", "")
    text = text.replace("import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;", "")
    text = text.replace("cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity.VirtualLaneStartResult", "cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingControllerHost.VirtualLaneStartResult")
    text = re.sub(r"\bECOCraftingWorkerBlockEntity\b", "ECOCraftingWorkerRuntimeHost", text)
    text = re.sub(r"\bECOCraftingSystemBlockEntity\b", "ECOCraftingControllerHost", text)
    text = text.replace("worker.getMainNode().getGrid()", "worker.getWorkerGrid()")
    text = text.replace("worker.getLevel()", "worker.getNativeLevel()")
    text = text.replace("worker.getNativeLevel().getGameTime()", "worker.getNativeLevel().getTotalWorldTime()")
    text = text.replace("worker.getNativeLevel().dimension().location()", PLATFORM + "minecraft.NativeWorldIds.dimension(worker.getNativeLevel())")
    text = text.replace("cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingNetworkCluster.VIRTUAL_CRAFTING_POWER_PER_TICK", "ECOCraftingControllerHost.VIRTUAL_CRAFTING_POWER_PER_TICK")
    text = text.replace("net.neoforged.neoforge.common.util.INBTSerializable", PLATFORM + "nbt.INBTSerializable")
    text = text.replace("import net.neoforged.neoforge.common.NeoForge;", "import cpw.mods.fml.common.FMLCommonHandler;")
    text = text.replace("NeoForge.EVENT_BUS", "FMLCommonHandler.instance().bus()")
    text = text.replace("net.neoforged.neoforge.event.entity.player.PlayerEvent", "cpw.mods.fml.common.gameevent.PlayerEvent")
    text = text.replace("net.neoforged.neoforge.common.util.FakePlayerFactory", "net.minecraftforge.common.util.FakePlayerFactory")
    text = text.replace("@Getter\n    private boolean isBusy", "private boolean isBusy")
    text = text.replace("    public boolean isFree() {", "    public boolean isBusy() { return isBusy; }\n\n    public boolean isFree() {")
    text = text.replace("firstOutputItem().copy()", PLATFORM + "minecraft.ItemStacks.copy(firstOutputItem())")
    text = text.replace("work.craftingEventOutput().copy()", PLATFORM + "minecraft.ItemStacks.copy(work.craftingEventOutput())")
    text = text.replace("craftingEventOutput.copy()", PLATFORM + "minecraft.ItemStacks.copy(craftingEventOutput)")
    text = text.replace("craftingInventory.getItem(slot).copy()", PLATFORM + "minecraft.ItemStacks.copy(craftingInventory.getItem(slot))")
    for name in ["initial", "remainder"]:
        text = text.replace(name + ".copy()", PLATFORM + "minecraft.ItemStacks.copy(" + name + ")")
    text = text.replace("stack.getComponentsPatch()", "stack.getTagCompound()")
    text = text.replace("ItemStack.parseOptional(", PLATFORM + "minecraft.ItemStacks.parseOptional(")
    text = text.replace("stack.copyWithCount(MAX_SERIALIZED_ITEM_STACK_COUNT).saveOptional(provider)", PLATFORM + "minecraft.ItemStacks.save(provider, " + PLATFORM + "minecraft.ItemStacks.copyWithCount(stack, MAX_SERIALIZED_ITEM_STACK_COUNT))")
    text = text.replace("stack.copyWithCount(count).saveOptional(provider)", PLATFORM + "minecraft.ItemStacks.save(provider, " + PLATFORM + "minecraft.ItemStacks.copyWithCount(stack, count))")
    text = text.replace("stack.saveOptional(provider)", PLATFORM + "minecraft.ItemStacks.save(provider, stack)")
    text = text.replace("import net.minecraft.world.entity.player.Player;", "import net.minecraft.entity.player.EntityPlayer;")
    text = text.replace("public static Player getFakePlayer", "public static EntityPlayer getFakePlayer")
    text = adapt(text.encode("utf8")).decode("utf8")
    text = text.replace(PLATFORM + "minecraft.ItemStacks.copyWithCount(" + PLATFORM + "minecraft.ItemStacks,", PLATFORM + "minecraft.ItemStacks.copyWithCount(")
    if relative.endswith("ECOCraftingFastPathVerifier.java"):
        text = text.replace(PLATFORM + "minecraft.ItemStacks.isEmpty(output) || inputs.isEmpty() || remaining.isEmpty()", "!output.isPresent() || !inputs.isPresent() || !remaining.isPresent()")
        text = text.replace("outputs.isEmpty() || inputEntries.isEmpty()", "!outputs.isPresent() || inputEntries.isEmpty()")
    for name in ["batchOutputs", "batchInputs", "batchRemaining"]:
        text = text.replace(name + ".isEmpty()", "!" + name + ".isPresent()")
    return text.encode("utf8")


def main():
    imports = []
    for path in [PREFIX + relative for relative in PATHS] + ["src/test/java/cn/dancingsnow/neoecoae/crafting/execution/worker/ECOExactVirtualLedgerTest.java"]:
        relative = path.removeprefix(PREFIX)
        raw = subprocess.check_output(["git", "-C", str(SOURCE), "show", COMMIT + ":" + path])
        target = ROOT / path
        if not target.exists():
            target.parent.mkdir(parents=True, exist_ok=True)
            if path.startswith("src/test/"):
                raise ValueError("Review the native test adaptation before refreshing its manifest: " + path)
            target.write_bytes(worker_adapt(relative, raw))
        imports.append(dict(source=path, target=path, source_sha256=hashlib.sha256(raw).hexdigest(),
            target_sha256=hashlib.sha256(target.read_bytes()).hexdigest(), kind="test" if path.startswith("src/test/") else "business", complete=True,
            adaptation="Complete worker admission, progress, output ownership, portable custody, recovery, verification and persistence. Typed real-host interfaces; native item/NBT, security, ticking, events and fake player adapters."))
    (ROOT / "porting/batches/execution-05-worker.json").write_text(
        json.dumps(dict(source_commit=COMMIT, title="完整工作线程与产物保管恢复", imports=imports), ensure_ascii=False, indent=2) + "\n", encoding="utf8")


if __name__ == "__main__":
    main()
