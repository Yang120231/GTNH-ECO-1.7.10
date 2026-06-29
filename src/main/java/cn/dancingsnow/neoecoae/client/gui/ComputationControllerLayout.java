package cn.dancingsnow.neoecoae.client.gui;

final class ComputationControllerLayout {

    static final int EDGE = 7;
    static final int HEADER_Y = 8;
    static final int MAIN_X = EDGE;
    static final int MAIN_Y = 24;
    static final int MAIN_W = 164;
    static final int MAIN_H = 132;
    static final int TASK_GAP = 8;
    static final int TASK_X = MAIN_X + MAIN_W + TASK_GAP;
    static final int TASK_Y = MAIN_Y;
    static final int TASK_W = 344 - TASK_X - EDGE;
    static final int TASK_H = 229 + 18 - TASK_Y;
    static final int TOOLBAR_X = 344 - EDGE - 16;
    static final int TOOLBAR_Y = 4;
    static final int TOOLBAR_SIZE = 16;
    static final int STAT_X = MAIN_X + 8;
    static final int STAT_BAR_X = MAIN_X + 12;
    static final int STAT_BAR_W = MAIN_W - 24;
    static final int STAT_BAR_H = 8;
    static final int THREAD_BAR_Y = MAIN_Y + 21;
    static final int STORAGE_TEXT_Y = MAIN_Y + 78;
    static final int STORAGE_BAR_Y = MAIN_Y + 93;
    static final int PARALLEL_CORES_Y = MAIN_Y + 108;
    static final int TASK_CARD_X = TASK_X + 8;
    static final int TASK_CARD_Y = TASK_Y + 19;
    static final int TASK_CARD_W = TASK_W - 16;
    static final int TASK_CARD_H = 24;
    static final int TASK_CARD_STEP = 26;
    static final int TASK_SCROLL_STEP = 1;

    private ComputationControllerLayout() {}
}
