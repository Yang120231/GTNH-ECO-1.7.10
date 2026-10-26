package cn.dancingsnow.neoecoae.platform.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ScreenShotHelper;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.factory.GuiFactories;
import com.cleanroommc.modularui.screen.ModularScreen;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.multiblock.ECOMachineKind;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;
import cn.dancingsnow.neoecoae.platform.registry.CraftingMachines;
import cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock;
import cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines;
import cn.dancingsnow.neoecoae.platform.ui.multiblock.NativeControllerBuilds;

/** Opt-in framebuffer gallery in the disposable storage-acceptance world. Never opens a user's save. */
public final class NativeUiParityAcceptance {

    private int stage, ticks;
    private volatile Throwable failure;
    private static final String[] NAMES = {"storage-controller", "storage-interface", "storage-priority",
        "workstation", "workstation-outputs", "crafting-controller", "computation-controller", "computation-selector",
        "computation-status", "pattern-bus", "input-hatch", "output-hatch", "computation-interface", "crafting-interface"};

    public void tick(EntityPlayerMP player, ServerTasks.Session server) {
        if (failure != null) throw new IllegalStateException("UI parity fixture failed", failure);
        if (stage >= NAMES.length) return;
        if (++ticks == 1) {
            if (stage == 1) open(server, player, 13, 3);
            else if (stage == 2) {
                open(server, player, 12, 2);
            } else if (stage == 3) prepare(server, player, WorkstationMachines.WORKSTATION, null);
            else if (stage == 5) prepare(server, player,
                (NativeMachineBlock) CraftingMachines.CRAFTING_SYSTEM_L4.value(), ECOMachineKind.CRAFTING);
            else if (stage == 6) prepare(server, player,
                (NativeMachineBlock) CraftingMachines.COMPUTATION_SYSTEM_L4.value(), ECOMachineKind.COMPUTATION);
            else if (stage >= 9) prepare(server, player, (NativeMachineBlock) (stage == 9 ? CraftingMachines.CRAFTING_PATTERN_BUS
                : stage == 10 ? CraftingMachines.INPUT_HATCH : stage == 11 ? CraftingMachines.OUTPUT_HATCH
                : stage == 12 ? CraftingMachines.COMPUTATION_INTERFACE : CraftingMachines.CRAFTING_INTERFACE).value(), null);
        }
        if (ticks == 30 && stage == 0) open(server, player, 12, 2);
        if (ticks == 20 && (stage == 5 || stage == 6)) power(server, player, stage == 5 ? 50 : 70);
        if (ticks == 30 && (stage == 3 || stage == 5 || stage == 6 || stage >= 9))
            open(server, player, machineX(), 2);
        if (ticks == 40 && (stage == 2 || stage == 4 || stage == 7)) {
            String name = stage == 2 ? "storage-priority" : stage == 4 ? "workstation-outputs" : "computation-cpu-list-toggle";
            var screen = ModularScreen.getCurrent();
            if (screen == null) throw new IllegalStateException("Missing MUI2 screen for " + name);
            IWidget button = find(screen.getMainPanel(), name);
            if (!(button instanceof Interactable)) throw new IllegalStateException("Missing native button " + name);
            ((Interactable) button).onMousePressed(0);
        }
        if (stage == 8 && ticks % 20 == 0 && ModularScreen.getCurrent().getMainPanel().getArea().width != 326) {
            IWidget row = find(ModularScreen.getCurrent().getMainPanel(), "cpu-row-0");
            if (!(row instanceof Interactable)) throw new IllegalStateException("Missing first CPU row");
            ((Interactable) row).onMousePressed(0);
        }
        if (stage == 12) checkFuzzySample(player, server);
        if (ticks == 80 && ModularScreen.getCurrent() != null) clearTooltips(ModularScreen.getCurrent().getMainPanel());
        if (ticks < 100) return;
        Minecraft client = Minecraft.getMinecraft();
        if (ModularScreen.getCurrent() == null) {
            if (ticks < 300 && stage == 0) {
                if (ticks % 40 == 0) open(server, player, 12, 2);
                return;
            }
            throw new IllegalStateException("No page at " + NAMES[stage]);
        }
        String filename = "ui-parity-" + NAMES[stage] + ".png";
        ScreenShotHelper.saveScreenshot(client.mcDataDir, filename, client.displayWidth, client.displayHeight,
            client.getFramebuffer());
        var panel = ModularScreen.getCurrent().getMainPanel();
        if (stage == 8 && panel.getArea().width != 326) throw new IllegalStateException("CPU details did not open");
        NeoECOAE.LOG.info("NEOECOAE_UI_PARITY_CAPTURE page={} panel={} dimensions={}x{} file={}",
            NAMES[stage], panel.getName(), panel.getArea().width, panel.getArea().height, filename);
        stage++;
        ticks = 0;
        if (stage == NAMES.length) NeoECOAE.LOG.info("NEOECOAE_UI_PARITY_GALLERY_COMPLETE count={}", stage);
    }

