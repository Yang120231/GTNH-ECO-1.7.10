package cn.dancingsnow.neoecoae.multiblock;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.tile.ECOControllerTier;

public final class ECOFormationVisibility {

    private static final Set<Key> HIDDEN_BLOCKS = new HashSet<Key>();
    private static final Set<SimpleKey> CLIENT_HIDDEN_BLOCKS = new HashSet<SimpleKey>();
    private static final Set<Key> FORMED_MEMBER_BLOCKS = new HashSet<Key>();
    private static final Set<SimpleKey> CLIENT_FORMED_MEMBER_BLOCKS = new HashSet<SimpleKey>();
    private static final Set<Key> MIRRORED_FORMED_MEMBER_BLOCKS = new HashSet<Key>();
    private static final Set<SimpleKey> CLIENT_MIRRORED_FORMED_MEMBER_BLOCKS = new HashSet<SimpleKey>();
    private static final Map<Key, ECOControllerTier> FORMED_MEMBER_TIERS = new HashMap<Key, ECOControllerTier>();
    private static final Map<SimpleKey, ECOControllerTier> CLIENT_FORMED_MEMBER_TIERS = new HashMap<SimpleKey, ECOControllerTier>();

    private ECOFormationVisibility() {}

    public static void replace(World world, List<ECOFormationBlockPos> oldPositions,
        List<ECOFormationBlockPos> newPositions) {
        if (world == null) {
            return;
        }
        remove(world, oldPositions);
        add(world, newPositions);
    }

    public static boolean isHidden(IBlockAccess world, int x, int y, int z) {
        if (world instanceof World) {
            return HIDDEN_BLOCKS.contains(new Key((World) world, x, y, z))
                || CLIENT_HIDDEN_BLOCKS.contains(new SimpleKey(x, y, z));
        }
        return CLIENT_HIDDEN_BLOCKS.contains(new SimpleKey(x, y, z));
    }

    public static boolean shouldRenderFormedMember(IBlockAccess world, int x, int y, int z) {
        if (world instanceof World) {
            return FORMED_MEMBER_BLOCKS.contains(new Key((World) world, x, y, z))
                || CLIENT_FORMED_MEMBER_BLOCKS.contains(new SimpleKey(x, y, z));
        }
        return CLIENT_FORMED_MEMBER_BLOCKS.contains(new SimpleKey(x, y, z));
    }

    public static boolean isMirroredFormedMember(IBlockAccess world, int x, int y, int z) {
        if (world instanceof World) {
            return MIRRORED_FORMED_MEMBER_BLOCKS.contains(new Key((World) world, x, y, z))
                || CLIENT_MIRRORED_FORMED_MEMBER_BLOCKS.contains(new SimpleKey(x, y, z));
        }
        return CLIENT_MIRRORED_FORMED_MEMBER_BLOCKS.contains(new SimpleKey(x, y, z));
    }

    public static ECOControllerTier getFormedMemberTier(IBlockAccess world, int x, int y, int z) {
        SimpleKey simpleKey = new SimpleKey(x, y, z);
        if (world instanceof World) {
            ECOControllerTier tier = FORMED_MEMBER_TIERS.get(new Key((World) world, x, y, z));
            return tier != null ? tier : CLIENT_FORMED_MEMBER_TIERS.get(simpleKey);
        }
        return CLIENT_FORMED_MEMBER_TIERS.get(simpleKey);
    }

    private static void add(World world, List<ECOFormationBlockPos> positions) {
        for (ECOFormationBlockPos pos : positions) {
            HIDDEN_BLOCKS.add(new Key(world, pos.getX(), pos.getY(), pos.getZ()));
            if (world.isRemote) {
                CLIENT_HIDDEN_BLOCKS.add(new SimpleKey(pos.getX(), pos.getY(), pos.getZ()));
            }
            mark(world, pos);
        }
    }

    public static void replaceFormedMembers(World world, List<ECOFormationBlockPos> oldPositions,
        List<ECOFormationBlockPos> newPositions, boolean mirrored) {
        if (world == null) {
            return;
        }
        removeFormedMembers(world, oldPositions);
        addFormedMembers(world, newPositions, mirrored);
    }

