package cn.dancingsnow.neoecoae.platform.mixin;

import net.minecraft.block.Block;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.ECOCellMutationBatch;
import cn.dancingsnow.neoecoae.platform.registry.NativeDriveBlock;

@Mixin(value = Chunk.class, remap = false)
public abstract class DriveChunkMixin {

    @Inject(method = "func_150807_a", at = @At("HEAD"), cancellable = true)
    private void neoecoae$beforeReplacingDrive(int column, int height, int row, Block replacement, int metadata,
        CallbackInfoReturnable<Boolean> callback) {
        Chunk chunk = (Chunk) (Object) this;
        if (chunk.worldObj.isRemote || !ECOCellMutationBatch.hasPendingWrites()) return;
        Block installed = chunk.getBlock(column, height, row);
        if (installed != replacement && installed instanceof NativeDriveBlock
            && chunk.func_150806_e(column, height, row) instanceof ECODriveBlockEntity drive
            && !drive.flushPendingCell()) callback.setReturnValue(false);
    }
}
