package cn.dancingsnow.neoecoae.tile;

public enum ECOControllerTier {

    L4("l4", 4),
    L6("l6", 6),
    L9("l9", 9);

    private final String id;
    private final int level;

    ECOControllerTier(String id, int level) {
        this.id = id;
        this.level = level;
    }

    public String getId() {
        return this.id;
    }

    public int getLevel() {
        return this.level;
    }

    public boolean supports(ECOControllerTier componentTier) {
        return this.level >= componentTier.level;
    }

    public static ECOControllerTier fromId(String id) {
        for (ECOControllerTier tier : values()) {
            if (tier.id.equals(id)) {
                return tier;
            }
        }
        return L4;
    }
}
