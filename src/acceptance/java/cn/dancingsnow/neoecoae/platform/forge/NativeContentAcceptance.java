package cn.dancingsnow.neoecoae.platform.forge;

import java.util.ArrayList;
import java.util.HashSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;
import cn.dancingsnow.neoecoae.platform.registry.CoreBlocks;
import cn.dancingsnow.neoecoae.platform.registry.NativeContent;
import cn.dancingsnow.neoecoae.platform.registry.NativeRegisteredBlock;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Opt-in registration/creative/model acceptance in a new isolated development save. */
@SideOnly(Side.CLIENT)
public final class NativeContentAcceptance {

    private final String worldName = "ECO_Content_Acceptance_" + System.currentTimeMillis();
    private int stage, ticks, changed, page;
    private volatile boolean placed;
    private volatile Throwable failure;

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getMinecraft();
        ticks++;
        try {
            if (failure != null) throw new IllegalStateException("Content gallery placement failed", failure);
            if (ticks - changed > 2400) throw new IllegalStateException("Content acceptance timed out: " + stage);
            if (stage == 0 && client.currentScreen instanceof GuiMainMenu) {
                client.launchIntegratedServer(
                    worldName,
                    worldName,
                    new WorldSettings(9251025L, WorldSettings.GameType.CREATIVE, true, false, WorldType.FLAT)
                        .enableCommands());
                advance(1);
            } else if (stage == 1 && client.thePlayer != null && client.getIntegratedServer() != null) {
                advance(2);
                ServerTasks.execute(() -> {
                    try {
                        var server = client.getIntegratedServer();
                        var world = server.worldServerForDimension(0);
                        world.getGameRules()
                            .setOrCreateGameRule("doDaylightCycle", "false");
                        world.setWorldTime(6000);
                        int index = 0;
                        for (var block : CoreBlocks.blocks()
                            .values()) if (block instanceof NativeRegisteredBlock) {
                                int x = index % 9 * 3, z = index / 9 * 3;
                                world.setBlock(x, 63, z, net.minecraft.init.Blocks.quartz_block);
                                world.setBlock(x, 64, z, block, 0, 3);
                                if (world.getBlock(x, 64, z) != block)
                                    throw new IllegalStateException("Block placement failed: " + index);
                                index++;
                            }
                        var player = (net.minecraft.entity.player.EntityPlayerMP) server
                            .getConfigurationManager().playerEntityList.get(0);
                        player.setGameType(WorldSettings.GameType.CREATIVE);
                        player.capabilities.isFlying = true;
                        player.sendPlayerAbilities();
                        player.setLocationAndAngles(12.5, 72, -9, 0, 35);
                        player.setPositionAndUpdate(12.5, 72, -9);
                        placed = true;
                        NeoECOAE.LOG.info("NEOECOAE_CONTENT_PLACEMENT_OK blocks={} save={}", index, worldName);
                    } catch (Throwable error) {
                        failure = error;
                    }
                });
            } else if (stage == 2 && placed && ticks - changed > 100) {
                CoreBlocks.checkCreativeItems();
                var stacks = new ArrayList<ItemStack>();
                NativeContent.TAB.displayAllReleventItems(stacks);
                var visible = new HashSet<Item>();
                for (ItemStack stack : stacks) {
                    if (!visible.add(stack.getItem()))
                        throw new IllegalStateException("Duplicate creative item: " + stack);
                    if (!net.minecraft.util.StatCollector.canTranslate(stack.getUnlocalizedName() + ".name"))
                        throw new IllegalStateException("Missing content translation: " + stack.getUnlocalizedName());
                    String expected = net.minecraft.util.StatCollector
                        .translateToLocal(stack.getUnlocalizedName() + ".name");
                    if (!expected.equals(stack.getDisplayName())) throw new IllegalStateException(
                        "Content display name differs from translation: " + stack.getUnlocalizedName()
                            + " -> "
                            + stack.getDisplayName());
                }
                for (var block : CoreBlocks.blocks()
                    .values()) {
                    if (!visible.contains(Item.getItemFromBlock(block)))
                        throw new IllegalStateException("Block missing from actual creative listing: " + block);
                    var stack = new ItemStack(block);
                    if (!stack.getDisplayName()
                        .equals(
                            net.minecraft.util.StatCollector.translateToLocal(stack.getUnlocalizedName() + ".name")))
                        throw new IllegalStateException(
                            "Block display name differs from source translation: " + stack.getUnlocalizedName());
                }
                if (visible.size() != 131)
                    throw new IllegalStateException("Expected 131 visible core items: " + visible.size());
                checkOrder(stacks);
                NeoECOAE.LOG.info(
                    "NEOECOAE_CONTENT_CREATIVE_OK visible={} blocks={} standalone=61 excluded_cards=7",
                    visible.size(),
                    CoreBlocks.blocks()
                        .size());
                screenshot(client, "gallery");
                var creative = new GuiContainerCreative(client.thePlayer);
                client.displayGuiScreen(creative);
                var select = GuiContainerCreative.class.getDeclaredMethod("setCurrentCreativeTab", CreativeTabs.class);
                select.setAccessible(true);
                select.invoke(creative, NativeContent.TAB);
                var tabPage = GuiContainerCreative.class.getDeclaredField("tabPage");
                tabPage.setAccessible(true);
                tabPage.setInt(null, NativeContent.TAB.getTabPage());
                advance(3);
            } else if (stage == 3 && ticks - changed > 20) {
                screenshot(client, "creative-" + page);
                page++;
                if (page == 4) {
                    NeoECOAE.LOG.info(
                        "NEOECOAE_CONTENT_ACCEPTANCE_OK total_blocks=70 new_blocks=47 visible_items=131 creative_pages=4 save={}",
                        worldName);
                    client.theWorld.sendQuittingDisconnectingPacket();
                    client.loadWorld(null);
                    client.displayGuiScreen(new GuiMainMenu());
                    advance(4);
                } else {
                    var screen = (GuiContainerCreative) client.currentScreen;
                    float scroll = page / 3F;
                    var currentScroll = GuiContainerCreative.class.getDeclaredField("currentScroll");
                    currentScroll.setAccessible(true);
                    currentScroll.setFloat(screen, scroll);
                    var method = screen.inventorySlots.getClass()
                        .getDeclaredMethod("scrollTo", float.class);
                    method.setAccessible(true);
                    method.invoke(screen.inventorySlots, scroll);
                    changed = ticks;
                }
            } else if (stage == 4 && ticks - changed > 20) client.shutdown();
        } catch (Throwable error) {
            NeoECOAE.LOG.error("NEOECOAE_CONTENT_ACCEPTANCE_FAILED stage=" + stage, error);
            client.shutdown();
            advance(5);
        }
    }

    private void advance(int next) {
        stage = next;
        changed = ticks;
    }

    private static void checkOrder(java.util.List<ItemStack> stacks) {
        var ids = new ArrayList<String>();
        for (var stack : stacks) ids.add(cn.dancingsnow.neoecoae.platform.registry.CreativeContentOrder.id(stack));
        String[] progression = { "storage_system_l4", "storage_system_l6", "storage_system_l9",
            "eco_item_storage_cell_16m", "eco_item_storage_cell_64m", "eco_item_storage_cell_256m",
            "crafting_system_l4", "crafting_system_l6", "crafting_system_l9", "crafting_high_energy_network_switch",
            "computation_system_l4", "computation_system_l6", "computation_system_l9",
            "computation_high_energy_network_switch", "integrated_working_station",
            "large_integrated_working_station_output_hatch", "iron_dust", "superconducting_processor", "aluminum_sword",
            "black_tungsten_alloy_hoe" };
        int previous = -1;
        for (String id : progression) {
            int current = ids.indexOf("neoecoae:" + id);
            if (current <= previous) throw new IllegalStateException("Unexpected creative group/tier order: " + id);
            previous = current;
        }
        if (!ids.get(0)
            .equals("neoecoae:storage_system_l4")) throw new IllegalStateException("Storage host must lead the tab");
        var repeated = new ArrayList<ItemStack>();
        NativeContent.TAB.displayAllReleventItems(repeated);
        var repeatedIds = new ArrayList<String>();
        for (var stack : repeated)
            repeatedIds.add(cn.dancingsnow.neoecoae.platform.registry.CreativeContentOrder.id(stack));
        if (!ids.equals(repeatedIds)) throw new IllegalStateException("Creative display order changed between reads");
        NeoECOAE.LOG.info(
            "NEOECOAE_CONTENT_ORDER_OK visible={} groups=storage,crafting,computation,workstation,materials,tools tier_order=ascending order={}",
            ids.size(),
            ids);
    }

    private static void screenshot(Minecraft client, String label) {
        NeoECOAE.LOG.info(
            "NEOECOAE_CONTENT_SCREENSHOT label={} {}",
            label,
            ScreenShotHelper
                .saveScreenshot(client.mcDataDir, client.displayWidth, client.displayHeight, client.getFramebuffer())
                .getUnformattedText());
    }
}
