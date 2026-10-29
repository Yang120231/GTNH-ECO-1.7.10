package cn.dancingsnow.neoecoae.platform.registry;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.impl.storage.legacy.BetaStorageReader;
import cn.dancingsnow.neoecoae.platform.ae2.grid.NativeNetworkBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

/**
 * Beta used one controller/interface tile ID for several block types. Wait for the owning
 * block to be available, then use its native factory. Until then, retain the entire old NBT.
 */
public final class BetaStorageTile extends TileEntity {

    private NBTTagCompound original = new NBTTagCompound();
    private boolean failed;
    private boolean reportedUnsupported;

    /**
     * Only storage snapshots wait here for a conversion retry. Other beta subsystems are never converted,
     * so their placeholders stay removable instead of turning into permanent, unbreakable blocks.
     */
    public static boolean protects(net.minecraft.world.IBlockAccess world, int x, int y, int z) {
        return world.getTileEntity(x, y, z) instanceof BetaStorageTile && StorageMachines.blocks()
            .containsValue(world.getBlock(x, y, z));
    }

    public static void register() {
        cpw.mods.fml.common.registry.GameRegistry.registerTileEntityWithAlternatives(
            BetaStorageTile.class,
            "neoecoae.beta_storage_tile",
            "neoecoae:eco_controller",
            "neoecoae:eco_drive",
            "neoecoae:eco_interface");
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        original = (NBTTagCompound) tag.copy();
        failed = false;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        for (Object key : original.func_150296_c())
            if (!"id".equals(key) && !"x".equals(key) && !"y".equals(key) && !"z".equals(key)) tag.setTag(
                (String) key,
                original.getTag((String) key)
                    .copy());
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote || failed) return;
        Block block = worldObj.getBlock(xCoord, yCoord, zCoord);
        // Only migrate storage here; other beta subsystem snapshots remain intact for recovery.
        if (!(block instanceof NativeMachineBlock)) return;
        if (!StorageMachines.blocks()
            .containsValue(block)) {
            if (!reportedUnsupported) {
                reportedUnsupported = true;
                NeoECOAE.LOG.warn(
                    "Beta {} tile at {},{},{} is not migrated and stays inactive with its original NBT; "
                        + "break and replace the block to reset it",
                    original.getString("Subsystem"),
                    xCoord,
                    yCoord,
                    zCoord);
            }
            return;
        }
        try {
            TileEntity replacement = block
                .createTileEntity(worldObj, worldObj.getBlockMetadata(xCoord, yCoord, zCoord));
            if (!(replacement instanceof NativeNetworkBlockEntity)) return;
            NBTTagCompound input = (NBTTagCompound) original.copy();
            resumeControllerRestore(input);
            recoverCompletedRestore(input);
            replacement.readFromNBT(input);
            // AE2's orientation setter notifies neighbours and requires an attached world.
            replacement.setWorldObj(worldObj);
            NativeNetworkBlockEntity nativeTile = (NativeNetworkBlockEntity) replacement;
            int meta = input.hasKey("FacingMeta") ? input.getInteger("FacingMeta")
                : worldObj.getBlockMetadata(xCoord, yCoord, zCoord);
            Direction facing = new Direction[] { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST }[meta
                & 3];
            nativeTile.nativeBlockState(
                ((NativeMachineBlock) block).defaultBlockState()
                    .setValue(NativeMachineBlock.FACING, facing)
                    .setValue(NativeMachineBlock.MIRRORED, input.getBoolean("Mirrored")));
            nativeTile.setOrientation(facing.nativeSide(), net.minecraftforge.common.util.ForgeDirection.UP);
            worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, facing.ordinal(), 2);
            worldObj.setTileEntity(xCoord, yCoord, zCoord, replacement);
            replacement.markDirty();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        } catch (java.io.IOException | RuntimeException failure) {
            failed = true;
            NeoECOAE.LOG.error(
                "Cannot migrate beta storage tile at {},{},{}; original NBT retained",
                xCoord,
                yCoord,
                zCoord,
                failure);
        }
    }

    private void resumeControllerRestore(NBTTagCompound input) throws java.io.IOException {
        if (!input.hasKey("HostDomainId") || !"storage".equals(input.getString("Subsystem"))) return;
        var domain = BetaStorageReader.domain(
            cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage
                .root(net.minecraft.server.MinecraftServer.getServer())
                .resolve("data")
                .resolve(BetaStorageReader.DOMAIN_FILE),
            java.util.UUID.fromString(input.getString("HostDomainId")));
        if (domain == null) {
            if ("formed_infinite".equals(input.getString("HostMode")))
                throw new IllegalStateException("Missing beta controller domain");
            return; // A migration may have been sealed before its first domain commit.
        }
        if (domain.contains("restorePlan")) {
            if (domain.getCompound("restorePlan")
                .getBoolean("Completed")) {
                // The domain released ownership before this stale controller chunk was saved.
                input.setString("HostMode", input.getBoolean("Formed") ? "formed_normal" : "unformed");
                input.removeTag("HostDomainId");
                input.setTag("MemberDiskIds", new net.minecraft.nbt.NBTTagList());
                input.removeTag("MigrationSteps");
            } else input.setBoolean("neoecoae_beta_restore_requested", true);
        }
    }

    private void recoverCompletedRestore(NBTTagCompound input) throws java.io.IOException {
        if (!input.hasKey("Cell", 10)) return;
        ItemStack stack = ItemStack.loadItemStackFromNBT(input.getCompoundTag("Cell"));
        if (stack == null) throw new IllegalStateException("Unresolved beta drive item");
        if (!stack.hasTagCompound()) return;
        NBTTagCompound tag = stack.getTagCompound();
        String mode = tag.getString("ECOStorageMode");
        if (!"domain_member".equals(mode) && !"migrating".equals(mode)) return;
        var domain = BetaStorageReader.domain(
            cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage
                .root(net.minecraft.server.MinecraftServer.getServer())
                .resolve("data")
                .resolve(BetaStorageReader.DOMAIN_FILE),
            java.util.UUID.fromString(tag.getString("ECOHostDomainId")));
        if (domain == null) {
            if ("domain_member".equals(mode)) throw new IllegalStateException("Missing beta member domain");
            return;
        }
        CompoundTag restore = domain.getCompound("restorePlan");
        String diskId = tag.getString("ECODiskId");
        if (restore.getBoolean("Completed")) {
            if (!restore.contains(diskId, 10)) throw new IllegalStateException("Missing beta restored disk snapshot");
            tag.setTag(
                "ECOStorage",
                restore.getCompound(diskId)
                    .copy()
                    .nativeTag());
            tag.setString("ECOStorageMode", "portable");
            tag.removeTag("ECOHostDomainId");
            tag.removeTag("ECOMemberIndex");
            input.setTag("Cell", stack.writeToNBT(new NBTTagCompound()));
        }
    }
}
