"""Native API substitutions for the storage/entity batch, never storage policy."""
import re

ROOT = "cn.dancingsnow.neoecoae.platform."


def adapt_storage(text):
    replacements = {
        "appeng.api.config.PowerUnit;":"appeng.api.config.PowerUnits;",
        "appeng.util.inv.AppEngInternalInventory": ROOT + "inventory.AppEngInternalInventory",
        "appeng.util.inv.InternalInventoryHost": ROOT + "inventory.InternalInventoryHost",
        "net.neoforged.neoforge.items.": ROOT + "inventory.",
        "com.lowdragmc.lowdraglib2.syncdata.annotation.": ROOT + "sync.",
        "com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity": ROOT + "sync.ManagedHolder",
        "com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage": ROOT + "sync.FieldManagedStorage",
        "com.lowdragmc.lowdraglib2.syncdata.storage.IManagedStorage": ROOT + "sync.FieldManagedStorage",
        "appeng.blockentity.powersink.IExternalPowerSink": ROOT + "ae2.grid.IExternalPowerSink",
        "appeng.me.energy.StoredEnergyAmount": ROOT + "ae2.grid.StoredEnergyAmount",
        "appeng.api.networking.events.GridPowerStorageStateChanged": "appeng.api.networking.events.MENetworkPowerStorage",
        "appeng.hooks.ticking.TickHandler": ROOT + "minecraft.TickClock",
        "cn.dancingsnow.neoecoae.util.ServerTaskUtil": ROOT + "minecraft.ServerTaskUtil",
        "cn.dancingsnow.neoecoae.blocks.storage.ECOStorageSystemBlock": ROOT + "registry.NativeStorageHostBlock",
        "cn.dancingsnow.neoecoae.blocks.storage.ECOEnergyCellBlock": ROOT + "registry.NativeEnergyCellBlock",
        "cn.dancingsnow.neoecoae.blocks.storage.ECOStorageVentBlock": ROOT + "registry.NativeVentBlock",
        "cn.dancingsnow.neoecoae.blocks.storage.ECODriveBlock": ROOT + "registry.NativeDriveBlock",
        "cn.dancingsnow.neoecoae.blocks.ECOMachineInterface": ROOT + "registry.NativeInterfaceBlock",
        "cn.dancingsnow.neoecoae.all.NEBlocks": ROOT + "registry.StorageMachines",
        "cn.dancingsnow.neoecoae.all.NETags": ROOT + "registry.StorageTags",
        "cn.dancingsnow.neoecoae.all.NERegistries": ROOT + "registry.StorageRegistries",
        "net.minecraft.core.Registry": ROOT + "registry.Registry",
        "net.minecraft.core.UUIDUtil": ROOT + "minecraft.UUIDUtil",
        "appeng.api.networking.IGrid;": ROOT + "ae2.grid.GridView;",
        "appeng.api.storage.StorageHelper": ROOT + "ae2.storage.StorageHelper",
        "appeng.api.storage.IStorageProvider": ROOT + "ae2.storage.IStorageProvider",
        "appeng.api.storage.IStorageMounts": ROOT + "ae2.storage.IStorageMounts",
        "appeng.api.networking.storage.IStorageService": ROOT + "ae2.storage.StorageServiceView",
        "appeng.api.networking.energy.IEnergyService":"appeng.api.networking.energy.IEnergyGrid",
        "com.lowdragmc.lowdraglib2.gui.ui.UI;": ROOT + "ui.NativeUiThread;",
        "net.minecraft.world.entity.player.Player": "net.minecraft.entity.player.EntityPlayer",
        "net.minecraft.network.chat.MutableComponent": ROOT + "minecraft.Component",
        "cn.dancingsnow.neoecoae.integration.biggerae2.BiggerAE2SingularityPowerService": ROOT + "ae2.storage.OptionalCellPower",
    }
    renames = {"ISyncPersistRPCBlockEntity": "ManagedHolder", "IManagedStorage": "FieldManagedStorage",
        "PowerUnit":"PowerUnits",
        "IStorageService":"StorageServiceView","IEnergyService":"IEnergyGrid",
        "ECOStorageSystemBlock": "NativeStorageHostBlock", "ECOEnergyCellBlock": "NativeEnergyCellBlock",
        "ECOStorageVentBlock": "NativeVentBlock", "ECODriveBlock": "NativeDriveBlock",
        "ECOMachineInterface": "NativeInterfaceBlock", "NEBlocks": "StorageMachines", "NETags": "StorageTags",
        "NERegistries": "StorageRegistries", "TickHandler": "TickClock", "MutableComponent": "Component",
        "GridPowerStorageStateChanged": "MENetworkPowerStorage", "BiggerAE2SingularityPowerService": "OptionalCellPower"}
    for old, new in replacements.items():
        text = text.replace(old, new)
    for old, new in renames.items():
        text = re.sub(r"\b" + old + r"\b", new, text)
    if "import " + ROOT + "ae2.grid.GridView;" in text:
        text = re.sub(r"\bIGrid\b", "GridView", text)
    if "import net.minecraft.entity.player.EntityPlayer;" in text:
        text = re.sub(r"\bPlayer\b", "EntityPlayer", text)
    text = text.replace("ECOTier.", "StorageTiers.")
    text = text.replace("NativeInterfaceBlock<?>", "NativeInterfaceBlock")
    # The temporary construction/UI/optional-cell members remain explicitly pending in partial-imports.json.
    if "public final class StorageHostCharts" in text:
        for name in ["capacityTooltip", "energyTooltip", "resourceTooltip", "domainTooltip"]:
            text=text.replace("private List<Component> " + name, "public List<Component> " + name)
        text=re.sub(r"(?m)^(\s*)record CapacitySegment", r"\1public record CapacitySegment", text)
        text=re.sub(r"(?m)^(\s*)static List<CapacitySegment>", r"\1public static List<CapacitySegment>", text)
        text=re.sub(r"(?m)^(\s*)static Component capacityValue", r"\1public static Component capacityValue", text)
    if "public final class StorageCellGrid" in text:
        text=text.replace("sync.state()", "snapshot.get()")
        text=re.sub(r"(?m)^(\s*)List<Component> tooltip", r"\1public List<Component> tooltip", text)
        text=re.sub(r"(?m)^(\s*)static Component pageText", r"\1public static Component pageText", text)
    if "public final class StoragePanelTextures" in text:
        text=re.sub(r"(?m)^(\s*)static final int VALUE_TEXT", r"\1public static final int VALUE_TEXT", text)
    text = text.replace('            stageRunner.run("construction", () -> buildController.tick(level));', '')
    if "class ECOEnergyCellBlockEntity" in text:
        text = text.replace("new TickingRequest(1, 20, !neighborChangePending)", "new TickingRequest(1, 20, !neighborChangePending, true)")
        text = re.sub(r"    private void setChangedNoTicketUpdate\(\) \{.*?\n    \}",
            "    private void setChangedNoTicketUpdate() { if (!isServerStopping()) getLevel().markLoadedChunkDirty(getBlockPos()); }", text, flags=re.S)
        text = text.replace("Platform.areBlockEntitiesTicking(getLevel(), getBlockPos())", "getLevel().hasChunkAt(getBlockPos())")
        text = re.sub(r"(?m)^import net\.minecraft\.(?:core\.SectionPos|world\.level\.chunk\.status\.ChunkStatus);\n", "", text)
    if "class ECOStorageSystemBlockEntity" in text:
        text = re.sub(r"@Override\s+(public boolean canPlayerInteract)",r"\1",text)
        text = text.replace("@Setter", "")
        text = text.rsplit("}", 1)[0] + "    public void setMirrored(boolean value) { mirrored=value; }\n}\n"
        text=text.replace("AppEngInternalInventory infiniteComponentInventory()", "public AppEngInternalInventory infiniteComponentInventory()")
        text=text.replace("ECOStorageHostMode storageHostMode()", "public ECOStorageHostMode storageHostMode()")
    text = text.replace("    @Override\n    public FieldManagedStorage getRootStorage()", "    public FieldManagedStorage getRootStorage()")
    text = text.replace("    @Override\n    public boolean isClientSide()", "    public boolean isClientSide()")
    text = re.sub(r"@Override\s+(public boolean isClientSide\(\))",r"\1",text)
    text = text.replace("cluster instanceof NEStorageCluster storageCluster", "(Object) cluster instanceof NEStorageCluster storageCluster")
    text = text.replace("persistMigrationContents(serverLevel)", "persistMigrationContents(serverLevel.nativeWorld())")
    text = text.replace("UI.isClientSide()", "NativeUiThread.isClientSide()")
    # Emit precisely the accessors requested by upstream Lombok, before removing that annotation.
    getters = []
    for match in re.finditer(r"@Getter\s+(?:@\w+(?:\([^)]*\))?\s+)*private\s+(?:final\s+)?([\w<>?, .]+?)\s+(\w+)\s*(?:=|;)", text):
        field_type, name = match.groups()
        prefix = "is" if field_type == "boolean" else "get"
        getters.append("    public " + field_type + " " + prefix + name[0].upper() + name[1:] + "() { return " + name + "; }")
    if getters:
        text = text.rsplit("}", 1)[0] + "\n".join(getters) + "\n}\n"
    text = text.replace("@Getter", "")
    if "record StoragePanelState(" in text:
        text=text.replace("record StoragePanelState(","public record StoragePanelState(")
        text=text.replace("    record TypeLine(","    public record TypeLine(")
        text=re.sub(r"(?m)^(\s*)static (?!final)",r"\1public static ",text)
        text=text.replace("static final StoragePanelState EMPTY", "public static final StoragePanelState EMPTY")
        text=text.replace("    TypeLine type(","    public TypeLine type(").replace("    boolean unbounded()", "    public boolean unbounded()")
        text=text.replace("    CompoundTag toTag(","    public CompoundTag toTag(")
        text=re.sub(r"    private static void putComponent\(.*?\n    \}","""    private static void putComponent(CompoundTag tag,String key,Component component) {
        tag.putString(key,component.toJson());
    }""",text,flags=re.S)
        text=re.sub(r"    private static Component component\(.*?\n    \}","""    private static Component component(CompoundTag tag,String key) {
        if(!tag.contains(key,8))return Component.empty();
        try { return Component.fromJson(tag.getString(key)); }
        catch(RuntimeException invalid) { return Component.empty(); }
    }""",text,flags=re.S)
        text=re.sub(r"(?m)^import (?:com\.mojang\.serialization\.DataResult|cn\.dancingsnow\.neoecoae\.platform\.nbt\.NbtOps|(?:net\.minecraft\.network\.chat|cn\.dancingsnow\.neoecoae\.platform\.minecraft)\.ComponentSerialization);\n","",text)
        text=text.replace("cell.stack().isEmpty()",ROOT+"minecraft.ItemStacks.isEmpty(cell.stack())")
        text=text.replace("config.infiniteComponentInventory().getStackInSlot(0).isEmpty()",ROOT+"minecraft.ItemStacks.isEmpty(config.infiniteComponentInventory().getStackInSlot(0))")
        text=text.replace("cell.stack().save(registries)",ROOT+"minecraft.ItemStacks.save(registries,cell.stack())")
        text=text.replace("ItemStack.parseOptional(",ROOT+"minecraft.ItemStacks.parseOptional(")
    if "class StorageHostUI" in text:
        text=text.replace("stack.copy()",ROOT+"minecraft.ItemStacks.copy(stack)")
        text=text.replace("HolderLookup.Provider registries()", "public HolderLookup.Provider registries()")
    text=text.replace("RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)", "RegistryAccess.EMPTY")
    text=text.replace("player.registryAccess()",ROOT+"minecraft.RegistryAccess.EMPTY")
    if "class StorageCellUiExtraction" in text or "class StorageCellUiInsertion" in text:
        text=text.replace("final class StorageCellUi", "public final class StorageCellUi")
        text=text.replace("static boolean extract(","public static boolean extract(").replace("static boolean insert(","public static boolean insert(")
        text=text.replace("player.level().isClientSide", "player.worldObj.isRemote")
        text=text.replace("player.getInventory()", "player.inventory").replace("player.containerMenu","player.openContainer")
        text=text.replace("inventory.items.size()","inventory.mainInventory.length").replace("inventory.getItem(","inventory.getStackInSlot(")
        text=text.replace("inventory.setItem(","inventory.setInventorySlotContents(").replace("inventory.setChanged()","inventory.markDirty()")
        text=text.replace("menu.getCarried()", "player.inventory.getItemStack()").replace("menu.setCarried(","player.inventory.setItemStack(")
        text=text.replace("menu.broadcastChanges()", ROOT + "minecraft.NativePlayerInventory.syncMenu(player)")
        text=text.replace("player.displayClientMessage(reason, true)","player.addChatMessage(reason.nativeText())")
        text=text.replace("drive.getCellStack().isEmpty()",ROOT+"minecraft.ItemStacks.isEmpty(drive.getCellStack())")
        text=text.replace("drive.getCellStack().getCount()",ROOT+"minecraft.ItemStacks.count(drive.getCellStack())")
        text=text.replace("player.inventory.getItemStack().isEmpty()",ROOT+"minecraft.ItemStacks.isEmpty(player.inventory.getItemStack())")
        text=text.replace("ItemStack.matches(","ItemStack.areItemStacksEqual(")
        text=text.replace("carried.shrink(1)",ROOT+"minecraft.ItemStacks.shrink(carried,1)")
    text = text.replace("Math.clamp(", ROOT + "jdk.JdkCompat.clamp(")
    text = text.replace("TickClock.instance().getCurrentTick()", "TickClock.currentTick()")
    text = text.replace("cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage.server(level)", "level.getServer()") if "ServerWorldView" in text else text
    # Native values cross into the already ported world-domain backend only at this boundary.
    text = re.sub(r"(ECOInfiniteStorageDomains\.\w+)\(serverLevel([,)])", r"\1(serverLevel.nativeWorld()\2", text)
    text = re.sub(r"(persistMigrationContents|commitMigrationContents)\(\(ServerWorldView\) level\)", r"\1(((ServerWorldView) level).nativeWorld())", text)
    text = text.replace("serverLevel.getServer()", "serverLevel.getServer().nativeServer()")
    text = text.replace("ItemStack.EMPTY", "null")
    text = re.sub(r"([\w.]+)\.getCount\(\)", r"cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.count(\1)", text)
    text = text.replace("host.getCellStack().copyWithCount(1)", ROOT+"minecraft.ItemStacks.copyWithCount(host.getCellStack(),1)")
    text = re.sub(r"\b([\w.]+)\.copyWithCount\(([^()]*)\)", r"cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.copyWithCount(\1,\2)", text)
    text = re.sub(r"\b(copy|stack)\.shrink\(([^()]*)\)", r"cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.shrink(\1,\2)", text)
    for name in ("cellStack", "stack", "drop", "infiniteComponent", "current"):
        text = text.replace(name + ".isEmpty()", ROOT + "minecraft.ItemStacks.isEmpty(" + name + ")")
        text = text.replace(name + ".getOrDefault(CellComponents.CUSTOM_DATA, CustomData.EMPTY)", "CellComponents.readCustomData(" + name + ")")
        text = re.sub(r"\b" + name + r"\.set\(CellComponents\.CUSTOM_DATA, (CustomData\.(?:of|EMPTY)[^;\n]*?)\)", r"CellComponents.writeCustomData(" + name + r", \1)", text)
        text = text.replace(name + ".remove(CellComponents.CUSTOM_DATA)", "CellComponents.clearCustomData(" + name + ")")
    text = text.replace("!infiniteComponentInventory.getStackInSlot(0).isEmpty()", "!" + ROOT + "minecraft.ItemStacks.isEmpty(infiniteComponentInventory.getStackInSlot(0))")
    text = text.replace("stack.is(StorageTags.Items.INFINITE_CELL_COMPONENTS)", "StorageTags.isInfiniteComponent(stack)")
    text = re.sub(r"\b(\w+\.)?getBlockState\(\)\.getBlock\(\)\.getName\(\)",lambda match:ROOT+"minecraft.Component.literal("+(match[1] or "")+"getBlockState().getBlock().getLocalizedName())",text)
    text = text.replace("identity.isEmpty()", "!identity.isPresent()")
    text = text.replace("cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)", ROOT+"minecraft.RegistryAccess.EMPTY")
    text = text.replace("ReadableNumberConverter.format(Math.max(0L, value), 4)", "ReadableNumberConverter.INSTANCE.toWideReadableForm(Math.max(0L, value))")
    if "@Test" in text:
        if "class StoragePanelStateTest" in text:
            text=text.replace(".toTag()", ".toTag(cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT)")
            text=text.replace("StoragePanelState.fromTag(", "decode(")
            text=text.rsplit("}",1)[0]+"""
    private static StoragePanelState decode(cn.dancingsnow.neoecoae.platform.nbt.Tag tag) {
        return StoragePanelState.fromTag(tag,cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT);
    }
}
"""
        text=text.replace("net.minecraft.world.level.block.Blocks",ROOT+"TestBlocks")
        text=re.sub(r"\bBlocks\b","TestBlocks",text)
        text=text.replace("net.minecraft.block.TestBlocks",ROOT+"TestBlocks")
        text=text.replace("TestBlocks.BlockState.defaultState(STONE)", ROOT+"minecraft.BlockState.defaultState(TestBlocks.STONE)")
        text=text.replace("extractAEPower(anyDouble(), any(), any())", "extractAEPower(anyDouble(), any(), org.mockito.ArgumentMatchers.any(appeng.api.config.PowerMultiplier.class))")
        if "class ECOInfiniteStorageAccessTest" in text:
            text=text.replace("@BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }", """private static org.mockito.MockedStatic<cn.dancingsnow.neoecoae.platform.ae2.storage.StorageCells> nativeCells;
    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        nativeCells=mockStatic(cn.dancingsnow.neoecoae.platform.ae2.storage.StorageCells.class);
    }
    @org.junit.jupiter.api.AfterAll static void closeNativeBoundary() { nativeCells.close(); }""")
        if "class StorageInterfaceFilterTest" in text:
            text=text.replace("var registries = RegistryAccess.EMPTY;", "var registries = cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT;")
            text=re.sub(r"(?m)^import .*\b(?:AEKeyTypesInternal|LargeWorkstationTestKeys|LightningKey);\n","",text)
            text=text.replace("try (var keys = mockStatic(AEKeyTypesInternal.class)) {", "{")
            text=re.sub(r"(?m)^\s*keys.when\(AEKeyTypesInternal::getRegistry\).thenReturn\(LargeWorkstationTestKeys.REGISTRY\);\n","\n",text)
            text=text.replace("RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)","cn.dancingsnow.neoecoae.platform.TestItemRegistry.CONTEXT")
            text=text.replace("LightningKey.EXTREME_HIGH_VOLTAGE", "cn.dancingsnow.neoecoae.platform.TestAddonKey.INSTANCE")
            text=text.replace("cn.dancingsnow.neoecoae.platform.registry.Fluids",ROOT+"TestFluids")
            text=re.sub(r"\bFluids\b","TestFluids",text)
            text=text.replace('named.set(CellComponents.CUSTOM_NAME, Component.literal("Exact sample"))', 'named.setStackDisplayName("Exact sample")')
    return text
