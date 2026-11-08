package cn.dancingsnow.neoecoae.crafting.upload;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.world.World;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
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

    @Test
    void customMappingsPersistPerPlayerAndCategoryWithoutDependingOnTheDisplayLanguage() throws Exception {
        NBTTagCompound data = new NBTTagCompound();
        NBTTagCompound persisted = new NBTTagCompound();
        persisted.setString("anotherMod", "keep");
        data.setTag(EntityPlayerMP.PERSISTED_NBT_TAG, persisted);
        EntityPlayerMP player = player(data);
        RouteHint hint = new RouteHint("", null, "压榨机", "nei:press", 3);
        Evaluation target = evaluation(target("a", "Press"), unknown(), SlotState.FREE, 4, 9);
        assertTrue(PatternUploadMappings.put(player, hint, target));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(data, bytes);
        NBTTagCompound restored = CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes.toByteArray()));
        EntityPlayerMP reconnected = player(restored);
        RouteHint translated = new RouteHint("", null, "Press", "nei:press", 9);
        PatternUploadMappings.Mapping mapping = PatternUploadMappings.get(reconnected, translated);
        assertNotNull(mapping);
        assertTrue(mapping.matches(target));
        assertNull(PatternUploadMappings.get(player(new NBTTagCompound()), translated));
        assertNull(PatternUploadMappings.get(reconnected, new RouteHint("", null, "Press", "nei:other", 9)));

        assertTrue(PatternUploadMappings.toggle(reconnected, translated));
        assertNull(
            PatternTargetSelector.choose(
                Collections.singletonList(target),
                PatternUploadMappings.get(reconnected, translated)).selected);
        assertTrue(PatternUploadMappings.toggle(reconnected, translated));
        assertTrue(
            PatternTargetSelector
                .choose(Collections.singletonList(target), PatternUploadMappings.get(reconnected, translated)).mapped);
        assertTrue(PatternUploadMappings.remove(reconnected, translated));
        assertNull(PatternUploadMappings.get(reconnected, translated));
        assertEquals(
            "keep",
            restored.getCompoundTag(EntityPlayerMP.PERSISTED_NBT_TAG)
                .getString("anotherMod"));
    }

    @Test
    void mappedFallbackChoosesTheFreestMemberWithoutPromotingItsMatchToVerified() {
        Evaluation smaller = evaluation(target("a", "Press"), unknown(), SlotState.FREE, 1, 9);
        Evaluation duplicate = evaluation(target("b", "Press"), unknown(), SlotState.DUPLICATE, 8, 9);
        Evaluation larger = evaluation(target("c", "Press"), unknown(), SlotState.FREE, 7, 9);
        Evaluation other = evaluation(target("d", "Other machine"), unknown(), SlotState.FREE, 9, 9);
        PatternUploadMappings.Mapping mapping = mapping(smaller);
        PatternTargetSelector.AutoChoice choice = PatternTargetSelector
            .choose(Arrays.asList(duplicate, smaller, other, larger), mapping);
        assertSame(larger, choice.selected);
        assertTrue(choice.mapped);
        assertEquals(MatchResult.Level.UNVERIFIED, larger.match.level);
        assertFalse(larger.canUploadAutomatically());
        assertNull(PatternTargetSelector.choose(Arrays.asList(smaller, larger)).selected);
        assertNull(PatternTargetSelector.choose(Collections.singletonList(duplicate), mapping).selected);
        assertNull(
            PatternTargetSelector.choose(
                Collections.singletonList(evaluation(target("full", "Press"), unknown(), SlotState.FULL, 0, 9)),
                mapping).selected);
        assertNull(PatternTargetSelector.choose(Collections.singletonList(other), mapping).selected);
    }

    @Test
    void verifiedRoutesKeepPriorityIncludingTheirFullDuplicateAndAmbiguousStates() {
        Evaluation fallback = evaluation(target("a", "Press"), unknown(), SlotState.FREE, 9, 9);
        PatternUploadMappings.Mapping mapping = mapping(fallback);
        Evaluation verified = evaluation(target("b", "Verified"), exact(1), SlotState.FREE, 1, 9);
        PatternTargetSelector.AutoChoice choice = PatternTargetSelector
            .choose(Arrays.asList(fallback, verified), mapping);
        assertSame(verified, choice.selected);
        assertFalse(choice.mapped);
        for (SlotState state : Arrays.asList(SlotState.FULL, SlotState.DUPLICATE)) {
            Evaluation unavailable = evaluation(verified.target, verified.match, state, 0, 9);
            assertNull(PatternTargetSelector.choose(Arrays.asList(fallback, unavailable), mapping).selected);
        }
        Evaluation otherRoute = evaluation(target("c", "Other route"), exact(2), SlotState.FREE, 4, 9);
        assertNull(PatternTargetSelector.choose(Arrays.asList(fallback, verified, otherRoute), mapping).selected);
    }

    @Test
    void mappingsCannotOverrideKnownProblemsOrFollowChangedGroupConfigurations() {
        PatternUploadTarget host = target("a", "Press");
        Evaluation initial = evaluation(host, unknown(), SlotState.FREE, 9, 9);
        PatternUploadMappings.Mapping mapping = mapping(initial);
        for (MatchResult.Level level : Arrays.asList(
            MatchResult.Level.CIRCUIT_CONFLICT,
            MatchResult.Level.RATIO_MISMATCH,
            MatchResult.Level.WRONG_RECIPE,
            MatchResult.Level.INCOMPATIBLE)) {
            Evaluation conflict = evaluation(host, MatchResult.of(level), SlotState.FREE, 9, 9);
            assertTrue(
                mapping.matches(conflict),
                "Changing the verdict must not hide the conflict behind a different key");
            assertNull(PatternTargetSelector.choose(Collections.singletonList(conflict), mapping).selected);
        }
        for (String reason : Arrays.asList("circuit_unknown", "probabilistic")) {
            Evaluation uncertain = evaluation(
                host,
                MatchResult.of(MatchResult.Level.UNVERIFIED, "gui.neoecoae.pattern_upload.reason." + reason),
                SlotState.FREE,
                9,
                9);
            assertNull(PatternTargetSelector.choose(Collections.singletonList(uncertain), mapping).selected);
        }
        Evaluation configured = evaluation(host, exact(1), SlotState.FREE, 9, 9);
        PatternUploadMappings.Mapping configuredMapping = mapping(configured);
        assertFalse(configuredMapping.matches(evaluation(host, exact(2), SlotState.FREE, 9, 9)));
        assertFalse(
            configuredMapping.matches(
                evaluation(
                    host,
                    new MatchResult(
                        MatchResult.Level.UNVERIFIED,
                        null,
                        circuit(1),
                        "other mode",
                        null,
                        "other#1",
                        false),
                    SlotState.FREE,
                    9,
                    9)));
        assertFalse(
            mapping.matches(
                evaluation(
                    target("a", "Press", PatternUploadTarget.Kind.AE2_INTERFACE),
                    unknown(),
                    SlotState.FREE,
                    9,
                    9)));
    }

    @Test
    void mappedSubmitRechecksTheMachineAndUsesTheExistingTransferAndUndo() {
        EntityPlayerMP player = player(new NBTTagCompound());
        Container container = mock(Container.class);
        container.windowId = 3;
        player.openContainer = container;
        player.playerNetServerHandler = mock(NetHandlerPlayServer.class);
        IGrid grid = mock(IGrid.class);
        InventoryBasic from = new InventoryBasic("source", true, 1);
        InventoryBasic to = new InventoryBasic("target", true, 2);
        ItemStack pattern = new ItemStack(new ProcessingPattern());
        from.setInventorySlotContents(0, pattern);
        PatternUploadSource source = mock(PatternUploadSource.class);
        when(source.slots()).thenReturn(PatternSlotAccess.of(from, 1, null));
        when(source.grid()).thenReturn(grid);
        when(source.identity()).thenReturn(from);
        PatternUploadTarget target = target("a", "Press");
        when(target.isPresent(grid)).thenReturn(true);
        when(target.slots()).thenReturn(PatternSlotAccess.of(to, 2, null));
        AtomicReference<MatchResult> route = new AtomicReference<>(unknown());
        when(target.match(any())).thenAnswer(invocation -> route.get());
        RouteHint hint = new RouteHint("", null, "Press", "nei:press", 3);
        hint.bind(pattern);
        PatternUploadSessions.setHint(player, hint);
        List<PatternUploadTarget> discovered = new ArrayList<>(Collections.singletonList(target));
        try {
            PatternUploadSession session = new PatternUploadSession(player, source, container, ignored -> discovered);
            session.refresh();
            int token = session.groupOf(target)
                .token();
            assertFalse(session.saveMapping(token), "Saving requires the terminal's normal edit permission");
            assertNull(PatternUploadMappings.get(player, hint));
            when(source.canEdit(player)).thenReturn(true);
            assertTrue(session.saveMapping(token));
            assertNotNull(from.getStackInSlot(0), "Saving the mapping does not upload a pattern");
            assertNull(to.getStackInSlot(0));
            route.set(MatchResult.of(MatchResult.Level.CIRCUIT_CONFLICT));
            assertFalse(
                session.upload(target, true)
                    .succeeded());
            assertNotNull(from.getStackInSlot(0));
            assertNull(to.getStackInSlot(0));

            route.set(unknown());
            PatternUploadTarget verified = target("b", "Verified route");
            when(verified.isPresent(grid)).thenReturn(true);
            when(verified.slots()).thenReturn(PatternSlotAccess.of(new InventoryBasic("verified", true, 1), 1, null));
            when(verified.match(any())).thenReturn(exact(1));
            discovered.add(verified);
            assertFalse(
                session.upload(target, true)
                    .succeeded(),
                "A newly verified route must supersede the fallback");
            assertNotNull(from.getStackInSlot(0));
            assertNull(to.getStackInSlot(0));
            discovered.remove(verified);
            assertTrue(
                session.upload(target, true)
                    .succeeded());
            assertNull(from.getStackInSlot(0));
            assertTrue(PatternStacks.sameItem(pattern, to.getStackInSlot(0)));
            assertTrue(
                session.undo()
                    .succeeded());
            assertTrue(PatternStacks.sameItem(pattern, from.getStackInSlot(0)));
            assertNull(to.getStackInSlot(0));
        } finally {
            PatternUploadSessions.clear();
        }
    }

    private static EntityPlayerMP player(NBTTagCompound data) {
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        when(player.getEntityData()).thenReturn(data);
        when(player.getUniqueID()).thenReturn(UUID.randomUUID());
        return player;
    }

    private static PatternUploadMappings.Mapping mapping(Evaluation evaluation) {
        EntityPlayerMP player = player(new NBTTagCompound());
        RouteHint hint = new RouteHint("", null, "Press", "nei:press", 3);
        assertTrue(PatternUploadMappings.put(player, hint, evaluation));
        return PatternUploadMappings.get(player, hint);
    }

    private static MatchResult unknown() {
        return MatchResult.of(MatchResult.Level.UNVERIFIED, "gui.neoecoae.pattern_upload.reason.no_route");
    }

    private static final class ProcessingPattern extends Item implements ICraftingPatternItem {

        @Override
        public ICraftingPatternDetails getPatternForItem(ItemStack stack, World world) {
            return mock(ICraftingPatternDetails.class);
        }
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
