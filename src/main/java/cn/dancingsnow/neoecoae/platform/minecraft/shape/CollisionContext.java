package cn.dancingsnow.neoecoae.platform.minecraft.shape;

public final class CollisionContext {

    private final net.minecraft.entity.Entity entity;

    public CollisionContext(net.minecraft.entity.Entity entity) {
        this.entity = entity;
    }

    public net.minecraft.entity.Entity entity() {
        return entity;
    }
}
