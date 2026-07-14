package cn.dancingsnow.neoecoae.client.gui;

/** Pixel geometry shared with the 1.21.1 computation-host panel. */
final class ComputationControllerLayout {

    static final int HEADER_Y = 8;

    static final int MAIN_X = 6;
    static final int MAIN_Y = 24;
    static final int MAIN_W = 162;
    static final int MAIN_H = 108;
    static final int STAT_X = MAIN_X + 6;
    static final int STAT_W = MAIN_W - 12;
    static final int CAPACITY_TITLE_Y = MAIN_Y + 6;
    static final int STORAGE_LABEL_Y = MAIN_Y + 18;
    static final int STORAGE_DETAIL_Y = MAIN_Y + 28;
    static final int THREAD_LABEL_Y = MAIN_Y + 38;
    static final int THREAD_DETAIL_Y = MAIN_Y + 48;
    static final int PARALLEL_COUNT_LABEL_Y = MAIN_Y + 61;
    static final int PARALLEL_COUNT_VALUE_Y = MAIN_Y + 71;
    static final int FREE_MEMORY_LABEL_Y = MAIN_Y + 81;
    static final int FREE_MEMORY_VALUE_Y = MAIN_Y + 91;
    static final int STAT_BAR_X = STAT_X;
    static final int STAT_BAR_W = 70;
    static final int STAT_BAR_H = 4;
    static final int STAT_VALUE_X = STAT_BAR_X + STAT_BAR_W + 4;
    static final int STORAGE_BAR_Y = STORAGE_DETAIL_Y + 2;
    static final int THREAD_BAR_Y = THREAD_DETAIL_Y + 2;

    static final int INVENTORY_LABEL_Y = 136;

    static final int TASK_X = 180;
    static final int TASK_Y = 24;
    static final int TASK_W = 156;
    static final int TASK_H = 200;
    static final int TASK_CARD_X = TASK_X + 12;
    static final int TASK_CARD_Y = TASK_Y + 19;
    static final int TASK_CARD_W = 132;
    static final int TASK_CARD_H = 28;
    static final int TASK_CARD_STEP = 30;
    static final int TASK_LIST_BOTTOM_Y = TASK_Y + TASK_H - 3;
    static final int TASK_SCROLLBAR_X = TASK_X + TASK_W - 5;
    static final int TASK_SCROLLBAR_W = 3;
    static final int TASK_SCROLL_STEP = 1;

    static final int TOOLBAR_SIZE = 18;
    static final int TOOLBAR_X = 344 - 6 - TOOLBAR_SIZE;
    static final int TOOLBAR_Y = 2;
    static final int HEADER_STATUS_RIGHT = TOOLBAR_X - 6;

    private ComputationControllerLayout() {}
}
