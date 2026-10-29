package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

/** Substitutes are looked up like AE2 rv3 does and each candidate is matched against the recipe once. */
class NativeSubstitutionIndexTest {

    private static final int STONE_ORE = 7;
    private final ICraftingPatternDetails pattern = mock(ICraftingPatternDetails.class);
    private final List<AEKey> checked = new ArrayList<>();
    private AEItemKey stone, cobblestone, gravel, dirt, sand;

    @BeforeEach
    void bootstrap() {
        InventoryTestBootstrap.initialize();
        stone = AEItemKey.of(TestItems.STONE);
        cobblestone = AEItemKey.of(TestItems.COBBLESTONE);
        gravel = AEItemKey.of(TestItems.GRAVEL);
        dirt = AEItemKey.of(TestItems.DIRT);
        sand = AEItemKey.of(TestItems.SAND);
    }

    /** Stone, cobblestone and gravel share an ore entry; the recipe rejects gravel. */
    private NativeSubstitutionIndex index() {
        return new NativeSubstitutionIndex(mock(World.class), (pattern, sparse, primary, candidate) -> {
            checked.add(candidate);
            return !candidate.equals(gravel);
        }, stack -> {
            Item item = stack.getItem();
            return item == TestItems.STONE || item == TestItems.COBBLESTONE || item == TestItems.GRAVEL
                ? new int[] { STONE_ORE }
                : new int[0];
        });
    }

    @Test
    void onlyOreEquivalentsAreMatchedAgainstTheRecipe() {
        var index = index();

        index.begin(keys(stone, cobblestone, gravel, dirt, sand));

        assertEquals(keys(cobblestone), accepted(index, stone));
        assertEquals(keys(cobblestone, gravel), new HashSet<>(checked), "Unrelated stock never reaches the recipe");
    }

    @Test
    void stockChangesOnlyValidateNewCandidates() {
        var index = index();
        index.begin(keys(stone, dirt));
        assertEquals(keys(), accepted(index, stone));

        index.begin(keys(stone, dirt, cobblestone, sand));
        assertEquals(keys(cobblestone), accepted(index, stone));
        index.begin(keys(stone, dirt));
        assertEquals(keys(), accepted(index, stone), "Departed substitutes leave the choices");
        index.begin(keys(stone, dirt, cobblestone, gravel));
        assertEquals(keys(cobblestone), accepted(index, stone), "Returning keys reuse the memo");

        assertEquals(Arrays.asList(cobblestone, gravel), checked);
    }

    @Test
    void inputsWithoutOresMatchTheSameItemAndDamage() {
        var index = index();
        var plain = AEItemKey.of(new ItemStack(TestItems.SAND, 1, 0));
        var otherDamage = AEItemKey.of(new ItemStack(TestItems.SAND, 1, 3));
        var pickaxe = AEItemKey.of(new ItemStack(TestItems.IRON_PICKAXE, 1, 0));
        var worn = AEItemKey.of(new ItemStack(TestItems.IRON_PICKAXE, 1, 40));
        index.begin(keys(plain, otherDamage, pickaxe, worn, dirt));

        assertEquals(keys(), accepted(index, plain), "Another damage value of a subtype item is a different item");
        assertEquals(keys(worn), accepted(index, pickaxe), "Damageable items accept any damage");
        assertEquals(Arrays.asList(worn), checked);
    }

    @Test
    void retiredPatternsAreValidatedAgainWhenTheyReturn() {
        var index = index();
        index.begin(keys(stone, cobblestone));
        accepted(index, stone);

        index.retain(new HashSet<>());
        index.begin(keys(stone, cobblestone));

        assertEquals(keys(cobblestone), accepted(index, stone));
        assertEquals(Arrays.asList(cobblestone, cobblestone), checked);
    }

    private Set<AEKey> accepted(NativeSubstitutionIndex index, AEItemKey primary) {
        var sparse = new GenericStack[] { new GenericStack(primary, 1) };
        Set<AEKey> result = new LinkedHashSet<>();
        index.accepted(pattern, 0, primary, sparse)
            .forEach(result::add);
        return result;
    }

    private static Set<AEKey> keys(AEKey... keys) {
        return new LinkedHashSet<>(Arrays.asList(keys));
    }
}
