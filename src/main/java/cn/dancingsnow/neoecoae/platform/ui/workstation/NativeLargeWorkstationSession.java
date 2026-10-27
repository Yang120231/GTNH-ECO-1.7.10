package cn.dancingsnow.neoecoae.platform.ui.workstation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.ListTag;
import cn.dancingsnow.neoecoae.platform.nbt.Tag;
import cn.dancingsnow.neoecoae.platform.ui.NativeSnapshotSession;

/** The page consumes the large host's bounded snapshots; task returns retain their UUID identity. */
public final class NativeLargeWorkstationSession extends NativeSnapshotSession {

    static final int PAGE = 0, OVERCLOCK = 1, COOLING = 2, PREHEAT = 3, RETURN = 4;
    private final NativeLargeWorkstationPages.Config config;
    private final Map<UUID, Integer> tokens = new HashMap<>();
    private int offset, nextToken = 1;
    final NativeLargeWorkstationScroll scroll = new NativeLargeWorkstationScroll();
    int requestedOffset;

    public NativeLargeWorkstationSession(NativeLargeWorkstationPages.Config config) {
        super(config.tile());
        this.config = config;
    }

    @Override
    protected CompoundTag capture() {
        CompoundTag snapshot = config.snapshot().apply(offset).copy();
        ListTag rows = snapshot.getList("tasks", Tag.TAG_COMPOUND);
        ListTag bounded = new ListTag();
        Set<UUID> visible = new HashSet<>();
        for (int i = 0; i < Math.min(NativeLargeWorkstationPages.SNAPSHOT_SIZE, rows.size()); i++) {
            CompoundTag row = rows.getCompound(i).copy();
            if (!row.hasUUID("id")) continue;
            UUID id = row.getUUID("id");
            visible.add(id);
            Integer token = tokens.get(id);
            if (token == null) {
                if (nextToken == Integer.MAX_VALUE) continue;
                token = nextToken++;
                tokens.put(id, token);
            }
            row.putInt("token", token);
            bounded.add(row);
        }
        tokens.keySet().retainAll(visible);
        snapshot.put("tasks", bounded);
        return snapshot;
    }

    @Override
    protected void apply(int action, int argument) {
        switch (action) {
            case PAGE:
                offset = Math.max(0, Math.min(509, argument));
                break;
            case OVERCLOCK:
                config.toggleOverclocked().run();
                break;
            case COOLING:
                config.toggleActiveCooling().run();
                break;
            case PREHEAT:
                config.togglePreheating().run();
                break;
            case RETURN:
                // Re-read the current queue before accepting a click from an older client snapshot.
                ListTag rows = config.snapshot().apply(offset).getList("tasks", Tag.TAG_COMPOUND);
                for (int i = 0; i < Math.min(NativeLargeWorkstationPages.SNAPSHOT_SIZE, rows.size()); i++) {
                    CompoundTag row = rows.getCompound(i);
                    if (!row.hasUUID("id") || !row.getBoolean("returnable")) continue;
                    UUID id = row.getUUID("id");
                    if (Integer.valueOf(argument).equals(tokens.get(id))) {
                        config.returnTask().accept(id);
                        break;
                    }
                }
                break;
            default:
                break;
        }
    }
}
