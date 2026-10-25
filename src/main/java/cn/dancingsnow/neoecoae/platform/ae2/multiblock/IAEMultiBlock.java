package cn.dancingsnow.neoecoae.platform.ae2.multiblock;

public interface IAEMultiBlock<C extends IAECluster> extends appeng.me.cluster.IAEMultiBlock, FormationState {

    @Override
    C getCluster();

    void updateMultiBlock(cn.dancingsnow.neoecoae.platform.minecraft.BlockPos changed);

    void rebuildMultiblock();

    void breakCluster();
}
