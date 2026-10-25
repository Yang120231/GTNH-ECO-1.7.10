package cn.dancingsnow.neoecoae.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Stream;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.jdk.CRC32C;
import cn.dancingsnow.neoecoae.platform.jdk.JdkCompat;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.ListTag;
import cn.dancingsnow.neoecoae.platform.nbt.NbtUtils;
import cn.dancingsnow.neoecoae.platform.nbt.Tag;
import cn.dancingsnow.neoecoae.platform.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;

class PlatformContractTest {

    @Test
    void collectionFactoriesPreserveNullDuplicateAndImmutabilityContracts() {
        assertThrows(NullPointerException.class, () -> JdkCompat.listOf("a", null));
        assertThrows(NullPointerException.class, () -> JdkCompat.mapOf("a", null));
        assertThrows(IllegalArgumentException.class, () -> JdkCompat.setOf("a", "a"));
        assertThrows(IllegalArgumentException.class, () -> JdkCompat.mapOf("a", 1, "a", 2));
        assertEquals(
            1,
            JdkCompat.setCopyOf(Arrays.asList("a", "a"))
                .size());
        assertThrows(
            UnsupportedOperationException.class,
            () -> JdkCompat.listOf("a")
                .add("b"));
        List<String> stream = Stream.of("a", (String) null)
            .collect(JdkCompat.toList());
        assertNull(stream.get(1));
        assertThrows(UnsupportedOperationException.class, () -> stream.add("c"));
    }

    @Test
    void reversedListIsALiveViewAndEmptyFirstThrowsTheCorrectException() {
        List<Integer> source = new ArrayList<>(Arrays.asList(1, 2, 3));
        List<Integer> reverse = JdkCompat.reversed(source);
        reverse.set(0, 4);
        reverse.add(0, 5);
        assertEquals(Arrays.asList(1, 2, 4, 5), source);
        assertEquals(Arrays.asList(5, 4, 2, 1), reverse);
        assertThrows(NoSuchElementException.class, () -> JdkCompat.first(Collections.emptyList()));
        assertTrue(JdkCompat.isBlank("\u2003\t"));
        assertFalse(JdkCompat.isBlank("\u00a0"));
    }

    @Test
    void castagnoliChecksumMatchesThePublishedCheckVector() {
        CRC32C crc = new CRC32C();
        crc.update("123456789".getBytes(StandardCharsets.US_ASCII));
        assertEquals(0xe3069283L, crc.getValue());
        crc.reset();
        assertEquals(0, crc.getValue());
    }

    @Test
    void nativeItemIdentityDefensivelyCopiesBothIngressAndEgressNbt() {
        ItemStack source = new ItemStack(new Item(), 64, 3);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("identity", "before");
        source.setTagCompound(tag);
        AEItemKey key = AEItemKey.of(source);
        int originalHash = key.hashCode();
        source.setItemDamage(7);
        tag.setString("identity", "after");
        ItemStack exposed = key.getReadOnlyStack();
        exposed.getTagCompound()
            .setString("identity", "also changed");
        assertEquals(
            3,
            key.toStack()
                .getItemDamage());
        assertEquals(
            "before",
            key.toStack()
                .getTagCompound()
                .getString("identity"));
        assertEquals(originalHash, key.hashCode());
        assertEquals(key, AEItemKey.of(key.toStack()));
    }

    @Test
    void uuidAndNativeNbtListValuesRoundTripWithoutMutation() {
        UUID id = new UUID(Long.MIN_VALUE, Long.MAX_VALUE);
        CompoundTag compound = new CompoundTag();
        compound.putUUID("id", id);
        ListTag list = new ListTag();
        list.add(NbtUtils.createUUID(id));
        compound.put("ids", list);
        CompoundTag copy = compound.copy();
        assertEquals(id, copy.getUUID("id"));
        assertEquals(
            id,
            NbtUtils.loadUUID(
                copy.getList("ids", Tag.TAG_INT_ARRAY)
                    .get(0)));
        copy.remove("id");
        assertTrue(compound.hasUUID("id"));
    }

    @Test
    void packetPrimitivesRejectOversizedLengthsAndRoundTripSignedValues() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            buffer.writeVarInt(-1)
                .writeVarLong(Long.MIN_VALUE)
                .writeUtf("移植")
                .writeEnum(Thread.State.RUNNABLE);
            assertEquals(-1, buffer.readVarInt());
            assertEquals(Long.MIN_VALUE, buffer.readVarLong());
            assertEquals("移植", buffer.readUtf());
            assertEquals(Thread.State.RUNNABLE, buffer.readEnum(Thread.State.class));
            buffer.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () -> buffer.readByteArray(1024));
        } finally {
            buffer.release();
        }
    }

    @Test
    void importedGraphSnapshotCompressionRoundTripsThroughTheTargetNettyBackend() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            CraftingGraphSnapshot.EMPTY.writeToPacket(buffer);
            assertEquals(CraftingGraphSnapshot.EMPTY, new CraftingGraphSnapshot(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void arbitraryPrecisionResourceKeysRoundTripThroughTheBoundedPacketCodec() {
        var key = new TestResourceKey("stored", false);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            AEKey.writeKey(buffer, key);
            assertEquals(key, AEKey.readKey(buffer));
        } finally {
            buffer.release();
        }
    }
}
