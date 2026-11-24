package cn.dancingsnow.neoecoae.platform.mixin;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.ECOCellMutationBatch;

@Mixin(value = ChunkProviderServer.class, remap = false)
public abstract class DriveChunkUnloadMixin {

    @Shadow(aliases = { "field_73248_b" })
    private Set<Long> chunksToUnload;

    @Unique
    private final Set<Long> neoecoae$deferredDriveUnloads = new HashSet<>();

    @Inject(method = { "unloadQueuedChunks", "func_73156_b" }, at = @At("HEAD"))
    private void neoecoae$deferUnwrittenDrives(CallbackInfoReturnable<Boolean> callback) {
        if (!ECOCellMutationBatch.hasPendingWrites()) return;
        ChunkProviderServer provider = (ChunkProviderServer) (Object) this;
        for (long position : chunksToUnload) {
            Chunk chunk = (Chunk) provider.loadedChunkHashMap.getValueByKey(position);
            if (chunk != null && !ECODriveBlockEntity.flushChunkCells(chunk)) {
                chunksToUnload.remove(position);
                neoecoae$deferredDriveUnloads.add(position);
            }
        }
    }

    @Inject(method = { "unloadQueuedChunks", "func_73156_b" }, at = @At("RETURN"))
    private void neoecoae$requeueDriveUnloads(CallbackInfoReturnable<Boolean> callback) {
        if (neoecoae$deferredDriveUnloads.isEmpty()) return;
        chunksToUnload.addAll(neoecoae$deferredDriveUnloads);
        neoecoae$deferredDriveUnloads.clear();
    }
}
