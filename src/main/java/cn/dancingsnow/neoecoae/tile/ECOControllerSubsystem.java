package cn.dancingsnow.neoecoae.tile;

public enum ECOControllerSubsystem {

    STORAGE("storage"),
    CRAFTING("crafting"),
    COMPUTATION("computation");

    private final String id;

    ECOControllerSubsystem(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public static ECOControllerSubsystem fromId(String id) {
        for (ECOControllerSubsystem subsystem : values()) {
            if (subsystem.id.equals(id)) {
                return subsystem;
            }
        }
        return STORAGE;
    }
}
