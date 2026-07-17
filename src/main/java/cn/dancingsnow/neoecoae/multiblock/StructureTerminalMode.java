package cn.dancingsnow.neoecoae.multiblock;

/** Operations exposed by the structure terminal. */
public enum StructureTerminalMode {

    BUILD,
    MIRRORED_BUILD,
    DISMANTLE;

    public StructureTerminalMode next() {
        StructureTerminalMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public static StructureTerminalMode fromName(String name) {
        if (name == null) {
            return BUILD;
        }
        for (StructureTerminalMode mode : values()) {
            if (mode.name()
                .equals(name)) {
                return mode;
            }
        }
        return BUILD;
    }
}
