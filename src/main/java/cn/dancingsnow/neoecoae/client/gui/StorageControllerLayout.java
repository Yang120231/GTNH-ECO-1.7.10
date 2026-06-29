package cn.dancingsnow.neoecoae.client.gui;

import cn.dancingsnow.neoecoae.gui.HostUiLayouts;
import cn.dancingsnow.neoecoae.gui.container.ContainerECOStorageController;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
final class StorageControllerLayout {

    static final int LEFT_X = 8;
    static final int LEFT_Y = 24;
    static final int LEFT_W = 162;
    static final int LEFT_H = 132;
    static final int TEXT_X = LEFT_X + 8;
    static final int TEXT_Y = LEFT_Y + 8;
    static final int TEXT_STEP = 13;

    static final int RIGHT_X = 174;
    static final int RIGHT_Y = 24;
    static final int RIGHT_W = 344 - RIGHT_X - 4;
    static final int RIGHT_H = 132;
    static final int STORAGE_GAUGE_W = 32;
    static final int STORAGE_GAUGE_H = 92;
    static final int STORAGE_GAUGE_X = RIGHT_X + 18;
    static final int STORAGE_GAUGE_Y = RIGHT_Y + 23;
    static final int USAGE_DETAIL_X = STORAGE_GAUGE_X + STORAGE_GAUGE_W + 10;
    static final int USAGE_DETAIL_Y = STORAGE_GAUGE_Y + 5;
    static final int USAGE_DETAIL_W = RIGHT_X + RIGHT_W - 10 - USAGE_DETAIL_X;
    static final int USAGE_DETAIL_LINE_H = 12;
    static final int RIGHT_DARK_X = RIGHT_X + 8;
    static final int RIGHT_DARK_Y = STORAGE_GAUGE_Y - 4;
    static final int RIGHT_DARK_W = RIGHT_W - 16;
    static final int RIGHT_DARK_H = STORAGE_GAUGE_H + 8;

    static final int MATRIX_X = RIGHT_X;
    static final int MATRIX_Y = 171;
    static final int MATRIX_W = 166;
    static final int MATRIX_H = HostUiLayouts.STORAGE.hotbarY() + 18 - MATRIX_Y;
    static final int MATRIX_GRID_ROWS = 3;
    static final int MATRIX_CELL_SIZE = 10;
    static final int MATRIX_LEGEND_X = MATRIX_X + 116;
    static final int MATRIX_GRID_AREA_X = MATRIX_X + 8;
    static final int MATRIX_GRID_AREA_W = MATRIX_LEGEND_X - MATRIX_GRID_AREA_X - 8;
    static final int MATRIX_GRID_LABEL_Y = MATRIX_Y + MATRIX_H - 17;
    static final int MATRIX_LEGEND_ROW_H = 8;
    static final int MATRIX_LEGEND_ROW_STEP = 11;
    static final int MATRIX_LEGEND_TOP = MATRIX_Y
        + (MATRIX_H - (MATRIX_LEGEND_ROW_STEP * 5 + MATRIX_LEGEND_ROW_H)) / 2;
    static final int MATRIX_LEGEND_W = MATRIX_X + MATRIX_W - MATRIX_LEGEND_X - 8;
    static final int MATRIX_EMPTY_BORDER = 0xFF1D1A24;

    static final int COMPONENT_SLOT_X = ContainerECOStorageController.INFINITE_COMPONENT_SLOT_FRAME_X;
    static final int COMPONENT_SLOT_Y = ContainerECOStorageController.INFINITE_COMPONENT_SLOT_FRAME_Y;
    static final int PRIORITY_TAB_X = 322;
    static final int PRIORITY_TAB_Y = 0;
    static final int TAB_SIZE = 22;

    private StorageControllerLayout() {}
}
