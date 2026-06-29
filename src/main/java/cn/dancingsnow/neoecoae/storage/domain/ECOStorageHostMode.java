package cn.dancingsnow.neoecoae.storage.domain;

public enum ECOStorageHostMode {

    UNFORMED("unformed"),
    FORMED_NORMAL("formed_normal"),
    MIGRATING_TO_INFINITE("migrating_to_infinite"),
    FORMED_INFINITE("formed_infinite");

    private final String id;

    ECOStorageHostMode(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public static ECOStorageHostMode fromId(String id) {
        for (ECOStorageHostMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return UNFORMED;
    }
}
