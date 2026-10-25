package cn.dancingsnow.neoecoae.platform.minecraft;

public final class BlockStateProperties {

    public static final Property<Direction> HORIZONTAL_FACING = new Property<>(
        "facing",
        Direction.class,
        direction -> direction.getAxis()
            .isHorizontal());
    public static final Property<Direction> FACING = new Property<>("facing", Direction.class, direction -> true);
    public static final Property<Boolean> LIT = Property.bool("lit");

    private BlockStateProperties() {}
}
