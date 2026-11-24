package cn.dancingsnow.neoecoae.platform.mixin;

import java.io.IOException;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;

@Mixin(value = AnvilChunkLoader.class, remap = false)
public abstract class DriveChunkSaveMixin {

    @Inject(method = { "saveChunk", "func_75816_a" }, at = @At("HEAD"))
    private void neoecoae$beforeSavingDrives(World world, Chunk chunk, CallbackInfo callback) throws IOException {
        if (!ECODriveBlockEntity.flushChunkCells(chunk))
            throw new IOException("ECO drive persistence pending in chunk " + chunk.xPosition + ", " + chunk.zPosition);
    }
}
