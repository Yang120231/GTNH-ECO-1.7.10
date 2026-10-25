package cn.dancingsnow.neoecoae.platform.ae2.storage;

public enum CellState {

    ABSENT,
    EMPTY,
    NOT_EMPTY,
    TYPES_FULL,
    FULL;

    public int getStateColor() {
        return this == FULL ? 0xff0000 : this == TYPES_FULL ? 0xffaa00 : 0x00ff00;
    }
}
