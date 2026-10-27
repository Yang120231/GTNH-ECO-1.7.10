package cn.dancingsnow.neoecoae.platform.ui.multiblock;

import java.io.IOException;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import cn.dancingsnow.neoecoae.multiblock.placement.RequiredItem;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.ListTag;
import cn.dancingsnow.neoecoae.platform.network.RegistryFriendlyByteBuf;
import cn.dancingsnow.neoecoae.platform.ui.NativeTileSession;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Server preview/material counts and source construction actions from the live physical host. */
public final class NativeBuilderSession extends NativeTileSession {

    private final NativeBuilderConfig config;
    private CompoundTag state = new CompoundTag(), previous;
    private long nextCapture;

    public NativeBuilderSession(NativeBuilderConfig config) {
        super(config.tile());
        this.config = config;
    }

    public int length() {
        return state.getInt("length");
    }

    public boolean mirrored() {
        return state.getBoolean("mirror");
    }

    public String status() {
        return state.getString("status");
    }

    public boolean building() {
        return state.getBoolean("building");
    }

    public int metric(String key) {
        return state.getInt(key);
    }

    public boolean canBuild() {
        return state.getBoolean("canBuild");
    }

    public boolean editable() {
        return config.minLength() < config.maxLength() && state.getBoolean("editable");
    }

    public int minLength() {
        return config.minLength();
    }

    public int maxLength() {
        return config.maxLength();
    }

    public ItemStack material(int index) {
        return ItemStacks.parseOptional(RegistryAccess.EMPTY, materialTag(index).getCompound("stack"));
    }

    public int required(int index) {
        return materialTag(index).getInt("required");
    }

    public int available(int index) {
        return materialTag(index).getInt("available");
    }

    public boolean enough(int index) {
        return materialTag(index).getBoolean("enough");
    }

    public String conflicts() {
        return state.getString("conflictPositions");
    }

    private CompoundTag materialTag(int index) {
        ListTag materials = state.getList("materials", 10);
        return index < 0 || index >= materials.size() ? new CompoundTag() : materials.getCompound(index);
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (!validSession()) return;
        long tick = config.tile()
            .getWorldObj()
            .getTotalWorldTime();
        if (!init && tick < nextCapture) return;
        nextCapture = tick + 5;
        state = capture(config);
        if (init || !state.equals(previous)) {
            previous = state.copy();
            syncToClient(0, output -> new RegistryFriendlyByteBuf(output, RegistryAccess.EMPTY).writeNbt(state));
        }
    }

    static CompoundTag capture(NativeBuilderConfig config) {
        return capture(config, RegistryAccess.EMPTY);
    }

    static CompoundTag capture(NativeBuilderConfig config,
        cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(
            "length",
            config.selectedLength()
                .getAsInt());
        tag.putBoolean(
            "mirror",
            config.mirrored()
                .getAsBoolean());
        var plan = config.previewPlan()
            .get();
        boolean formed = config.formed()
            .getAsBoolean(),
            building = config.buildInProgress()
                .getAsBoolean();
        tag.putBoolean("building", building);
        tag.putBoolean("editable", !formed && !building && plan != null);
        boolean creative = config.player().capabilities.isCreativeMode;
        List<RequiredItem> required = plan == null ? java.util.Collections.emptyList() : plan.getRequiredItems();
        ListTag materials = new ListTag();
        boolean allEnough = true;
        for (RequiredItem item : required) {
            int available = MultiBlockPlacementService.countMatchingItems(config.player(), item.stack());
            boolean enough = creative || available >= item.count();
            allEnough &= enough;
            CompoundTag material = new CompoundTag();
            material.put("stack", ItemStacks.save(registries, item.stack()));
            material.putInt("required", item.count());
            material.putInt("available", available);
            material.putBoolean("enough", enough);
            materials.add(material);
        }
        tag.put("materials", materials);
        tag.putInt(
            "missing",
            plan == null ? 0
                : plan.getMissingBlocks()
                    .size());
        tag.putInt("reused", plan == null ? 0 : plan.getReusedBlockCount());
        tag.putInt(
            "conflicts",
            plan == null ? 0
                : plan.getConflictPositions()
                    .size());
        tag.putInt("required", plan == null ? 0 : plan.getRequiredItemCount());
        StringBuilder conflicts = new StringBuilder();
        if (plan != null) for (int index = 0; index < Math.min(
            8,
            plan.getConflictPositions()
                .size()); index++) {
                    var pos = plan.getConflictPositions()
                        .get(index);
                    if (index > 0) conflicts.append('\n');
                    conflicts.append(pos.getX())
                        .append(", ")
                        .append(pos.getY())
                        .append(", ")
                        .append(pos.getZ());
                }
        tag.putInt(
            "otherConflicts",
            plan == null ? 0
                : Math.max(
                    0,
                    plan.getConflictPositions()
                        .size() - 8));
        tag.putString("conflictPositions", conflicts.toString());
        String status = formed ? "controller_formed"
            : building ? "build_in_progress"
                : plan == null ? "no_definition"
                    : !plan.getConflictPositions()
                        .isEmpty() ? "conflicts_detected"
                            : plan.getMissingBlocks()
                                .isEmpty() ? "structure_ready" : allEnough ? "ready_to_build" : "not_enough_items";
        tag.putString("status", status);
        tag.putBoolean("canBuild", status.equals("ready_to_build") || status.equals("structure_ready"));
        return tag;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void readOnClient(int id, PacketBuffer buffer) throws IOException {
        if (id != 0) throw new IOException("Unknown builder snapshot: " + id);
        state = new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY).readNbt();
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) throws IOException {
        Runnable action;
        if (id == 0) action = () -> config.changeLength()
            .accept(-1);
        else if (id == 1) action = () -> config.changeLength()
            .accept(1);
        else if (id == 2) {
            boolean mirror = buffer.readBoolean();
            action = () -> config.setMirrored()
                .accept(mirror);
        } else if (id == 3) action = config.build();
        else if (id == 4) {
            int length = buffer.readInt();
            action = () -> config.setLength()
                .accept(
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                        .clamp(length, config.minLength(), config.maxLength()));
        } else if (id == 5) {
            int delta = buffer.readInt();
            if (delta != -10 && delta != -1 && delta != 1 && delta != 10)
                throw new IOException("Invalid builder length step: " + delta);
            action = () -> config.changeLength()
                .accept(delta);
        } else throw new IOException("Unknown builder action: " + id);
        if (buffer.isReadable()) throw new IOException("Trailing builder action bytes");
        edit(() -> {
            if (config.formed()
                .getAsBoolean()
                || config.buildInProgress()
                    .getAsBoolean()
                || config.previewPlan()
                    .get() == null)
                return;
            action.run();
            nextCapture = 0;
        });
    }

    @SideOnly(Side.CLIENT)
    public void changeLength(int delta) {
        syncToServer(5, output -> output.writeInt(delta));
    }

    @SideOnly(Side.CLIENT)
    public void setLength(String text) {
        try {
            int length = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                .clamp(Long.parseLong(text), minLength(), maxLength());
            syncToServer(4, output -> output.writeInt(length));
        } catch (NumberFormatException ignored) {
            // Clearing or partially editing the field preserves the selected length.
        }
    }

    @SideOnly(Side.CLIENT)
    public void mirror(boolean mirror) {
        syncToServer(2, output -> output.writeBoolean(mirror));
    }

    @SideOnly(Side.CLIENT)
    public void build() {
        syncToServer(3);
    }
}
