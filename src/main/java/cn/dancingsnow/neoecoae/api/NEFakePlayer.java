package cn.dancingsnow.neoecoae.api;

import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import com.google.common.collect.MapMaker;
import com.mojang.authlib.GameProfile;

public final class NEFakePlayer {

    private static final UUID DEFAULT_UUID = UUID.fromString("90892e60-608b-4b30-9b4a-73ebbb2594e8");
    private static final GameProfile PROFILE = new GameProfile(DEFAULT_UUID, "[ECO]");
    private static final ConcurrentMap<WorldServer, FakePlayer> PLAYERS = new MapMaker().weakKeys()
        .weakValues()
        .makeMap();

    private NEFakePlayer() {}

    public static EntityPlayer getFakePlayer(WorldServer level) {
        return PLAYERS.computeIfAbsent(level, world -> new FakePlayer(world, PROFILE));
    }
}
