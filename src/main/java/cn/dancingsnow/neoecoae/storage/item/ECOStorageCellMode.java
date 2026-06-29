package cn.dancingsnow.neoecoae.storage.item;

public enum ECOStorageCellMode {

    PORTABLE("portable"),
    MIGRATING("migrating"),
    DOMAIN_MEMBER("domain_member");

    private final String id;

    ECOStorageCellMode(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public static ECOStorageCellMode fromId(String id) {
        for (ECOStorageCellMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return PORTABLE;
    }
}
