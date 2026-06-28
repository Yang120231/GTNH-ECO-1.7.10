package cn.dancingsnow.neoecoae.multiblock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ECOFormationResult {

    private final boolean formed;
    private final boolean mirrored;
    private final String message;
    private final List<ECOFormationBlockPos> hiddenBlocks;
    private final List<ECOFormationBlockPos> formedMemberBlocks;

    private ECOFormationResult(boolean formed, boolean mirrored, String message,
        List<ECOFormationBlockPos> hiddenBlocks, List<ECOFormationBlockPos> formedMemberBlocks) {
        this.formed = formed;
        this.mirrored = mirrored;
        this.message = message;
        this.hiddenBlocks = new ArrayList<ECOFormationBlockPos>(hiddenBlocks);
        this.formedMemberBlocks = new ArrayList<ECOFormationBlockPos>(formedMemberBlocks);
    }

    public static ECOFormationResult formed(boolean mirrored, List<ECOFormationBlockPos> hiddenBlocks) {
        return formed(mirrored, hiddenBlocks, Collections.<ECOFormationBlockPos>emptyList());
    }

    public static ECOFormationResult formed(boolean mirrored, List<ECOFormationBlockPos> hiddenBlocks,
        List<ECOFormationBlockPos> formedMemberBlocks) {
        return new ECOFormationResult(true, mirrored, "formed", hiddenBlocks, formedMemberBlocks);
    }

    public static ECOFormationResult failed(String message) {
        return new ECOFormationResult(
            false,
            false,
            message,
            Collections.<ECOFormationBlockPos>emptyList(),
            Collections.<ECOFormationBlockPos>emptyList());
    }

    public boolean isFormed() {
        return this.formed;
    }

    public boolean isMirrored() {
        return this.mirrored;
    }

    public String getMessage() {
        return this.message;
    }

    public List<ECOFormationBlockPos> getHiddenBlocks() {
        return Collections.unmodifiableList(this.hiddenBlocks);
    }

    public List<ECOFormationBlockPos> getFormedMemberBlocks() {
        return Collections.unmodifiableList(this.formedMemberBlocks);
    }
}
