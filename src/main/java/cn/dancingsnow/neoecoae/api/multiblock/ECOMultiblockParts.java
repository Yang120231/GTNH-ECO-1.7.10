package cn.dancingsnow.neoecoae.api.multiblock;

import java.util.Set;

import net.minecraft.tileentity.TileEntity;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.api.extension.ECOExtensionRegistry;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;

/** Semantic part classification. Runtime cores/workers must still provide their corresponding ECO block entity. */
public final class ECOMultiblockParts {

    private static final ECOExtensionRegistry<Provider> REGISTRY = new ECOExtensionRegistry<>();

    private ECOMultiblockParts() {}

    public enum Role {
        CASING,
        INTERFACE,
        TRANSMITTER,
        COMPUTATION_DRIVE,
        COMPUTATION_PARALLEL_CORE,
        COMPUTATION_THREADING_CORE,
        COOLING_CONTROLLER,
        STORAGE_DRIVE,
        ENERGY_CELL,
        CRAFTING_WORKER,
        CRAFTING_PARALLEL_CORE,
        PATTERN_STORAGE,
        VENT
    }

    public enum DriveRow {
        UPPER,
        LOWER
    }

    @com.github.bsideup.jabel.Desugar
    public record Context(@Nullable WorldView level, BlockPos position, BlockState state,
        @Nullable TileEntity blockEntity) {}

    @com.github.bsideup.jabel.Desugar
    public record Part(Set<Role> roles, @Nullable StorageTierDescriptor tier, @Nullable DriveRow driveRow, int order) {

        public Part {
            roles = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setCopyOf(roles);
        }

        public static Part of(Role role, @Nullable StorageTierDescriptor tier) {
            return new Part(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(role), tier, null, 0);
        }
    }

    @FunctionalInterface
    public interface Provider {

        @Nullable
        Part describe(Context context);
    }

    public static void register(ResourceLocation id, int priority, Provider provider) {
        REGISTRY.register(id, priority, provider);
    }

    public static boolean unregister(ResourceLocation id) {
        return REGISTRY.unregister(id);
    }

    public static long revision() {
        return REGISTRY.revision();
    }

    @Nullable
    public static Part describe(Context context) {
        for (var entry : REGISTRY.entries()) {
            Part part = entry.extension()
                .describe(context);
            if (part != null) return part;
        }
        return context.state()
            .getBlock() instanceof Provider provider ? provider.describe(context) : null;
    }

    public static Context context(WorldView level, BlockPos pos) {
        return new Context(level, pos.immutable(), level.getBlockState(pos), level.getBlockEntity(pos));
    }

    public static boolean matches(Context context, Role role, boolean fallback) {
        Part part = describe(context);
        return part == null ? fallback
            : part.roles()
                .contains(role);
    }

    @Nullable
    public static StorageTierDescriptor tier(Context context) {
        Part part = describe(context);
        if (part != null && part.tier() != null) return part.tier();
        return context.blockEntity() instanceof cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<?, ?>member
            ? member.getTier()
            : null;
    }

    public static DriveRow driveRow(Context context, DriveRow fallback) {
        Part part = describe(context);
        return part == null || part.driveRow() == null ? fallback : part.driveRow();
    }

    public static int order(Context context) {
        Part part = describe(context);
        return part == null ? 0 : part.order();
    }

    public static int order(TileEntity member) {
        BlockState state = cn.dancingsnow.neoecoae.platform.minecraft.TileViews.state(member);
        return state == null ? 0
            : order(
                new Context(
                    cn.dancingsnow.neoecoae.platform.minecraft.TileViews.level(member),
                    cn.dancingsnow.neoecoae.platform.minecraft.TileViews.position(member),
                    state,
                    member));
    }

    public static boolean matchesTiered(Context context, Role role, StorageTierDescriptor hostTier, boolean fallback,
        cn.dancingsnow.neoecoae.platform.minecraft.Direction facing, Class<? extends TileEntity> runtimeType) {
        StorageTierDescriptor memberTier = tier(context);
        return runtimeType.isInstance(context.blockEntity()) && matches(context, role, fallback)
            && memberTier != null
            && hostTier.supportsComponentTier(memberTier)
            && context.state()
                .getOptionalValue(cn.dancingsnow.neoecoae.platform.minecraft.BlockStateProperties.HORIZONTAL_FACING)
                .orElse(null) == facing;
    }

    /** Role-based alternatives to the built-in block classes, shared by formation predicates. */
    @Nullable
    public static Boolean acceptsExpected(Context context, BlockState expected) {
        Part part = describe(context);
        if (part == null) return null;
        Role role = builtinRole(expected);
        return role == null ? null
            : part.roles()
                .contains(role) && ECOMultiblockRequirements.matchingProperties(expected, context.state());
    }

    @Nullable
    private static Role builtinRole(BlockState expected) {
        return expected.getBlock() instanceof cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock block
            ? block.partRole()
            : null;
    }
}
