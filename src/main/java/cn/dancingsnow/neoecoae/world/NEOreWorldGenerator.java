package cn.dancingsnow.neoecoae.world;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.feature.WorldGenMinable;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cpw.mods.fml.common.IWorldGenerator;

public final class NEOreWorldGenerator implements IWorldGenerator {

    public static final NEOreWorldGenerator INSTANCE = new NEOreWorldGenerator();

    private NEOreWorldGenerator() {}

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator,
        IChunkProvider chunkProvider) {
        if (world.provider == null || world.provider.dimensionId != 1) {
            return;
        }

        generateEndOre(world, random, chunkX, chunkZ, NEBlocks.aluminumOre, 9, 60, 60, 256);
        generateEndOre(world, random, chunkX, chunkZ, NEBlocks.aluminumOre, 6, 10, 10, 70);
        generateEndOre(world, random, chunkX, chunkZ, NEBlocks.tungstenOre, 9, 60, 60, 256);
        generateEndOre(world, random, chunkX, chunkZ, NEBlocks.tungstenOre, 6, 10, 10, 70);
    }

    private void generateEndOre(World world, Random random, int chunkX, int chunkZ, Block ore, int veinSize,
        int attempts, int minY, int maxY) {
        int height = Math.max(1, maxY - minY);
        WorldGenMinable generator = new WorldGenMinable(ore, veinSize, Blocks.end_stone);
        for (int i = 0; i < attempts; i++) {
            int x = chunkX * 16 + random.nextInt(16);
            int y = minY + random.nextInt(height);
            int z = chunkZ * 16 + random.nextInt(16);
            generator.generate(world, random, x, y, z);
        }
    }
}
