package cn.dancingsnow.neoecoae.multiblock;

import cn.dancingsnow.neoecoae.tile.ECOControllerSubsystem;

/** Stable item-NBT names for the three ECO multiblock host families. */
public enum StructureTerminalHostType {

    CRAFTING,
    STORAGE,
    COMPUTATION;

    public static StructureTerminalHostType fromSubsystem(ECOControllerSubsystem subsystem) {
        if (subsystem == null) {
            return CRAFTING;
        }
        switch (subsystem) {
            case CRAFTING:
                return CRAFTING;
            case COMPUTATION:
                return COMPUTATION;
            case STORAGE:
            default:
                return STORAGE;
        }
    }

    public static StructureTerminalHostType fromName(String name) {
        if (name == null) {
            return CRAFTING;
        }
        for (StructureTerminalHostType type : values()) {
            if (type.name()
                .equals(name)) {
                return type;
            }
        }
        return CRAFTING;
    }
}
