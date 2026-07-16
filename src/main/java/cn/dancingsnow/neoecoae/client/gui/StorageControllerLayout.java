package cn.dancingsnow.neoecoae.client.gui;

import cn.dancingsnow.neoecoae.gui.HostUiLayouts;
import cn.dancingsnow.neoecoae.gui.container.ContainerECOStorageController;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
final class StorageControllerLayout {

    static final int LEFT_X = 6;
    static final int LEFT_Y = 24;
    static final int LEFT_W = 162;
    static final int LEFT_H = 108;
    static final int TEXT_X = LEFT_X + 8;
    static final int TEXT_Y = LEFT_Y + 8;
    static final int TEXT_STEP = 13;

    static final int RIGHT_X = 180;
    static final int RIGHT_Y = 24;
    static final int RIGHT_W = 157;
    static final int RIGHT_H = 200;
    static final int STORAGE_GAUGE_W = 32;
    static final int STORAGE_GAUGE_H = 143;
    static final int RIGHT_CONTENT_X = RIGHT_X + 6;
    static final int STORAGE_GAUGE_X = RIGHT_CONTENT_X + 10;
    static final int RIGHT_CONTENT_SHIFT_Y = 6;
    static final int STORAGE_GAUGE_Y = RIGHT_Y + 26 + RIGHT_CONTENT_SHIFT_Y;
    static final int USAGE_DETAIL_X = STORAGE_GAUGE_X + STORAGE_GAUGE_W + 8;
    static final int USAGE_DETAIL_Y = STORAGE_GAUGE_Y + 5;
    static final int USAGE_DETAIL_W = 88;
    static final int USAGE_DETAIL_LINE_H = 15;
    static final int RIGHT_DARK_X = RIGHT_CONTENT_X + 2;
    static final int RIGHT_DARK_Y = RIGHT_Y + 14 + RIGHT_CONTENT_SHIFT_Y;
    static final int RIGHT_DARK_W = 141;
    static final int RIGHT_DARK_H = 169;

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
    static final int MATRIX_LEGEND_TOP = MATRIX_Y + (MATRIX_H - (MATRIX_LEGEND_ROW_STEP * 5 + MATRIX_LEGEND_ROW_H)) / 2;
    static final int MATRIX_LEGEND_W = MATRIX_X + MATRIX_W - MATRIX_LEGEND_X - 8;
    static final int MATRIX_EMPTY_BORDER = 0xFF1D1A24;

    static final int COMPONENT_SLOT_X = ContainerECOStorageController.INFINITE_COMPONENT_SLOT_FRAME_X;
    static final int COMPONENT_SLOT_Y = ContainerECOStorageController.INFINITE_COMPONENT_SLOT_FRAME_Y;
    static final int PRIORITY_TAB_X = 322;
    static final int PRIORITY_TAB_Y = 0;
    static final int TAB_SIZE = 22;

    private StorageControllerLayout() {}
}
