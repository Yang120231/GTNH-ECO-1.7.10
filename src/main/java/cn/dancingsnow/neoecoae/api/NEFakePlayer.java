package cn.dancingsnow.neoecoae.api;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;

import com.mojang.authlib.GameProfile;

public final class NEFakePlayer {

    private static final UUID DEFAULT_UUID = UUID.fromString("90892e60-608b-4b30-9b4a-73ebbb2594e8");
    private static final GameProfile PROFILE = new GameProfile(DEFAULT_UUID, "[ECO]");

    private NEFakePlayer() {}

    public static EntityPlayer getFakePlayer(WorldServer level) {
        return FakePlayerFactory.get(level, PROFILE);
    }
}
