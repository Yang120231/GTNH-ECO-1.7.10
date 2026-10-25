package cn.dancingsnow.neoecoae.platform.minecraft;

/** State codec owned by the Forge block adapter; business code only sees BlockState. */
public interface NativeStateBlock {

    BlockState defaultBlockState();

    BlockState fromMetadata(int metadata);

    int metadata(BlockState state);
}