    private static void addFormedMembers(World world, List<ECOFormationBlockPos> positions, boolean mirrored) {
        for (ECOFormationBlockPos pos : positions) {
            FORMED_MEMBER_BLOCKS.add(new Key(world, pos.getX(), pos.getY(), pos.getZ()));
            if (pos.getTier() != null) {
                FORMED_MEMBER_TIERS.put(new Key(world, pos.getX(), pos.getY(), pos.getZ()), pos.getTier());
            }
            if (world.isRemote) {
                CLIENT_FORMED_MEMBER_BLOCKS.add(new SimpleKey(pos.getX(), pos.getY(), pos.getZ()));
                if (pos.getTier() != null) {
                    CLIENT_FORMED_MEMBER_TIERS.put(new SimpleKey(pos.getX(), pos.getY(), pos.getZ()), pos.getTier());
                }
            }
            if (mirrored) {
                MIRRORED_FORMED_MEMBER_BLOCKS.add(new Key(world, pos.getX(), pos.getY(), pos.getZ()));
                if (world.isRemote) {
                    CLIENT_MIRRORED_FORMED_MEMBER_BLOCKS.add(new SimpleKey(pos.getX(), pos.getY(), pos.getZ()));
                }
            }
            mark(world, pos);
        }
    }

    private static void removeFormedMembers(World world, List<ECOFormationBlockPos> positions) {
        for (ECOFormationBlockPos pos : positions) {
            FORMED_MEMBER_BLOCKS.remove(new Key(world, pos.getX(), pos.getY(), pos.getZ()));
            FORMED_MEMBER_TIERS.remove(new Key(world, pos.getX(), pos.getY(), pos.getZ()));
            if (world.isRemote) {
                CLIENT_FORMED_MEMBER_BLOCKS.remove(new SimpleKey(pos.getX(), pos.getY(), pos.getZ()));
                CLIENT_FORMED_MEMBER_TIERS.remove(new SimpleKey(pos.getX(), pos.getY(), pos.getZ()));
            }
            MIRRORED_FORMED_MEMBER_BLOCKS.remove(new Key(world, pos.getX(), pos.getY(), pos.getZ()));
            if (world.isRemote) {
                CLIENT_MIRRORED_FORMED_MEMBER_BLOCKS.remove(new SimpleKey(pos.getX(), pos.getY(), pos.getZ()));
            }
            mark(world, pos);
        }
    }

    private static void remove(World world, List<ECOFormationBlockPos> positions) {
        for (ECOFormationBlockPos pos : positions) {
            HIDDEN_BLOCKS.remove(new Key(world, pos.getX(), pos.getY(), pos.getZ()));
            if (world.isRemote) {
                CLIENT_HIDDEN_BLOCKS.remove(new SimpleKey(pos.getX(), pos.getY(), pos.getZ()));
            }
            mark(world, pos);
        }
    }

    private static final class SimpleKey {

        private final int x;
        private final int y;
        private final int z;

        private SimpleKey(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SimpleKey)) {
                return false;
            }
            SimpleKey other = (SimpleKey) obj;
            return this.x == other.x && this.y == other.y && this.z == other.z;
        }

        @Override
        public int hashCode() {
            int result = this.x;
            result = 31 * result + this.y;
            result = 31 * result + this.z;
            return result;
        }
    }

    private static void mark(World world, ECOFormationBlockPos pos) {
        world.markBlockRangeForRenderUpdate(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
    }

    private static final class Key {

        private final int dimension;
        private final int x;
        private final int y;
        private final int z;

        private Key(World world, int x, int y, int z) {
            this.dimension = world.provider.dimensionId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Key)) {
                return false;
            }
            Key other = (Key) obj;
            return this.dimension == other.dimension && this.x == other.x && this.y == other.y && this.z == other.z;
        }

        @Override
        public int hashCode() {
            int result = this.dimension;
            result = 31 * result + this.x;
            result = 31 * result + this.y;
            result = 31 * result + this.z;
            return result;
        }
    }
}
