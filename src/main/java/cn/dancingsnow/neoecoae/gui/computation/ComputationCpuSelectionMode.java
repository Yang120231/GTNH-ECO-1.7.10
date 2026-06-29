package cn.dancingsnow.neoecoae.gui.computation;

public enum ComputationCpuSelectionMode {

    ANY("any"),
    PLAYER_ONLY("player"),
    MACHINE_ONLY("machine");

    private final String id;

    ComputationCpuSelectionMode(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public ComputationCpuSelectionMode next() {
        ComputationCpuSelectionMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public static ComputationCpuSelectionMode fromOrdinal(int ordinal) {
        ComputationCpuSelectionMode[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return ANY;
        }
        return values[ordinal];
    }
}
