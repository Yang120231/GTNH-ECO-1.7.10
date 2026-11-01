package cn.dancingsnow.neoecoae.crafting.upload;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.upload.PatternTargetSelector.Evaluation;
import cn.dancingsnow.neoecoae.crafting.upload.PatternTargetSelector.SlotState;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;

class PatternTargetGroupsTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void parallelMachinesShareOneFreeRowEvenWhenSomeAlreadyHoldThePattern() {
        Evaluation duplicate = evaluation(target("a", "Macerator"), exact(1), SlotState.DUPLICATE, 3, 9);
        Evaluation small = evaluation(target("b", "Macerator"), exact(1), SlotState.FREE, 2, 9);
        Evaluation large = evaluation(target("c", "Macerator"), exact(1), SlotState.FREE, 7, 9);
        Evaluation full = evaluation(target("d", "Macerator"), exact(1), SlotState.FULL, 0, 9);

        List<PatternTargetGroups.Group> groups = PatternTargetGroups
            .group(Arrays.asList(duplicate, small, large, full));

        assertEquals(1, groups.size());
        PatternTargetGroups.Group group = groups.get(0);
        assertEquals(SlotState.FREE, group.state);
        assertEquals(4, group.members.size());
        assertEquals(2, group.freeMembers);
        assertEquals(1, group.duplicates);
        assertEquals(12, group.emptySlots);
        assertEquals(36, group.totalSlots);
        assertTrue(group.manual);
        assertSame(large, group.position());
        assertEquals(Arrays.asList(large, small), group.uploadOrder());
    }

    @Test
    void groupStateFallsBackFromDuplicateToFullToOffline() {
        assertEquals(
            SlotState.DUPLICATE,
            single(
                evaluation(target("a", "Hatch"), exact(1), SlotState.FULL, 0, 4),
                evaluation(target("b", "Hatch"), exact(1), SlotState.DUPLICATE, 1, 4)).state);
        PatternTargetGroups.Group full = single(
            evaluation(target("a", "Hatch"), exact(1), SlotState.FULL, 0, 4),
            evaluation(target("b", "Hatch"), exact(1), SlotState.OFFLINE, 0, 0));
        assertEquals(SlotState.FULL, full.state);
        assertFalse(full.manual);
        assertTrue(
            full.uploadOrder()
                .isEmpty());
        assertEquals("a", full.position().target.positionKey());
        assertEquals(
            SlotState.OFFLINE,
            single(evaluation(target("a", "Hatch"), exact(1), SlotState.OFFLINE, 0, 0)).state);
    }

    @Test
    void differentNameKindCircuitLevelOrCoverSplitGroups() {
        PatternUploadTarget base = target("a", "Macerator");
        List<Evaluation> evaluations = Arrays.asList(
            evaluation(base, exact(1), SlotState.FREE, 1, 9),
            evaluation(target("b", "Macerator"), exact(2), SlotState.FREE, 1, 9),
            evaluation(target("c", "Compressor"), exact(1), SlotState.FREE, 1, 9),
            evaluation(
                target("d", "Macerator", PatternUploadTarget.Kind.AE2_INTERFACE),
                exact(1),
                SlotState.FREE,
                1,
                9),
            evaluation(target("e", "Macerator"), MatchResult.of(MatchResult.Level.UNVERIFIED), SlotState.FREE, 1, 9),
            evaluation(
                target("f", "Macerator"),
                new MatchResult(MatchResult.Level.EXACT, null, circuit(1), "route", null, "map#1", true),
                SlotState.FREE,
                1,
                9));
        assertEquals(
            6,
            PatternTargetGroups.group(evaluations)
                .size());
    }

    @Test
    void groupsAreOrderedByTheirBestMemberWithOfflineLast() {
        Evaluation offline = evaluation(target("a", "Offline"), exact(1), SlotState.OFFLINE, 0, 0);
        Evaluation unverified = evaluation(
            target("b", "Unverified"),
            MatchResult.of(MatchResult.Level.UNVERIFIED),
            SlotState.FREE,
            5,
            9);
        Evaluation exactFull = evaluation(target("c", "Exact"), exact(1), SlotState.FULL, 0, 9);
        Evaluation exactFree = evaluation(target("d", "Exact"), exact(1), SlotState.FREE, 1, 9);

        List<PatternTargetGroups.Group> groups = PatternTargetGroups
            .group(Arrays.asList(offline, unverified, exactFull, exactFree));

        assertEquals(3, groups.size());
        assertSame(
            exactFree,
            groups.get(0)
                .best());
        assertSame(
            unverified,
            groups.get(1)
                .best());
        assertSame(
            offline,
            groups.get(2)
                .best());
    }

    @Test
    void tokensStayStableForAnUnchangedGroupKey() {
        PatternTargetGroups groups = new PatternTargetGroups();
        PatternUploadTarget a = target("a", "Macerator");
        PatternUploadTarget b = target("b", "Compressor");
        groups.update(
            Arrays
                .asList(evaluation(a, exact(1), SlotState.FREE, 2, 9), evaluation(b, exact(1), SlotState.FREE, 2, 9)));
        int tokenA = groups.groupOf(a)
            .token();
        int tokenB = groups.groupOf(b)
            .token();
        assertNotEquals(tokenA, tokenB);

        // After an upload the machine holds the pattern: state changes, the group key does not.
        PatternUploadTarget a2 = target("a2", "Macerator");
        groups.update(
            Arrays.asList(
                evaluation(a, exact(1), SlotState.DUPLICATE, 1, 9),
                evaluation(a2, exact(1), SlotState.FREE, 9, 9)));
        assertEquals(
            tokenA,
            groups.groupOf(a2)
                .token());
        assertSame(groups.group(tokenA), groups.groupOf(a));
        assertNull(groups.group(tokenB));

        // A changed circuit is a different route and must never inherit the old token.
        groups.update(Collections.singletonList(evaluation(a, exact(2), SlotState.FREE, 1, 9)));
        assertNotEquals(
            tokenA,
            groups.groupOf(a)
                .token());
        assertNull(groups.group(tokenA));

        groups.clear();
        assertTrue(
            groups.groups()
                .isEmpty());
    }

    @Test
    void tokensOfSeparateSessionsNeverCollide() {
        PatternUploadTarget a = target("a", "Macerator");
        PatternTargetGroups first = new PatternTargetGroups();
        PatternTargetGroups second = new PatternTargetGroups();
        first.update(Collections.singletonList(evaluation(a, exact(1), SlotState.FREE, 1, 9)));
        second.update(Collections.singletonList(evaluation(a, exact(1), SlotState.FREE, 1, 9)));
        assertNull(
            second.group(
                first.groupOf(a)
                    .token()));
    }

    @Test
    void onlyTargetSpecificRejectionsFailOverToTheNextMember() {
        assertTrue(PatternUploadSession.targetSpecific(PatternTransfer.reject(PatternTransfer.REASON_TARGET_OCCUPIED)));
        assertTrue(PatternUploadSession.targetSpecific(PatternTransfer.reject(PatternTransfer.REASON_OFFLINE)));
        assertTrue(PatternUploadSession.targetSpecific(PatternTransfer.reject(SlotState.FULL.translationKey())));
        assertFalse(PatternUploadSession.targetSpecific(PatternTransfer.reject(PatternTransfer.REASON_SOURCE_CHANGED)));
        assertFalse(
            PatternUploadSession
                .targetSpecific(PatternTransfer.reject("gui.neoecoae.pattern_upload.reason.no_permission")));
        assertFalse(
            PatternUploadSession.targetSpecific(PatternTransfer.reject("gui.neoecoae.pattern_upload.no_pattern")));
    }

    @Test
    void categoryOnlyHintsAreKeptButNeverRoute() {
        RouteHint categoryOnly = new RouteHint("", null, "Crafting Table", 3);
        assertFalse(categoryOnly.isEmpty());
        assertFalse(categoryOnly.hasRecipeMap());
        ItemStack pattern = new ItemStack(TestItems.DIAMOND);
        categoryOnly.bind(pattern);
        assertTrue(categoryOnly.appliesTo(3, pattern));
        assertFalse(categoryOnly.appliesTo(4, pattern));
        assertTrue(new RouteHint("  ", null, " ", 3).isEmpty());
        RouteHint mapped = new RouteHint("gt.recipe.macerator", null, "Macerator", 3);
        assertTrue(mapped.hasRecipeMap());
        assertEquals("Macerator", mapped.getCategory());
        StringBuilder longName = new StringBuilder();
        for (int index = 0; index < 200; index++) longName.append('x');
        assertEquals(
            128,
            new RouteHint("", null, longName.toString(), 3).getCategory()
                .length());
    }

    private static PatternTargetGroups.Group single(Evaluation... evaluations) {
        List<PatternTargetGroups.Group> groups = PatternTargetGroups.group(Arrays.asList(evaluations));
        assertEquals(1, groups.size());
        return groups.get(0);
    }

    private static MatchResult exact(int circuit) {
        return new MatchResult(MatchResult.Level.EXACT, null, circuit(circuit), "route", null, "map#" + circuit, false);
    }

    private static ItemStack circuit(int number) {
        return new ItemStack(TestItems.STONE, 1, number);
    }

    private static PatternUploadTarget target(String position, String name) {
        return target(position, name, PatternUploadTarget.Kind.GT_CRAFTING_INPUT);
    }

    private static PatternUploadTarget target(String position, String name, PatternUploadTarget.Kind kind) {
        PatternUploadTarget target = mock(PatternUploadTarget.class);
        when(target.positionKey()).thenReturn(position);
        when(target.name()).thenReturn(name);
        when(target.kind()).thenReturn(kind);
        return target;
    }

    private static Evaluation evaluation(PatternUploadTarget target, MatchResult match, SlotState state, int empty,
        int total) {
        return new Evaluation(target, match, state, empty, total, state == SlotState.FREE ? 0 : -1);
    }
}