    private void open(ServerTasks.Session session, EntityPlayerMP player, int x, int z) {
        session.execute(() -> {
            try {
                player.setPositionAndUpdate(x + .5, 240, z - 1.5);
                GuiFactories.tileEntity().open(player, x, 240, z);
            } catch (Throwable error) { failure = error; }
        });
    }

    private void prepare(ServerTasks.Session session, EntityPlayerMP player, NativeMachineBlock block, ECOMachineKind kind) {
        int x = machineX();
        session.execute(() -> {
            try {
                player.setPositionAndUpdate(x + .5, 240, .5);
                var world = player.worldObj;
                world.getChunkFromBlockCoords(x, 2);
                if (!WorldView.of(world).setBlockAndUpdate(new BlockPos(x, 240, 2), block.defaultBlockState()))
                    throw new IllegalStateException("Cannot place parity machine " + block.model());
                if (kind != null) {
                    var host = (NEBlockEntity<?, ?>) world.getTileEntity(x, 240, 2);
                    NativeControllerBuilds.config(host, player, kind).build().run();
                }
            } catch (Throwable error) { failure = error; }
        });
    }

    private int machineX() { return stage == 3 ? 30 : stage == 5 ? 50 : stage == 6 ? 70 : 160 + (stage - 9) * 32; }

    private void checkFuzzySample(EntityPlayerMP player, ServerTasks.Session server) {
        if (ticks == 40) server.execute(() -> {
            player.inventory.setItemStack(new net.minecraft.item.ItemStack(net.minecraft.init.Items.iron_ingot, 64));
            cn.dancingsnow.neoecoae.platform.minecraft.NativePlayerInventory.syncMenu(player);
        });
        if (ticks == 50 || ticks == 75) {
            IWidget filter = find(ModularScreen.getCurrent().getMainPanel(), "fuzzy-filter-0");
            if (!(filter instanceof Interactable)) throw new IllegalStateException("Missing fuzzy sample slot");
            ((Interactable) filter).onMousePressed(ticks == 50 ? 0 : 1);
        }
        if (ticks == 65 || ticks == 90) {
            boolean clearing = ticks == 90;
            int x = machineX();
            server.execute(() -> {
                try {
                    var host = (cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity<?>) player.worldObj.getTileEntity(x, 240, 2);
                    var sample = host.getFuzzyPlanningInventory().getStackInSlot(0);
                    if (clearing ? sample != null : sample == null || sample.stackSize != 1
                        || sample.getItem() != net.minecraft.init.Items.iron_ingot)
                        throw new IllegalStateException("Fuzzy sample mismatch after " + (clearing ? "clear" : "copy"));
                    var carried = player.inventory.getItemStack();
                    if (carried == null || carried.stackSize != 64 || carried.getItem() != net.minecraft.init.Items.iron_ingot)
                        throw new IllegalStateException("Fuzzy sample consumed or changed the cursor");
                    NeoECOAE.LOG.info("NEOECOAE_UI_PARITY_FUZZY_OK action={} cursor=64", clearing ? "clear" : "copy");
                    if (clearing) {
                        player.inventory.setItemStack(null);
                        cn.dancingsnow.neoecoae.platform.minecraft.NativePlayerInventory.syncMenu(player);
                    }
                } catch (Throwable error) { failure = error; }
            });
        }
    }

    private void power(ServerTasks.Session session, EntityPlayerMP player, int x) {
        session.execute(() -> {
            try {
                var power = appeng.api.AEApi.instance().definitions().blocks().energyCellCreative().maybeBlock().get();
                player.worldObj.setBlock(x, 240, 1, power);
                var host = player.worldObj.getTileEntity(x, 240, 2);
                ((NEBlockEntity<?, ?>) host).updateState(true);
                if (host instanceof cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity) {
                    var cluster = ((cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity) host).getCluster();
                    if (cluster != null && !cluster.getDrives().isEmpty()) cluster.getDrives().get(0).getInternalInventory()
                        .setStackInSlot(0, new net.minecraft.item.ItemStack(
                            cn.dancingsnow.neoecoae.platform.registry.NativeContent.item("eco_computation_cell_l4")));
                }
            } catch (Throwable error) { failure = error; }
        });
    }

    private static IWidget find(IWidget widget, String name) {
        if (name.equals(widget.getName())) return widget;
        for (IWidget child : widget.getChildren()) {
            var found = find(child, name);
            if (found != null) return found;
        }
        return null;
    }

    private static void clearTooltips(IWidget widget) {
        if (widget instanceof com.cleanroommc.modularui.widget.Widget<?>)
            ((com.cleanroommc.modularui.widget.Widget<?>) widget).tooltip((com.cleanroommc.modularui.screen.RichTooltip) null);
        for (IWidget child : widget.getChildren()) clearTooltips(child);
    }
}
