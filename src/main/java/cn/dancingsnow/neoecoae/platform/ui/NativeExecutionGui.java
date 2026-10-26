package cn.dancingsnow.neoecoae.platform.ui;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.platform.ui.computation.NativeComputationPages;
import cn.dancingsnow.neoecoae.platform.ui.crafting.NativeCraftingPages;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Explicit native menu bridge used only by actual supported execution machine identities. */
public interface NativeExecutionGui extends IGuiHolder<PosGuiData> {

    @Override
    default ModularPanel buildUI(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        if (this instanceof ECOCraftingSystemBlockEntity)
            return NativeCraftingPages.create((ECOCraftingSystemBlockEntity) this, data.getPlayer(), sync);
        if (this instanceof ECOComputationSystemBlockEntity)
            return NativeComputationPages.create((ECOComputationSystemBlockEntity) this, data.getPlayer(), sync);
        if (this instanceof ECOCraftingPatternBusBlockEntity)
            return NativeCraftingPages.patternBus((ECOCraftingPatternBusBlockEntity) this, data.getPlayer(), sync);
        if (this instanceof ECOComputationDriveBlockEntity)
            return NativeComputationPages.drive((ECOComputationDriveBlockEntity) this, data.getPlayer(), sync);
        throw new IllegalStateException("No native execution menu for " + getClass().getName());
    }

    @Override
    @SideOnly(Side.CLIENT)
    default ModularScreen createScreen(PosGuiData data, ModularPanel panel) {
        return new ModularScreen("neoecoae", panel);
    }
}
