package cn.dancingsnow.neoecoae.platform.ui.multiblock;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;

import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;

/** Native menu ownership and the shared four-row builder's length, mirror and construction actions. */
@com.github.bsideup.jabel.Desugar
public record NativeBuilderConfig(TileEntity tile, EntityPlayer player, IntSupplier selectedLength, int minLength,
    int maxLength, IntConsumer setLength, IntConsumer changeLength, BooleanSupplier mirrored,
    Consumer<Boolean> setMirrored, Runnable build, BooleanSupplier formed, BooleanSupplier buildInProgress,
    Supplier<MultiBlockPlacementPlan> previewPlan) {}
