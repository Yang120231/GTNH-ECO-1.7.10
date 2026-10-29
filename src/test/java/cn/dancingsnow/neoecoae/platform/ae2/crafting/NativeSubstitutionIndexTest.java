package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.World;

import org.junit.jupiter.api.Test;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

/** Recipe view rebuilds only match newly seen keys against substitution patterns. */
class NativeSubstitutionIndexTest {

    private static final AEKeyType TYPE = AEKeyType
        .register(ResourceLocation.parse("neoecoae_test:substitution"), null, 8, 1);
    private static final Map<IAEStack<?>, AEKey> NATIVE = new IdentityHashMap<>();
    private final World world = mock(World.class);
    private final AEKey stone = new Key("stone"), cobblestone = new Key("cobblestone"), dirt = new Key("dirt"),
        gravel = new Key("gravel");
    private final GenericStack[] sparse = { new GenericStack(stone, 1), null, new GenericStack(stone, 1) };

    @Test
    void stockChangesOnlyValidateUnseenKeys() {
        var pattern = pattern();
        var index = new NativeSubstitutionIndex(world);

        index.begin(keys(stone, cobblestone, dirt));
        assertEquals(keys(stone, cobblestone), accepted(index, pattern));
        int initial = checks(pattern);

        index.begin(keys(stone, cobblestone, dirt, gravel));
        assertEquals(keys(stone, cobblestone, gravel), accepted(index, pattern));
        assertEquals(initial + 2, checks(pattern), "Only the new key is matched, once per encoded slot");

        index.begin(keys(stone, dirt));
        assertEquals(keys(stone), accepted(index, pattern), "Departed substitutes leave the choices");
        index.begin(keys(stone, cobblestone, dirt, gravel));
        assertEquals(keys(stone, cobblestone, gravel), accepted(index, pattern), "Returning keys reuse the memo");
        assertEquals(initial + 2, checks(pattern));
    }

    @Test
    void retiredPatternsAreValidatedAgainWhenTheyReturn() {
        var pattern = pattern();
        var index = new NativeSubstitutionIndex(world);
        index.begin(keys(stone, cobblestone));
        accepted(index, pattern);
        int initial = checks(pattern);

        index.retain(new HashSet<>());
        index.begin(keys(stone, cobblestone));

        assertEquals(keys(stone, cobblestone), accepted(index, pattern));
        assertEquals(initial * 2, checks(pattern));
    }

    private ICraftingPatternDetails pattern() {
        var pattern = mock(ICraftingPatternDetails.class);
        when(pattern.isValidItemForSlot(anyInt(), any(IAEStack.class), any()))
            .thenAnswer(call -> !dirt.equals(NATIVE.get(call.<IAEStack<?>>getArgument(1))));
        return pattern;
    }

    private static int checks(ICraftingPatternDetails pattern) {
        return (int) mockingDetails(pattern).getInvocations()
            .stream()
            .filter(
                call -> call.getMethod()
                    .getName()
                    .equals("isValidItemForSlot"))
            .count();
    }

    private Set<AEKey> accepted(NativeSubstitutionIndex index, ICraftingPatternDetails pattern) {
        List<AEKey> result = new ArrayList<>();
        index.accepted(pattern, 0, stone, sparse)
            .forEach(result::add);
        return new LinkedHashSet<>(result);
    }

    private static Set<AEKey> keys(AEKey... keys) {
        return new LinkedHashSet<>(Arrays.asList(keys));
    }

    private static final class Key extends AEKey {

        private final String name;

        Key(String name) {
            this.name = name;
        }

        @Override
        public AEKeyType getType() {
            return TYPE;
        }

        @Override
        public Object getPrimaryKey() {
            return name;
        }

        @Override
        public String getDisplayName() {
            return name;
        }

        @Override
        public IAEStack<?> toNative(long amount) {
            IAEStack<?> stack = mock(IAEStack.class);
            NATIVE.put(stack, this);
            return stack;
        }

        @Override
        public CompoundTag toTagGeneric(HolderLookup.Provider registries) {
            throw new UnsupportedOperationException();
        }
    }
}
