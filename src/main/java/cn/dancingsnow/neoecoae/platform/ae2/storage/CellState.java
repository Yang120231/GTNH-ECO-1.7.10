package cn.dancingsnow.neoecoae.platform.ae2.storage;

public enum CellState {

    ABSENT(0),
    EMPTY(0x00ff00),
    NOT_EMPTY(0x00aaff),
    TYPES_FULL(0xffaa00),
    FULL(0xff0000);

    private final int stateColor;

    CellState(int stateColor) {
        this.stateColor = stateColor;
    }

    public int getStateColor() {
        return stateColor;
    }
}
