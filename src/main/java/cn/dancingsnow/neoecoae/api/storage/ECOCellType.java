package cn.dancingsnow.neoecoae.api.storage;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import cn.dancingsnow.neoecoae.platform.minecraft.Component;

/**
 * @param visible whether to show this type in the storage host's type information list
 */
@com.github.bsideup.jabel.Desugar
public record ECOCellType(Component desc, int typeCount, boolean visible) {

    public ECOCellType(Component desc, int typeCount) {
        this(desc, typeCount, true);
    }

    @Contract(" -> new")
    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static class Builder {

        Component desc = Component.translatable("neoecoae.unknow_cell_type");
        int typeCount = 1;
        boolean visible = true;

        public Builder desc(Component value) {
            desc = value;
            return this;
        }

        public Builder typeCount(int value) {
            typeCount = value;
            return this;
        }

        public Builder visible(boolean value) {
            visible = value;
            return this;
        }

        public ECOCellType build() {
            return new ECOCellType(desc, typeCount, visible);
        }
    }
}
