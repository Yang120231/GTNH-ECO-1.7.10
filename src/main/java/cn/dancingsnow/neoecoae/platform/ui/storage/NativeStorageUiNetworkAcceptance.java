package cn.dancingsnow.neoecoae.platform.ui.storage;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ScreenShotHelper;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Programmatic native menu/packet acceptance, explicitly distinct from physical mouse gesture acceptance. */
@SideOnly(Side.CLIENT)
final class NativeStorageUiNetworkAcceptance {

    private final Runnable openInterface, completed;
    private int stage, ticks, changed;
    private ItemStack sample;

    NativeStorageUiNetworkAcceptance(Runnable openInterface, Runnable completed) {
        this.openInterface = openInterface;
        this.completed = completed;
    }

    void tick() {
        ticks++;
        if (ticks - changed > 600)
            throw new IllegalStateException("Native storage UI network acceptance timed out: " + stage);
        if (ticks - changed < 20) return;
        var client = Minecraft.getMinecraft();
        var storage = NativeStorageSession.acceptanceInspection();
        var priority = NativeStoragePrioritySession.acceptanceInspection();
        var storageInterface = NativeStorageInterfaceSession.acceptanceInspection();
        switch (stage) {
            case 0:
                if (storage == null || priority == null
                    || !storage.state()
                        .formed())
                    return;
                require(
                    storage.state()
                        .cellCount() == 12,
                    "Source physical drive snapshot");
                screenshot("storage-main");
                priority.getSyncManager()
                    .findPanelHandlerNullable("storage.priority.window")
                    .openPanel();
                advance();
                break;
            case 1:
                priority.change(1000);
                advance();
                break;
            case 2:
                if (priority.priority() != 1073) return;
                screenshot("priority-popup");
                priority.set(String.valueOf(Integer.MIN_VALUE));
                advance();
                break;
            case 3:
                if (priority.priority() != Integer.MIN_VALUE) return;
                priority.change(-1000);
                advance();
                break;
            case 4:
                require(priority.priority() == Integer.MIN_VALUE, "Priority clamps at signed int minimum");
                priority.set(String.valueOf(Integer.MAX_VALUE));
                advance();
                break;
            case 5:
                if (priority.priority() != Integer.MAX_VALUE) return;
                priority.change(1000);
                advance();
                break;
            case 6:
                require(priority.priority() == Integer.MAX_VALUE, "Priority clamps at signed int maximum");
                priority.set("73");
                priority.getSyncManager()
                    .findPanelHandlerNullable("storage.priority.window")
                    .closePanel();
                advance();
                break;
            case 7:
                if (priority.priority() != 73) return;
                require(storage.click(0, false), "Send native physical disk extraction");
                advance();
                break;
            case 8:
                if (storage.state()
                    .cells()
                    .get(0)
                    .installed()) return;
                require(client.thePlayer.inventory.getItemStack() != null, "Extracted disk on actual cursor");
                screenshot("storage-extracted");
                require(storage.click(0, false), "Send native physical disk insertion");
                advance();
                break;
            case 9:
                if (!storage.state()
                    .cells()
                    .get(0)
                    .installed()) return;
                require(client.thePlayer.inventory.getItemStack() == null, "Inserted disk consumes actual cursor");
                openInterface.run();
                advance();
                break;
            case 10:
                if (storageInterface == null || !storageInterface.isValid()) return;
                screenshot("interface-default");
                storageInterface.selectMode(ECOStorageInterfaceMode.INPUT);
                advance();
                break;
            case 11:
                if (storageInterface.mode() != ECOStorageInterfaceMode.INPUT) return;
                storageInterface.selectMode(ECOStorageInterfaceMode.OUTPUT);
                advance();
                break;
            case 12:
                if (storageInterface.mode() != ECOStorageInterfaceMode.OUTPUT) return;
                storageInterface.toggleCreative();
                storageInterface.toggleStorageBus();
                inventoryClick(0);
                advance();
                break;
            case 13:
                if (storageInterface.ignoringCreative() || storageInterface.excludingStorageBus()) return;
                sample = client.thePlayer.inventory.getItemStack();
                require(
                    sample != null && sample.getItem() == net.minecraft.init.Items.water_bucket,
                    "Actual sample water bucket on cursor");
                sample = sample.copy();
                storageInterface.setFilter(0, NativeStorageFilters.sample(sample, false));
                storageInterface.setFilter(1, NativeStorageFilters.sample(sample, true));
                advance();
                break;
            case 14:
                if (!(storageInterface.filter(0) instanceof AEFluidKey)
                    || !(storageInterface.filter(1) instanceof AEItemKey)) return;
                require(
                    ItemStack.areItemStacksEqual(sample, client.thePlayer.inventory.getItemStack()),
                    "Fluid/item sample retains full cursor stack");
                screenshot("interface-fluid-and-item");
                storageInterface.setFilter(0, null);
                inventoryClick(0);
                inventoryClick(1);
                advance();
                break;
            case 15:
                if (storageInterface.filter(0) != null) return;
                sample = client.thePlayer.inventory.getItemStack();
                require(
                    sample != null && sample.getItem() == net.minecraft.init.Items.iron_ingot && sample.stackSize == 64,
                    "Actual iron stack on cursor");
                sample = sample.copy();
                storageInterface.setFilter(26, NativeStorageFilters.sample(sample, false));
                advance();
                break;
            case 16:
                if (!AEItemKey.of(sample)
                    .equals(storageInterface.filter(26))) return;
                require(
                    ItemStack.areItemStacksEqual(sample, client.thePlayer.inventory.getItemStack()),
                    "Exact item sample retains cursor quantity");
                inventoryClick(1);
                advance();
                break;
            case 17:
                require(client.thePlayer.inventory.getItemStack() == null, "No sample item remains on cursor");
                screenshot("interface-final");
                completed.run();
                advance();
                break;
            default:
                break;
        }
    }

    private void advance() {
        NeoECOAE.LOG.info("NEOECOAE_STORAGE_UI_NETWORK_STAGE {}", stage);
        stage++;
        changed = ticks;
    }

    private static void inventoryClick(int inventoryIndex) {
        var client = Minecraft.getMinecraft();
        for (Object value : client.thePlayer.openContainer.inventorySlots) {
            Slot slot = (Slot) value;
            if (slot.isSlotInInventory(client.thePlayer.inventory, inventoryIndex)) {
                client.playerController
                    .windowClick(client.thePlayer.openContainer.windowId, slot.slotNumber, 0, 0, client.thePlayer);
                return;
            }
        }
        throw new IllegalStateException("No player sample slot in native menu: " + inventoryIndex);
    }

    private static void screenshot(String name) {
        var client = Minecraft.getMinecraft();
        NeoECOAE.LOG.info(
            "NEOECOAE_STORAGE_UI_SCREENSHOT name={} {}",
            name,
            ScreenShotHelper
                .saveScreenshot(client.mcDataDir, client.displayWidth, client.displayHeight, client.getFramebuffer())
                .getUnformattedText());
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException(reason);
    }
}
