package cn.dancingsnow.neoecoae.multiblock;

import net.minecraftforge.common.util.ForgeDirection;

final class FormationDirections {

    final ForgeDirection front;
    final ForgeDirection back;
    final ForgeDirection top;
    final ForgeDirection down;
    final ForgeDirection interfaceSide;
    final ForgeDirection expandSide;
    final boolean mirrored;

    FormationDirections(ForgeDirection front, ForgeDirection back, ForgeDirection top, ForgeDirection down,
        ForgeDirection interfaceSide, ForgeDirection expandSide, boolean mirrored) {
        this.front = front;
        this.back = back;
        this.top = top;
        this.down = down;
        this.interfaceSide = interfaceSide;
        this.expandSide = expandSide;
        this.mirrored = mirrored;
    }
}
