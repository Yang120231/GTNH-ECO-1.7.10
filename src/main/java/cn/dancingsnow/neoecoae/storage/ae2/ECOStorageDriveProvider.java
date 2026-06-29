package cn.dancingsnow.neoecoae.storage.ae2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationBlockPos;
import cn.dancingsnow.neoecoae.storage.domain.ECOHostDomainInventoryHandler;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cn.dancingsnow.neoecoae.tile.TileECODrive;

public class ECOStorageDriveProvider implements ICellProvider {

    private final World world;
    private final List<ECOFormationBlockPos> drivePositions;
    private final TileECOController controller;

    public ECOStorageDriveProvider(World world, List<ECOFormationBlockPos> drivePositions,
        TileECOController controller) {
        this.world = world;
        this.drivePositions = drivePositions == null ? Collections.<ECOFormationBlockPos>emptyList()
            : new ArrayList<ECOFormationBlockPos>(drivePositions);
        this.controller = controller;
    }

    @Override
    public List<IMEInventoryHandler> getCellArray(StorageChannel channel) {
        if (this.world == null) {
            return Collections.emptyList();
        }
        if (this.controller != null && this.controller.canUseHostDomainStorage()) {
            List<IMEInventoryHandler> domainHandlers = new ArrayList<IMEInventoryHandler>();
            domainHandlers.add(new ECOHostDomainInventoryHandler(this.controller, channel));
            return domainHandlers;
        }
        List<IMEInventoryHandler> handlers = new ArrayList<IMEInventoryHandler>();
        for (ECOFormationBlockPos pos : this.drivePositions) {
            net.minecraft.tileentity.TileEntity tile = this.world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            if (!(tile instanceof TileECODrive)) {
                continue;
            }
            TileECODrive drive = (TileECODrive) tile;
            ItemStack stack = drive.getCellStack();
            IMEInventoryHandler handler = ECOCellHandler.INSTANCE
                .getCellInventory(stack, null, channel, this.getPriority());
            if (handler != null) {
                handlers.add(handler);
            }
        }
        return handlers;
    }

    @Override
    public int getPriority() {
        return this.controller == null ? 0 : this.controller.getPriority();
    }

    public boolean containsDrive(int x, int y, int z) {
        for (ECOFormationBlockPos pos : this.drivePositions) {
            if (pos.getX() == x && pos.getY() == y && pos.getZ() == z) {
                return true;
            }
        }
        return false;
    }
}
