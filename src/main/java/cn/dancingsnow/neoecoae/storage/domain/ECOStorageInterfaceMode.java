package cn.dancingsnow.neoecoae.storage.domain;

/**
 * Direction of the storage interface bridge.
 *
 * <p>
 * The legacy AE2 API has no unified key type, so the transfer implementation adapts this
 * mode to both item and fluid storage channels.
 * </p>
 */
public enum ECOStorageInterfaceMode {

    STORAGE,
    INPUT,
    OUTPUT;

    public ECOStorageInterfaceMode next() {
        switch (this) {
            case STORAGE:
                return INPUT;
            case INPUT:
                return OUTPUT;
            case OUTPUT:
            default:
                return STORAGE;
        }
    }

    public boolean isTransfer() {
        return this == INPUT || this == OUTPUT;
    }

    public static ECOStorageInterfaceMode byName(String name) {
        for (ECOStorageInterfaceMode mode : values()) {
            if (mode.name()
                .equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return STORAGE;
    }

    public static ECOStorageInterfaceMode byOrdinal(int ordinal) {
        ECOStorageInterfaceMode[] modes = values();
        return ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : STORAGE;
    }
}
