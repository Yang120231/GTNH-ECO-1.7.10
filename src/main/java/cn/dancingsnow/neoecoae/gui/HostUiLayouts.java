package cn.dancingsnow.neoecoae.gui;

public final class HostUiLayouts {

    public static final Layout STORAGE = new Layout(344, 256, 8, 171, 229);
    public static final Layout COMPUTATION = new Layout(344, 252, 8, 171, 229);
    public static final Layout CRAFTING = new Layout(304, 268, 12, 187, 243);

    private HostUiLayouts() {}

    public static final class Layout {

        private final int width;
        private final int height;
        private final int inventoryX;
        private final int inventoryY;
        private final int hotbarY;

        private Layout(int width, int height, int inventoryX, int inventoryY, int hotbarY) {
            this.width = width;
            this.height = height;
            this.inventoryX = inventoryX;
            this.inventoryY = inventoryY;
            this.hotbarY = hotbarY;
        }

        public int width() {
            return this.width;
        }

        public int height() {
            return this.height;
        }

        public int inventoryX() {
            return this.inventoryX;
        }

        public int inventoryY() {
            return this.inventoryY;
        }

        public int hotbarY() {
            return this.hotbarY;
        }
    }
}
