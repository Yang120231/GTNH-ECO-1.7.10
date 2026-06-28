package cn.dancingsnow.neoecoae.multiblock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.LongHashMap;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.tile.ECOControllerTier;

public final class ECOFormationVisibility {

    private static final Object PRESENT = new Object();
    private static final Map<Integer, LongHashMap> HIDDEN_BLOCKS = new HashMap<>();
    private static LongHashMap clientHiddenBlocks = new LongHashMap();
    private static final Map<Integer, LongHashMap> FORMED_MEMBER_BLOCKS = new HashMap<>();
    private static LongHashMap clientFormedMemberBlocks = new LongHashMap();
    private static final Map<Integer, LongHashMap> MIRRORED_FORMED_MEMBER_BLOCKS = new HashMap<>();
    private static LongHashMap clientMirroredFormedMemberBlocks = new LongHashMap();
    private static final Map<Integer, LongHashMap> FORMED_MEMBER_TIERS = new HashMap<>();
    private static LongHashMap clientFormedMemberTiers = new LongHashMap();

    private ECOFormationVisibility() {}

    public static void replace(World world, List<ECOFormationBlockPos> oldPositions,
        List<ECOFormationBlockPos> newPositions) {
        if (world == null) {
            return;
        }
        remove(world, oldPositions);
        add(world, newPositions);
    }

    public static void clearClient() {
        clientHiddenBlocks = new LongHashMap();
        clientFormedMemberBlocks = new LongHashMap();
        clientMirroredFormedMemberBlocks = new LongHashMap();
        clientFormedMemberTiers = new LongHashMap();
    }

    public static void clearDimension(World world) {
        if (world == null) {
            return;
        }
        Integer dimension = dimensionId(world);
        HIDDEN_BLOCKS.remove(dimension);
        FORMED_MEMBER_BLOCKS.remove(dimension);
        MIRRORED_FORMED_MEMBER_BLOCKS.remove(dimension);
        FORMED_MEMBER_TIERS.remove(dimension);
        if (world.isRemote) {
            clearClient();
        }
    }

    public static boolean isHidden(IBlockAccess world, int x, int y, int z) {
        if (world instanceof World) {
            return contains(HIDDEN_BLOCKS, (World) world, x, y, z) || clientHiddenBlocks.containsItem(posKey(x, y, z));
        }
        return clientHiddenBlocks.containsItem(posKey(x, y, z));
    }

    public static boolean shouldRenderFormedMember(IBlockAccess world, int x, int y, int z) {
        if (world instanceof World) {
            return contains(FORMED_MEMBER_BLOCKS, (World) world, x, y, z)
                || clientFormedMemberBlocks.containsItem(posKey(x, y, z));
        }
        return clientFormedMemberBlocks.containsItem(posKey(x, y, z));
    }

    public static boolean isMirroredFormedMember(IBlockAccess world, int x, int y, int z) {
        if (world instanceof World) {
            return contains(MIRRORED_FORMED_MEMBER_BLOCKS, (World) world, x, y, z)
                || clientMirroredFormedMemberBlocks.containsItem(posKey(x, y, z));
        }
        return clientMirroredFormedMemberBlocks.containsItem(posKey(x, y, z));
    }

    public static ECOControllerTier getFormedMemberTier(IBlockAccess world, int x, int y, int z) {
        long key = posKey(x, y, z);
        if (world instanceof World) {
            ECOControllerTier tier = getTier((World) world, x, y, z);
            return tier != null ? tier : (ECOControllerTier) clientFormedMemberTiers.getValueByKey(key);
        }
        return (ECOControllerTier) clientFormedMemberTiers.getValueByKey(key);
    }

    private static void add(World world, List<ECOFormationBlockPos> positions) {
        for (ECOFormationBlockPos pos : positions) {
            mapFor(HIDDEN_BLOCKS, world).add(posKey(pos), PRESENT);
            if (world.isRemote) {
                clientHiddenBlocks.add(posKey(pos), PRESENT);
            }
        }
        mark(world, positions);
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
            long key = posKey(pos);
            mapFor(FORMED_MEMBER_BLOCKS, world).add(key, PRESENT);
            if (pos.getTier() != null) {
                mapFor(FORMED_MEMBER_TIERS, world).add(key, pos.getTier());
            }
            if (world.isRemote) {
                clientFormedMemberBlocks.add(key, PRESENT);
                if (pos.getTier() != null) {
                    clientFormedMemberTiers.add(key, pos.getTier());
                }
            }
            if (mirrored) {
                mapFor(MIRRORED_FORMED_MEMBER_BLOCKS, world).add(key, PRESENT);
                if (world.isRemote) {
                    clientMirroredFormedMemberBlocks.add(key, PRESENT);
                }
            }
        }
        mark(world, positions);
    }

    private static void removeFormedMembers(World world, List<ECOFormationBlockPos> positions) {
        for (ECOFormationBlockPos pos : positions) {
            long key = posKey(pos);
            remove(FORMED_MEMBER_BLOCKS, world, key);
            remove(FORMED_MEMBER_TIERS, world, key);
            if (world.isRemote) {
                clientFormedMemberBlocks.remove(key);
                clientFormedMemberTiers.remove(key);
            }
            remove(MIRRORED_FORMED_MEMBER_BLOCKS, world, key);
            if (world.isRemote) {
                clientMirroredFormedMemberBlocks.remove(key);
            }
        }
        mark(world, positions);
    }

    private static void remove(World world, List<ECOFormationBlockPos> positions) {
        for (ECOFormationBlockPos pos : positions) {
            remove(HIDDEN_BLOCKS, world, posKey(pos));
            if (world.isRemote) {
                clientHiddenBlocks.remove(posKey(pos));
            }
        }
        mark(world, positions);
    }

    private static void mark(World world, List<ECOFormationBlockPos> positions) {
        if (positions.isEmpty()) {
            return;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (ECOFormationBlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        world.markBlockRangeForRenderUpdate(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean contains(Map<Integer, LongHashMap> byDimension, World world, int x, int y, int z) {
        LongHashMap positions = byDimension.get(dimensionId(world));
        return positions != null && positions.containsItem(posKey(x, y, z));
    }

    private static ECOControllerTier getTier(World world, int x, int y, int z) {
        LongHashMap tiers = FORMED_MEMBER_TIERS.get(dimensionId(world));
        return tiers == null ? null : (ECOControllerTier) tiers.getValueByKey(posKey(x, y, z));
    }

    private static LongHashMap mapFor(Map<Integer, LongHashMap> byDimension, World world) {
        Integer dimension = dimensionId(world);
        LongHashMap positions = byDimension.get(dimension);
        if (positions == null) {
            positions = new LongHashMap();
            byDimension.put(dimension, positions);
        }
        return positions;
    }

    private static void remove(Map<Integer, LongHashMap> byDimension, World world, long key) {
        Integer dimension = dimensionId(world);
        LongHashMap positions = byDimension.get(dimension);
        if (positions == null) {
            return;
        }
        positions.remove(key);
        if (positions.getNumHashElements() == 0) {
            byDimension.remove(dimension);
        }
    }

    private static int dimensionId(World world) {
        return world.provider.dimensionId;
    }

    private static long posKey(ECOFormationBlockPos pos) {
        return posKey(pos.getX(), pos.getY(), pos.getZ());
    }

    private static long posKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }
}
