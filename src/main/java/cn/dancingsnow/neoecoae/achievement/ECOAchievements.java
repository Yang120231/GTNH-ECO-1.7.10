package cn.dancingsnow.neoecoae.achievement;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.Achievement;
import net.minecraft.world.World;
import net.minecraftforge.common.AchievementPage;
import net.minecraftforge.common.util.FakePlayer;

import com.google.gson.JsonObject;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.platform.recipe.SourceRecipes;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The source's displayed {@code eco/*} advancements as native 1.7.10 achievements on their own page.
 *
 * <p>
 * Parent, icon, frame and criteria are read from the source JSON. The {@code recipes/*} advancements only unlock
 * recipe-book entries, which 1.7.10 does not have, so they are not registered. Titles and descriptions come from
 * the language files ({@code achievement.neoecoae.<id>} / {@code .desc}); the page grid position is the only
 * data without a source counterpart.
 */
public final class ECOAchievements {

    /** id, column, row. Parents are listed before their children. */
    private static final Object[][] LAYOUT = { { "root", 0, 0 }, { "host_l4", 2, -1 }, { "host_l6", 4, -1 },
        { "host_l9", 6, -1 }, { "infinite_component", 8, -1 }, { "integrated_working_station", 2, 1 },
        { "large_integrated_formed", 4, 1 } };
    private static final String PENDING = "neoecoaePendingAchievements";
    private static final int SCAN_INTERVAL = 40;

    private static final Map<String, Achievement> ACHIEVEMENTS = new LinkedHashMap<>();
    /** {@code minecraft:inventory_changed} criteria: any one of the items completes the achievement. */
    private static final Map<Achievement, Set<Item>> INVENTORY = new LinkedHashMap<>();
    /** {@code minecraft:tick} criteria: granted as soon as the player is in the world. */
    private static final Set<Achievement> AUTOMATIC = new HashSet<>();

    private ECOAchievements() {}

    public static void register() {
        if (!ACHIEVEMENTS.isEmpty()) throw new IllegalStateException("ECO achievements registered twice");
        for (Object[] entry : LAYOUT) {
            String id = (String) entry[0];
            JsonObject definition = SourceRecipes.read("data/neoecoae/advancement/eco/" + id + ".json");
            JsonObject display = definition.getAsJsonObject("display");
            Achievement parent = null;
            if (definition.has("parent")) {
                parent = ACHIEVEMENTS.get(
                    definition.get("parent")
                        .getAsString()
                        .substring("neoecoae:eco/".length()));
                if (parent == null) throw new IllegalStateException("Unknown parent of ECO achievement " + id);
            }
            Achievement achievement = new Achievement(
                "achievement.neoecoae." + id,
                "neoecoae." + id,
                (Integer) entry[1],
                (Integer) entry[2],
                new ItemStack(
                    item(
                        display.getAsJsonObject("icon")
                            .get("id")
                            .getAsString())),
                parent);
            if (parent == null) achievement.initIndependentStat();
            if (display.get("frame")
                .getAsString()
                .equals("goal")) achievement.setSpecial();
            achievement.registerStat();
            ACHIEVEMENTS.put(id, achievement);
            for (var criterion : definition.getAsJsonObject("criteria")
                .entrySet()) {
                JsonObject value = criterion.getValue()
                    .getAsJsonObject();
                switch (value.get("trigger")
                    .getAsString()) {
                    case "minecraft:tick":
                        AUTOMATIC.add(achievement);
                        break;
                    case "minecraft:inventory_changed":
                        Set<Item> items = INVENTORY.computeIfAbsent(achievement, key -> new HashSet<>());
                        for (var predicate : value.getAsJsonObject("conditions")
                            .getAsJsonArray("items"))
                            for (var itemId : predicate.getAsJsonObject()
                                .getAsJsonArray("items")) items.add(item(itemId.getAsString()));
                        break;
                    case "minecraft:impossible":
                        // Granted from code through award().
                        break;
                    default:
                        throw new IllegalStateException("Unmapped trigger of ECO achievement " + id);
                }
            }
        }
        AchievementPage.registerAchievementPage(
            new AchievementPage(
                "Neo ECO AE Extension",
                ACHIEVEMENTS.values()
                    .toArray(new Achievement[0])));
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new Events());
        NeoECOAE.LOG.info("NEOECOAE_ACHIEVEMENTS_REGISTERED count={}", ACHIEVEMENTS.size());
    }

    private static Item item(String id) {
        int colon = id.indexOf(':');
        Item item = GameRegistry.findItem(id.substring(0, colon), id.substring(colon + 1));
        if (item == null) throw new IllegalStateException("Unregistered ECO achievement item: " + id);
        return item;
    }

    /**
     * Grants a code-triggered achievement. 1.7.10 refuses an achievement whose parent is still locked, so such a
     * grant is remembered on the player and completed by the periodic check once the parent is done.
     */
    public static void award(EntityPlayer player, String id) {
        Achievement achievement = ACHIEVEMENTS.get(id);
        if (achievement == null || !(player instanceof EntityPlayerMP) || player instanceof FakePlayer) return;
        var stats = ((EntityPlayerMP) player).func_147099_x();
        if (stats.hasAchievementUnlocked(achievement)) return;
        if (stats.canUnlockAchievement(achievement)) player.triggerAchievement(achievement);
        else pending(player, true).setBoolean(id, true);
    }

    /** Source {@code ECOAdvancementService.awardNearest} without a preferred player. */
    public static void awardNearest(World world, int x, int y, int z, double radius, String id) {
        if (world == null || world.isRemote) return;
        EntityPlayer nearest = null;
        double best = radius * radius;
        for (Object value : world.playerEntities) {
            EntityPlayer player = (EntityPlayer) value;
            double distance = player.getDistanceSq(x + 0.5, y + 0.5, z + 0.5);
            if (distance <= best && !(player instanceof FakePlayer)) {
                nearest = player;
                best = distance;
            }
        }
        if (nearest != null) award(nearest, id);
    }

    private static NBTTagCompound pending(EntityPlayer player, boolean create) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10)) {
            if (!create) return null;
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!persisted.hasKey(PENDING, 10)) {
            if (!create) return null;
            persisted.setTag(PENDING, new NBTTagCompound());
        }
        return persisted.getCompoundTag(PENDING);
    }

    private static void check(EntityPlayerMP player) {
        var stats = player.func_147099_x();
        NBTTagCompound pending = pending(player, false);
        for (var entry : ACHIEVEMENTS.entrySet()) {
            Achievement achievement = entry.getValue();
            if (stats.hasAchievementUnlocked(achievement)) {
                if (pending != null) pending.removeTag(entry.getKey());
                continue;
            }
            if (!stats.canUnlockAchievement(achievement)) continue;
            boolean done = AUTOMATIC.contains(achievement) || pending != null && pending.getBoolean(entry.getKey());
            Set<Item> items = INVENTORY.get(achievement);
            if (!done && items != null) for (ItemStack stack : player.inventory.mainInventory)
                if (stack != null && items.contains(stack.getItem())) {
                    done = true;
                    break;
                }
            if (done) player.triggerAchievement(achievement);
        }
    }

    public static final class Events {

        @SubscribeEvent
        public void login(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.player instanceof EntityPlayerMP && !(event.player instanceof FakePlayer))
                check((EntityPlayerMP) event.player);
        }

        /** 1.7.10 has no inventory-changed hook covering every source (chests, terminals), so poll instead. */
        @SubscribeEvent
        public void tick(TickEvent.PlayerTickEvent event) {
            if (event.phase == TickEvent.Phase.END && event.player instanceof EntityPlayerMP
                && !(event.player instanceof FakePlayer)
                && event.player.ticksExisted % SCAN_INTERVAL == 0) check((EntityPlayerMP) event.player);
        }
    }
}
