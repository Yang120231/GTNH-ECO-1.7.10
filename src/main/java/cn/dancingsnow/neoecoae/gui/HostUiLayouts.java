package cn.dancingsnow.neoecoae.gui;

public final class HostUiLayouts {

    /**
     * Pixel contracts shared with the 1.21.1 host panels and their 1.20.1
     * LDLib2-style backport. Keep container slots and client rendering on the
     * same coordinates: changing only the screen makes ghost-slot hit boxes.
     */
    public static final Layout STORAGE = new Layout(344, 232, 13, 147, 205);
    public static final Layout COMPUTATION = new Layout(344, 232, 6, 147, 205);
    public static final Layout CRAFTING = new Layout(304, 196, 6, 113, 169);

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
