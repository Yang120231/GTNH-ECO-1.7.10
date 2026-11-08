package cn.dancingsnow.neoecoae.crafting.upload;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import cn.dancingsnow.neoecoae.crafting.upload.PatternTargetSelector.Evaluation;

/** Player-owned route preferences, saved by Forge with the player and retained across respawns. Server thread only. */
public final class PatternUploadMappings {

    private static final String TAG = "neoecoaePatternUploadMappings";
    private static final int MAX_MAPPINGS = 512;

    private PatternUploadMappings() {}

    public static final class Mapping {

        public final String groupKey, name, nameSuffix;
        public final boolean enabled;

        private Mapping(String groupKey, String name, String nameSuffix, boolean enabled) {
            this.groupKey = groupKey;
            this.name = name;
            this.nameSuffix = nameSuffix;
            this.enabled = enabled;
        }

        public boolean matches(Evaluation evaluation) {
            return groupKey.equals(PatternTargetGroups.mappingKey(evaluation));
        }
    }

    public static Mapping get(EntityPlayer player, RouteHint hint) {
        if (hint == null || hint.getCategoryId()
            .isEmpty()) return null;
        NBTTagCompound tag = data(player, false).getCompoundTag(hint.getCategoryId());
        String key = tag.getString("group");
        return key.isEmpty() ? null
            : new Mapping(key, tag.getString("name"), tag.getString("suffix"), tag.getBoolean("enabled"));
    }

    /** Call only after re-reading a group selected by its server-issued token. */
    public static boolean put(EntityPlayer player, RouteHint hint, Evaluation evaluation) {
        if (hint == null || hint.getCategoryId()
            .isEmpty()
            || !evaluation.match.allowsMapping()
            || evaluation.state == PatternTargetSelector.SlotState.OFFLINE) return false;
        String key = PatternTargetGroups.mappingKey(evaluation);
        NBTTagCompound current = data(player, false);
        String id = hint.getCategoryId();
        if (key.length() > 8192 || !current.hasKey(id, 10) && current.func_150296_c()
            .size() >= MAX_MAPPINGS) return false;
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("group", key);
        tag.setString("name", display(evaluation.target.name()));
        tag.setString("suffix", display(evaluation.target.nameSuffix()));
        tag.setBoolean("enabled", true);
        data(player, true).setTag(id, tag);
        return true;
    }

    public static boolean toggle(EntityPlayer player, RouteHint hint) {
        if (get(player, hint) == null) return false;
        NBTTagCompound tag = data(player, false).getCompoundTag(hint.getCategoryId());
        tag.setBoolean("enabled", !tag.getBoolean("enabled"));
        return true;
    }

    public static boolean remove(EntityPlayer player, RouteHint hint) {
        if (get(player, hint) == null) return false;
        data(player, false).removeTag(hint.getCategoryId());
        return true;
    }

    private static NBTTagCompound data(EntityPlayer player, boolean create) {
        NBTTagCompound entity = player.getEntityData();
        NBTTagCompound persisted = entity.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        NBTTagCompound mappings = persisted.getCompoundTag(TAG);
        if (create) {
            persisted.setTag(TAG, mappings);
            entity.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        }
        return mappings;
    }

    private static String display(String value) {
        if (value == null) return "";
        return value.length() <= 256 ? value : value.substring(0, 256);
    }
}
